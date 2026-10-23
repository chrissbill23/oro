package com.asata.org.oro.attachment;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.attachment.entity.Attachment;
import com.asata.org.oro.attachment.service.AttachmentPurge;
import com.asata.org.oro.attachment.storage.FileStorage;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.ScanStatus;
import com.asata.org.oro.message.dto.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** the files Oro no longer keeps are removed, file then row; the others stay, file and row */
class AttachmentPurgeTest extends PostgresIntegrationTest {

    @Autowired
    private AttachmentPurge purge;
    @Autowired
    private FileStorage storage;
    @Value("${oro.attachments.local-dir}")
    private String localDir;

    private Actor amaniActor;
    private UUID group;

    @BeforeEach
    void group() {
        UUID amani = oroUser("amani");
        amaniActor = Actor.oro(amani);
        Conversation conversation = conversations.createGroup(amaniActor, "Files", null, List.of(oroUser("baraka")));
        group = conversation.getId();
    }

    /** an upload received in full and scanned clean; received false: registered only (the upload stopped) */
    private Attachment upload(String name, boolean received) throws Exception {
        Attachment attachment = attachments.register(amaniActor, group, name, "text/plain", 5);
        if (received) {
            FileStorage.Stored stored = storage.put(attachment.getStorageKey(), new ByteArrayInputStream("hello".getBytes()), 100);
            attachments.markUploaded(attachment.getId(), stored.sizeBytes(), stored.sha256());
            attachments.recordScan(attachment.getId(), ScanStatus.CLEAN);
        }
        return attachment;
    }

    private UUID send(Attachment attachment) {
        return messages.send(amaniActor, group, new SendMessage("key-" + UUID.randomUUID(), MessageType.FILE, null, null,
                List.of(attachment.getId()))).message().getId();
    }

    private boolean stored(Attachment attachment) {
        return Files.exists(Path.of(localDir).resolve(attachment.getStorageKey()));
    }

    private boolean known(Attachment attachment) {
        return count("SELECT count(*) FROM oro_attachment WHERE id = ?", attachment.getId()) == 1;
    }

    /** older than it is (the trigger keeps the time of an upload: off for this statement of the test) */
    private void age(Attachment attachment, long hours) {
        admin.execute("SET session_replication_role = replica; UPDATE oro_attachment SET created_at = created_at - interval '"
                + hours + " hours' WHERE id = '" + attachment.getId() + "'; SET session_replication_role = origin");
    }

    @Test
    void whatIsNoLongerKeptGoesTheRestStays() throws Exception {
        Attachment ofADeletedMessage = upload("deleted.txt", true);
        messages.delete(amaniActor, send(ofADeletedMessage));
        Attachment sent = upload("sent.txt", true);
        send(sent);
        Attachment abandoned = upload("abandoned.txt", true);
        age(abandoned, 25);
        Attachment recent = upload("recent.txt", true);
        Attachment neverReceived = upload("never-received.txt", false);
        age(neverReceived, 2);
        Attachment stillArriving = upload("arriving.txt", false);

        assertThat(purge.purgeAttachments()).isEqualTo(3);

        for (Attachment gone : List.of(ofADeletedMessage, abandoned, neverReceived)) {
            assertThat(known(gone)).as(gone.getFileName()).isFalse();
            assertThat(stored(gone)).as(gone.getFileName()).isFalse();
        }
        for (Attachment kept : List.of(sent, recent)) {
            assertThat(known(kept)).as(kept.getFileName()).isTrue();
            assertThat(stored(kept)).as(kept.getFileName()).isTrue();
        }
        assertThat(known(stillArriving)).isTrue();
        // nothing more: the purge can run again
        assertThat(purge.purgeAttachments()).isZero();
    }

    @Test
    void aFileWithoutAnAttachmentGoesAfterADay() throws Exception {
        Attachment sent = upload("sent.txt", true);
        send(sent);
        Path root = Path.of(localDir);
        Path oldOrphan = Files.writeString(Files.createDirectories(root.resolve("oro/orphans")).resolve(UUID.randomUUID().toString()), "x");
        Path newOrphan = Files.writeString(root.resolve("oro/orphans").resolve(UUID.randomUUID().toString()), "x");
        Path receiving = Files.writeString(Files.createDirectories(root.resolve(".incoming")).resolve("upload.tmp"), "x");
        FileTime twoDaysAgo = FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS));
        for (Path file : List.of(oldOrphan, receiving, root.resolve(sent.getStorageKey()))) {
            Files.setLastModifiedTime(file, twoDaysAgo);
        }

        // at least this one (files left by earlier runs of the tests may go too)
        assertThat(purge.purgeOrphans()).isPositive();
        assertThat(oldOrphan).doesNotExist();
        // a recent one may be an upload between its file and its row; a dot directory is the server's
        assertThat(newOrphan).exists();
        assertThat(receiving).exists();
        assertThat(stored(sent)).isTrue();
        Files.delete(newOrphan);
        Files.delete(receiving);
    }
}
