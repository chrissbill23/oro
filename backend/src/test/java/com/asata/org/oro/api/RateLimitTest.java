package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** the limits of application.yml, over HTTP: 429 with Retry-After, before any work, per user */
@AutoConfigureMockMvc
class RateLimitTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aUserOverALimitWaitsAndNothingIsDone() throws Exception {
        String amani = TestTokens.oro("oro-amani");
        oroUser("baraka");
        // create-group: a burst of 5
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Group " + i + "\",\"memberUserIds\":[]}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"One too many\",\"memberUserIds\":[]}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "20"))
                .andExpect(jsonPath("$.status").value(429));
        assertThat(count("SELECT count(*) FROM oro_conversation")).isEqualTo(5);

        // the other requests of Amani, and the groups of Baraka, are not limited by it
        mvc.perform(get("/conversations").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)).andExpect(status().isOk());
        mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro("oro-baraka"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine\",\"memberUserIds\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void everyRequestCountsInAny() throws Exception {
        String chausiku = TestTokens.oro("oro-chausiku");
        int ok = 0;
        int limited = 0;
        for (int i = 0; i < 110; i++) {
            int status = mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + chausiku))
                    .andReturn().getResponse().getStatus();
            if (status == 200) {
                ok++;
            } else if (status == 429) {
                limited++;
            }
        }
        // a burst of 100, plus what refilled meanwhile (5 a second)
        assertThat(ok).isBetween(100, 105);
        assertThat(limited).isEqualTo(110 - ok);
    }
}
