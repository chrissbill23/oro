package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** what the database refuses is logged without the refused row: never the text of a message */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogPrivacyTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String oro(String userId) {
        return com.asata.org.oro.TestTokens.oro(userId);
    }

    @Test
    void aMessageRefusedByTheDatabaseIsNotInTheLogs(CapturedOutput output) throws Exception {
        String amani = oro("oro-amani");
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oro("oro-baraka"))).andExpect(status().isOk());
        String direct = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/conversations/direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"oro-baraka\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        // the metadata is over the 8 KB of the database: the check constraint refuses the whole row
        String secret = "PRIVATE-WORDS-" + java.util.UUID.randomUUID();
        String send = "{\"clientMessageId\":\"client-0001\",\"type\":\"TEXT\",\"body\":\"%s\",\"metadata\":{\"note\":\"%s\"}}"
                .formatted(secret, "x".repeat(9000));
        mvc.perform(post("/conversations/" + direct + "/messages").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                .contentType(MediaType.APPLICATION_JSON).content(send)).andExpect(status().isConflict());

        assertThat(output.getAll()).contains("ck_oro_message").doesNotContain(secret).doesNotContain("Failing row");
    }
}
