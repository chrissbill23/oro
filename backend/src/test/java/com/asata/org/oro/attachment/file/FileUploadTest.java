package com.asata.org.oro.attachment.file;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** over HTTP: what a file is comes from its bytes, and a photo is stored without its metadata */
@AutoConfigureMockMvc
class FileUploadTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final String amani = TestTokens.oro("oro-amani");

    private String group() throws Exception {
        return JsonPath.read(mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Files\",\"memberUserIds\":[]}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String upload(String group, String name, String declared, byte[] content, String expectedType) throws Exception {
        return JsonPath.read(mvc.perform(multipart("/conversations/" + group + "/attachments")
                        .file(new MockMultipartFile("file", name, declared, content)).header(HttpHeaders.AUTHORIZATION, "Bearer " + amani))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value(expectedType))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void aPageDeclaredAsAnImageIsNeverShown() throws Exception {
        String group = group();
        String page = upload(group, "cat.png", "image/png",
                "<html><body><script>fetch('/oro/me')</script></body></html>".getBytes(StandardCharsets.UTF_8), "text/html");
        mvc.perform(get("/attachments/" + page + "/content").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")));
    }

    @Test
    void aPhotoIsStoredWithoutItsMetadata() throws Exception {
        byte[] plain = ImageMetadataTest.encode("jpg");
        int afterApp0 = 2 + 2 + ((plain[4] & 0xFF) << 8 | (plain[5] & 0xFF));
        ByteArrayOutputStream photo = new ByteArrayOutputStream();
        photo.write(plain, 0, afterApp0);
        photo.writeBytes(ImageMetadataTest.segment(0xE1, ImageMetadataTest.exif()));
        photo.write(plain, afterApp0, plain.length - afterApp0);
        byte[] taken = photo.toByteArray();

        String group = group();
        // declared as a PNG: a JPEG it is, and shown as one
        String id = upload(group, "IMG_2041.png", "image/png", taken, "image/jpeg");
        byte[] stored = mvc.perform(get("/attachments/" + id + "/content").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(ImageMetadataTest.SECRET);
        assertThat(stored.length).isLessThan(taken.length);
        // its size and checksum are the stored file's
        assertThat(count("SELECT size_bytes FROM oro_attachment WHERE id = ?::uuid", id)).isEqualTo(stored.length);
    }

    @Test
    void aBrokenImageIsRefusedAndNothingStored() throws Exception {
        String group = group();
        // the bytes of a JPEG, cut short: detected as one, but not readable
        byte[] cut = java.util.Arrays.copyOf(ImageMetadataTest.encode("jpg"), 6);
        mvc.perform(multipart("/conversations/" + group + "/attachments")
                        .file(new MockMultipartFile("file", "broken.jpg", "image/jpeg", cut)).header(HttpHeaders.AUTHORIZATION, "Bearer " + amani))
                .andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM oro_attachment WHERE upload_status = 'uploaded'")).isZero();
    }
}
