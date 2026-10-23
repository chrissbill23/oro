package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** what a user may upload: its bytes not deleted, its files of the day; refused before anything is stored */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"oro.attachments.quota.bytes-per-user=100", "oro.attachments.quota.files-per-day=3"})
class StorageQuotaTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String conversationOf(String token) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/conversations/groups")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Files\",\"memberUserIds\":[]}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String conversationId, int bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[bytes]);
        return mvc.perform(multipart("/conversations/" + conversationId + "/attachments").file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void theBytesOfAUserAreBounded() throws Exception {
        String amani = TestTokens.oro("oro-amani");
        String group = conversationOf(amani);
        upload(amani, group, 40).andExpect(status().isCreated());
        upload(amani, group, 40).andExpect(status().isCreated());
        upload(amani, group, 40).andExpect(status().isPayloadTooLarge());
        assertThat(count("SELECT count(*) FROM oro_attachment")).isEqualTo(2);
        // a deleted file gives its bytes back
        admin.update("UPDATE oro_attachment SET deleted_at = now() WHERE id = (SELECT id FROM oro_attachment LIMIT 1)");
        upload(amani, group, 20).andExpect(status().isCreated());
        // another user has its own quota
        String baraka = TestTokens.oro("oro-baraka");
        upload(baraka, conversationOf(baraka), 90).andExpect(status().isCreated());
    }

    @Test
    void theFilesOfADayAreBoundedDeletedOrNot() throws Exception {
        String chausiku = TestTokens.oro("oro-chausiku");
        String group = conversationOf(chausiku);
        for (int i = 0; i < 3; i++) {
            upload(chausiku, group, 5).andExpect(status().isCreated());
        }
        // deleting does not give uploads back
        admin.update("UPDATE oro_attachment SET deleted_at = now()");
        upload(chausiku, group, 5).andExpect(status().isTooManyRequests()).andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"));
        // a day later (the trigger keeps the time of an upload: off for this one statement of the test)
        admin.execute("SET session_replication_role = replica; "
                + "UPDATE oro_attachment SET created_at = created_at - interval '25 hours'; SET session_replication_role = origin");
        upload(chausiku, group, 5).andExpect(status().isCreated());
    }
}
