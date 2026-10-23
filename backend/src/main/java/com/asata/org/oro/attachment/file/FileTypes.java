package com.asata.org.oro.attachment.file;

import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * The type of an uploaded file, from its bytes (Apache Tika's magic numbers), never from what the client says alone. The
 * type the client declared is kept only for what Oro never shows: a file it declares as an image, an HTML page or a
 * script, but whose bytes say otherwise, gets the type of its bytes. So a file is an image to show (inline) only when
 * its bytes are one.
 */
public final class FileTypes {

    /** shown in the browser; anything else is downloaded (never an SVG or HTML inline: scripts) */
    public static final Set<String> INLINE = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    /** declared types Oro never takes from the client: what a browser would show or run */
    private static final Set<String> NEVER_DECLARED = Set.of("text/html", "application/xhtml+xml", "image/svg+xml",
            "text/javascript", "application/javascript", "text/xml", "application/xml");

    private static final Tika TIKA = new Tika();

    private FileTypes() {
    }

    /** the type of the bytes (the first ones), application/octet-stream when unknown */
    public static String detect(InputStream content) throws IOException {
        String detected = TIKA.detect(content);
        return detected == null ? "application/octet-stream" : detected.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * the type to record: the detected one when the bytes are an image to show, or when the client declares a type a
     * browser would show or run (an image, a page, a script) whatever the bytes; else the declared one, which only
     * labels a download (a .docx declared as such, detected as application/zip)
     */
    public static String resolve(String declared, String detected) {
        if (INLINE.contains(detected) || declared == null || declared.startsWith("image/") || NEVER_DECLARED.contains(declared)) {
            return detected;
        }
        return declared;
    }

    public static boolean inline(String mimeType) {
        return INLINE.contains(mimeType);
    }
}
