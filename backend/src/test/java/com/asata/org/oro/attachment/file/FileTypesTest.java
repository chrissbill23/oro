package com.asata.org.oro.attachment.file;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** the type of a file is the one of its bytes whenever it decides what is shown */
class FileTypesTest {

    private static String detect(String content) throws Exception {
        return FileTypes.detect(new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void theBytesDecideWhatIsShown() throws Exception {
        assertThat(detect("<html><body><script>alert(1)</script></body></html>")).isEqualTo("text/html");
        assertThat(detect("\u0089PNG\r\n\u001a\n")).isEqualTo("image/png");
        assertThat(detect("%PDF-1.4 a small file")).isEqualTo("application/pdf");

        // a page or a script declared as an image is what it is: never shown
        assertThat(FileTypes.resolve("image/png", "text/html")).isEqualTo("text/html");
        assertThat(FileTypes.resolve("image/png", "application/octet-stream")).isEqualTo("application/octet-stream");
        assertThat(FileTypes.resolve("text/html", "text/plain")).isEqualTo("text/plain");
        assertThat(FileTypes.resolve("image/svg+xml", "application/xml")).isEqualTo("application/xml");
        // an image is the image its bytes are
        assertThat(FileTypes.resolve("image/png", "image/jpeg")).isEqualTo("image/jpeg");
        assertThat(FileTypes.resolve("application/pdf", "image/jpeg")).isEqualTo("image/jpeg");
        // the declared type labels a download the bytes cannot tell apart
        assertThat(FileTypes.resolve("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(FileTypes.resolve("text/csv", "text/plain")).isEqualTo("text/csv");

        assertThat(FileTypes.inline("image/jpeg")).isTrue();
        assertThat(FileTypes.inline("text/html")).isFalse();
    }
}
