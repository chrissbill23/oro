package com.asata.org.oro.attachment.api;

import com.asata.org.oro.attachment.entity.Attachment;
import com.asata.org.oro.attachment.file.FileTypes;
import com.asata.org.oro.attachment.file.ImageMetadata;
import com.asata.org.oro.attachment.scan.AttachmentScanning;
import com.asata.org.oro.attachment.service.AttachmentQuota;
import com.asata.org.oro.attachment.service.AttachmentService;
import com.asata.org.oro.attachment.storage.FileStorage;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.ScanStatus;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.Unprocessable;
import com.asata.org.oro.message.dto.MessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The files go through the API to the storage (FileStorage): the API records their real size and checksum, then the
 * malware scan. Without a scanner (oro.attachments.scan: none, development only) a file is recorded clean unscanned.
 */
@Slf4j
@RestController
public class AttachmentController implements AttachmentApi {

    private static final Pattern MIME = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*");

    private final AttachmentService attachments;
    private final AttachmentQuota quota;
    private final AttachmentScanning scanning;
    private final ObjectProvider<FileStorage> storage;
    private final long maxSizeBytes;
    private final boolean scanless;
    private final String multipartLocation;

    public AttachmentController(AttachmentService attachments, AttachmentQuota quota, AttachmentScanning scanning,
                                ObjectProvider<FileStorage> storage,
                                @Value("${oro.attachments.max-size-bytes:104857600}") long maxSizeBytes,
                                @Value("${oro.attachments.scan:none}") String scan,
                                @Value("${spring.servlet.multipart.location:}") String multipartLocation) {
        this.multipartLocation = multipartLocation;
        this.attachments = attachments;
        this.quota = quota;
        this.scanning = scanning;
        this.storage = storage;
        this.maxSizeBytes = maxSizeBytes;
        this.scanless = "none".equals(scan);
    }

    @Override
    public MessageDTO.AttachmentDTO upload(Actor actor, UUID conversationId, MultipartFile file) {
        FileStorage files = requireStorage();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        // before anything is registered or stored: the quota of the user, then the space of the storage
        quota.check(actor.identityId(), file.getSize());
        files.requireSpace(file.getSize());
        // the type of the bytes, not only the client's word: what is shown inline is an image indeed
        String type;
        try (InputStream head = file.getInputStream()) {
            type = mimeType(FileTypes.resolve(mimeType(file.getContentType()), FileTypes.detect(head)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Attachment attachment = attachments.register(actor, conversationId, fileName(file.getOriginalFilename()), type,
                file.getSize());
        FileStorage.Stored stored;
        Path cleaned = null;
        try {
            InputStream content;
            if (ImageMetadata.cleans(type)) {
                // a photo is stored without its metadata (where, when, with what): a copy, cleaned, then stored
                cleaned = Files.createTempFile(temporaryDirectory(), ".clean-", ".tmp");
                try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(cleaned)) {
                    ImageMetadata.strip(type, in, out);
                }
                content = Files.newInputStream(cleaned);
            } else {
                content = file.getInputStream();
            }
            try (content) {
                stored = files.put(attachment.getStorageKey(), content, maxSizeBytes);
            }
        } catch (FileStorage.TooLarge e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (ImageMetadata.InvalidImage e) {
            // nothing stored: the registered upload stays pending, purged within the hour
            throw new IllegalArgumentException("The image could not be read (" + e.getMessage() + ")");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(cleaned);
        }
        attachments.markUploaded(attachment.getId(), stored.sizeBytes(), stored.sha256());
        boolean available;
        if (scanless) {
            log.warn("attachment {} recorded clean without a malware scan (oro.attachments.scan: none)", attachment.getId());
            attachments.recordScan(attachment.getId(), ScanStatus.CLEAN);
            available = true;
        } else {
            // fail closed: downloadable once the scanner said clean; pending (scanned again later) if it is away
            ScanStatus scanned = scanning.scan(attachment.getId(), attachment.getStorageKey());
            if (scanned == ScanStatus.INFECTED) {
                throw new Unprocessable("The file was refused: the malware scan found a threat in it");
            }
            available = scanned == ScanStatus.CLEAN;
        }
        return new MessageDTO.AttachmentDTO(attachment.getId(), attachment.getFileName(), attachment.getMimeType(),
                stored.sizeBytes(), available);
    }

    @Override
    public ResponseEntity<InputStreamResource> download(Actor actor, UUID attachmentId) {
        FileStorage files = requireStorage();
        Attachment attachment = attachments.requireDownloadable(actor, attachmentId);
        boolean inline = FileTypes.inline(attachment.getMimeType());
        InputStream content;
        try {
            content = files.open(attachment.getStorageKey());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.ok()
                .contentType(inline ? MediaType.parseMediaType(attachment.getMimeType()) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(attachment.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new InputStreamResource(content));
    }

    /** where the cleaned copies are written: where the uploads are received (the data volume), else the system's */
    private Path temporaryDirectory() {
        return multipartLocation == null || multipartLocation.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(multipartLocation);
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("temporary file not deleted: {}", file.getFileName());
            }
        }
    }

    private FileStorage requireStorage() {
        FileStorage files = storage.getIfAvailable();
        if (files == null) {
            throw new Conflict("No file storage is configured");
        }
        return files;
    }

    /** a name to show: no path, no control character, 255 characters at most */
    static String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    static String mimeType(String contentType) {
        String type = contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        return MIME.matcher(type).matches() && type.length() <= 127 ? type : "application/octet-stream";
    }
}
