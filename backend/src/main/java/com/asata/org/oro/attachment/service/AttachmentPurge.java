package com.asata.org.oro.attachment.service;

import com.asata.org.oro.attachment.storage.FileStorage;
import com.asata.org.oro.context.DbSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Removes the files Oro no longer keeps, as oro_system, file first then row (an interruption leaves a row to retry,
 * never a file without a row):
 * <ul>
 * <li>the attachments of deleted messages (deleted_at), at once;</li>
 * <li>the uploads never attached to a message, after abandoned-after (24 h);</li>
 * <li>the uploads still pending (received in part, or never), after pending-after (1 h).</li>
 * </ul>
 * And the files of the storage that have no attachment (an upload stopped between the file and its row), after a day.
 * By batches, FOR UPDATE SKIP LOCKED: two instances never take the same rows.
 */
@Slf4j
@Service
public class AttachmentPurge {

    private static final int BATCH = 200;

    private final DbSession session;
    private final ObjectProvider<FileStorage> storage;
    private final Duration abandonedAfter;
    private final Duration pendingAfter;

    public AttachmentPurge(DbSession session, ObjectProvider<FileStorage> storage,
                           @Value("${oro.attachments.purge.abandoned-after:PT24H}") Duration abandonedAfter,
                           @Value("${oro.attachments.purge.pending-after:PT1H}") Duration pendingAfter) {
        this.session = session;
        this.storage = storage;
        this.abandonedAfter = abandonedAfter;
        this.pendingAfter = pendingAfter;
    }

    /** what a purge removed: rows (and their files), and files that had a row no longer */
    public record Result(int attachments, int orphans) {
    }

    /** the attachments to remove, batch after batch; the number removed */
    public int purgeAttachments() {
        FileStorage files = storage.getIfAvailable();
        if (files == null) {
            return 0;
        }
        int removed = 0;
        for (int batch; (batch = purgeBatch(files)) > 0; ) {
            removed += batch;
            if (batch < BATCH) {
                break;
            }
        }
        if (removed > 0) {
            log.info("{} attachment(s) purged (deleted messages, uploads never attached or never received)", removed);
        }
        return removed;
    }

    private int purgeBatch(FileStorage files) {
        Integer purged = session.systemTransaction().execute(status -> {
            session.enterSystem();
            List<Map<String, Object>> rows = session.jdbc().queryForList("""
                    SELECT id, storage_key FROM oro_attachment
                    WHERE deleted_at IS NOT NULL
                       OR (message_id IS NULL AND created_at < now() - make_interval(secs => ?))
                       OR (upload_status = 'pending' AND created_at < now() - make_interval(secs => ?))
                    ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED""",
                    abandonedAfter.toSeconds(), pendingAfter.toSeconds(), BATCH);
            List<UUID> gone = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                try {
                    files.delete((String) row.get("storage_key"));
                    gone.add((UUID) row.get("id"));
                } catch (IOException | RuntimeException e) {
                    // the row stays: tried again at the next purge
                    log.warn("file of attachment {} not deleted: {}", row.get("id"), e.getClass().getSimpleName());
                }
            }
            for (UUID id : gone) {
                session.jdbc().update("DELETE FROM oro_attachment WHERE id = ?", id);
            }
            return gone.size();
        });
        return purged == null ? 0 : purged;
    }

    /** the files of the storage last changed more than a day ago that no attachment knows; the number removed */
    public int purgeOrphans() {
        FileStorage files = storage.getIfAvailable();
        if (files == null) {
            return 0;
        }
        List<String> candidates = new ArrayList<>();
        try {
            files.forEachKey(Instant.now().minus(Duration.ofDays(1)), candidates::add);
        } catch (IOException e) {
            log.warn("the files of the storage could not be listed: {}", e.getMessage());
            return 0;
        }
        int removed = 0;
        for (int from = 0; from < candidates.size(); from += BATCH) {
            List<String> batch = candidates.subList(from, Math.min(candidates.size(), from + BATCH));
            Set<String> known = new HashSet<>(session.systemTransaction().execute(status -> {
                session.enterSystem();
                return session.jdbc().queryForList("SELECT storage_key FROM oro_attachment WHERE storage_key = ANY(?)",
                        String.class, (Object) batch.toArray(String[]::new));
            }));
            for (String key : batch) {
                if (!known.contains(key)) {
                    try {
                        files.delete(key);
                        removed++;
                    } catch (IOException | RuntimeException e) {
                        log.warn("orphan file not deleted: {}", e.getClass().getSimpleName());
                    }
                }
            }
        }
        if (removed > 0) {
            log.info("{} file(s) without an attachment purged", removed);
        }
        return removed;
    }
}
