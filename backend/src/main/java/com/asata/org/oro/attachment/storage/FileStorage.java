package com.asata.org.oro.attachment.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * The object storage of the files of the attachments, by their random storage key (never a file name). The database
 * holds only their metadata.
 */
public interface FileStorage {

    /** the file stored under the key, its real size and SHA-256; refused (nothing kept) beyond maxBytes */
    Stored put(String key, InputStream content, long maxBytes) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;

    /**
     * the keys of the stored files last changed before the time, for the purge of the files without an attachment;
     * nothing by default (an object storage lists its objects with its own API)
     */
    default void forEachKey(java.time.Instant changedBefore, java.util.function.Consumer<String> key) throws IOException {
    }

    /** refuses (ServiceUnavailable) a file of this size when the storage is short of space; nothing by default */
    default void requireSpace(long bytes) {
    }

    /** what was stored: the real size and checksum, which the database records (not the ones the client said) */
    record Stored(long sizeBytes, String sha256) {
    }

    /** the file is bigger than allowed */
    class TooLarge extends IOException {
        public TooLarge(long maxBytes) {
            super("The file is bigger than " + maxBytes + " bytes");
        }
    }
}
