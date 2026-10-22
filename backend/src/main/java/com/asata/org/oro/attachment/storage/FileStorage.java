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
