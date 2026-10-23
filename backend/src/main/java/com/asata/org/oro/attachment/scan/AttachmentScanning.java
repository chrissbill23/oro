package com.asata.org.oro.attachment.scan;

import com.asata.org.oro.attachment.service.AttachmentService;
import com.asata.org.oro.attachment.storage.FileStorage;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.ScanStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The malware scan of the uploaded files, fail closed: a file is downloadable only once the scanner said clean. An
 * infected file is deleted from the storage at once (its row stays, infected: it can never be attached nor downloaded,
 * and the purge removes it). A file the scanner cannot read is failed, for good. When the scanner cannot be asked, the
 * file stays pending, and {@link #scanPending} asks again.
 */
@Slf4j
@Service
public class AttachmentScanning {

    private final ObjectProvider<MalwareScanner> scanner;
    private final ObjectProvider<FileStorage> storage;
    private final AttachmentService attachments;
    private final DbSession session;

    public AttachmentScanning(ObjectProvider<MalwareScanner> scanner, ObjectProvider<FileStorage> storage,
                              AttachmentService attachments, DbSession session) {
        this.scanner = scanner;
        this.storage = storage;
        this.attachments = attachments;
        this.session = session;
    }

    public boolean enabled() {
        return scanner.getIfAvailable() != null;
    }

    /** the scan of an uploaded file; PENDING when the scanner could not be asked */
    public ScanStatus scan(UUID attachmentId, String storageKey) {
        MalwareScanner clamd = scanner.getIfAvailable();
        FileStorage files = storage.getIfAvailable();
        if (clamd == null || files == null) {
            return ScanStatus.PENDING;
        }
        MalwareScanner.Verdict verdict;
        try (InputStream content = files.open(storageKey)) {
            verdict = clamd.scan(content);
        } catch (IOException e) {
            log.warn("attachment {} not scanned yet, the scanner could not be asked: {}", attachmentId, e.getClass().getSimpleName());
            return ScanStatus.PENDING;
        }
        ScanStatus status = switch (verdict.result()) {
            case CLEAN -> ScanStatus.CLEAN;
            case INFECTED -> ScanStatus.INFECTED;
            case FAILED -> ScanStatus.FAILED;
        };
        attachments.recordScan(attachmentId, status);
        if (status == ScanStatus.INFECTED) {
            log.warn("attachment {} infected ({}): its file is deleted", attachmentId, verdict.signature());
            try {
                files.delete(storageKey);
            } catch (IOException e) {
                log.error("the file of the infected attachment {} could not be deleted: the purge will", attachmentId);
            }
        } else if (status == ScanStatus.FAILED) {
            log.warn("attachment {} could not be scanned: never downloadable", attachmentId);
        }
        return status;
    }

    /** the uploaded files still pending (the scanner was away), scanned now; the number scanned */
    public int scanPending() {
        if (!enabled()) {
            return 0;
        }
        List<Map<String, Object>> pending = session.systemTransaction().execute(status -> {
            session.enterSystem();
            return session.jdbc().queryForList("""
                    SELECT id, storage_key FROM oro_attachment
                    WHERE upload_status = 'uploaded' AND scan_status = 'pending' AND deleted_at IS NULL
                    ORDER BY created_at LIMIT 50""");
        });
        int scanned = 0;
        for (Map<String, Object> row : pending == null ? List.<Map<String, Object>>of() : pending) {
            if (scan((UUID) row.get("id"), (String) row.get("storage_key")) == ScanStatus.PENDING) {
                // the scanner is still away: the next round
                break;
            }
            scanned++;
        }
        return scanned;
    }
}
