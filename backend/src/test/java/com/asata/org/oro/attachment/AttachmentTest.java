package com.asata.org.oro.attachment;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.attachment.entity.Attachment;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.MessageType;
import com.asata.org.oro.enums.ScanStatus;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.message.dto.SendMessage;
import com.asata.org.oro.message.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** the metadata of a file is seen by who can read its message only; a file is downloaded uploaded and clean only */
class AttachmentTest extends PostgresIntegrationTest {

    private static final String CHECKSUM = "a".repeat(64);

    private UUID amani;
    private UUID baraka;
    private Actor amaniActor;
    private Actor barakaActor;
    private Actor chausikuActor;
    private UUID direct;

    @BeforeEach
    void directConversation() {
        amani = oroUser("amani");
        baraka = oroUser("baraka");
        amaniActor = Actor.oro(amani);
        barakaActor = Actor.oro(baraka);
        chausikuActor = Actor.oro(oroUser("chausiku"));
        direct = conversations.openDirect(amaniActor, baraka).getId();
    }

    private Attachment cleanUpload(Actor actor) {
        Attachment upload = attachments.register(actor, direct, "photo.jpg", "image/jpeg", 2048);
        attachments.markUploaded(upload.getId(), 2048, CHECKSUM);
        attachments.recordScan(upload.getId(), ScanStatus.CLEAN);
        return upload;
    }

    private Message sendWith(Actor actor, UUID attachmentId) {
        return messages.send(actor, direct, new SendMessage("key-" + UUID.randomUUID(), MessageType.IMAGE, null, null,
                List.of(attachmentId))).message();
    }

    @Test
    void onlyTheMembersWhoCanReadTheMessageSeeItsAttachment() {
        Attachment upload = cleanUpload(amaniActor);
        // not attached yet: its uploader only
        assertThat(rawCount(barakaActor, "SELECT count(*) FROM oro_attachment")).isZero();

        Message message = sendWith(amaniActor, upload.getId());

        assertThat(attachments.forMessage(barakaActor, message.getId())).extracting(Attachment::getId).containsExactly(upload.getId());
        assertThat(attachments.requireDownloadable(barakaActor, upload.getId()).getStorageKey()).isEqualTo(upload.getStorageKey());
        // somebody else: nothing, even knowing the storage key
        assertThatThrownBy(() -> attachments.forMessage(chausikuActor, message.getId())).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> attachments.requireDownloadable(chausikuActor, upload.getId())).isInstanceOf(NotFound.class);
        assertThat(rawCount(chausikuActor, "SELECT count(*) FROM oro_attachment WHERE storage_key = ?", upload.getStorageKey())).isZero();
        // the storage key is random, not the file name
        assertThat(upload.getStorageKey()).doesNotContain("photo").startsWith("oro/" + direct + "/");
    }

    @Test
    void aFileIsDownloadedOnlyOnceUploadedAndClean() {
        Attachment pending = attachments.register(amaniActor, direct, "report.pdf", "application/pdf", 4096);
        assertThatThrownBy(() -> attachments.requireDownloadable(amaniActor, pending.getId())).isInstanceOf(Conflict.class);

        attachments.markUploaded(pending.getId(), 4096, CHECKSUM);
        assertThatThrownBy(() -> attachments.requireDownloadable(amaniActor, pending.getId())).isInstanceOf(Conflict.class);

        attachments.recordScan(pending.getId(), ScanStatus.INFECTED);
        assertThatThrownBy(() -> attachments.requireDownloadable(amaniActor, pending.getId())).isInstanceOf(Conflict.class);
        assertRefused(() -> sendWith(amaniActor, pending.getId()), "infected");
        // the API cannot mark a file clean itself
        assertRefused(() -> asUser(amaniActor, jdbc -> jdbc.update("UPDATE oro_attachment SET scan_status = 'clean' WHERE id = ?",
                pending.getId())), "permission denied");
    }

    @Test
    void aSenderAttachesItsOwnUploadsOnly() {
        Attachment amanis = cleanUpload(amaniActor);

        // invisible to Baraka
        assertThatThrownBy(() -> sendWith(barakaActor, amanis.getId())).isInstanceOf(NotFound.class);
        // raw: the composite foreign key ties an attachment to a message of its uploader
        Message barakas = say(barakaActor, direct, "Mine").message();
        assertRefused(() -> admin.update("UPDATE oro_attachment SET message_id = ? WHERE id = ?", barakas.getId(), amanis.getId()),
                "fk_oro_attachment_message");
        assertThat(count("SELECT count(*) FROM oro_message")).isEqualTo(1);
    }

    @Test
    void theAttachmentsOfADeletedMessageAreHiddenAndMarkedToPurge() {
        Attachment upload = cleanUpload(amaniActor);
        Message message = sendWith(amaniActor, upload.getId());

        messages.delete(amaniActor, message.getId());

        assertThat(count("SELECT count(*) FROM oro_attachment WHERE deleted_at IS NOT NULL")).isEqualTo(1);
        assertThat(attachments.forMessage(barakaActor, message.getId())).isEmpty();
        assertThatThrownBy(() -> attachments.requireDownloadable(barakaActor, upload.getId())).isInstanceOf(NotFound.class);
    }

    @Test
    void aNonMemberCannotUploadIntoAConversation() {
        assertThatThrownBy(() -> attachments.register(chausikuActor, direct, "x.txt", "text/plain", 10)).isInstanceOf(NotFound.class);
        assertRefused(() -> attachments.register(amaniActor, direct, "../etc/passwd", "text/plain", 10), "ck_oro_attachment_file_name");
    }
}
