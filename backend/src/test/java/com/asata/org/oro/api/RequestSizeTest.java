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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** a request that is not an upload carries 1 MiB at most, with a length or without */
@AutoConfigureMockMvc
class RequestSizeTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aBodyOverAMegabyteIsRefused() throws Exception {
        String amani = TestTokens.oro("oro-amani");
        String huge = "{\"name\":\"" + "x".repeat(2 * 1024 * 1024) + "\",\"memberUserIds\":[]}";
        // its length said
        mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                .contentType(MediaType.APPLICATION_JSON).content(huge)).andExpect(status().isPayloadTooLarge());
        // its length not said (chunked): cut while it is read
        mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                        .contentType(MediaType.APPLICATION_JSON).content(huge)
                        .with(request -> {
                            request.removeHeader(HttpHeaders.CONTENT_LENGTH);
                            return new org.springframework.mock.web.MockHttpServletRequest(request.getServletContext()) {
                                {
                                    setMethod(request.getMethod());
                                    setRequestURI(request.getRequestURI());
                                    setContentType(request.getContentType());
                                    setContent(request.getContentAsByteArray());
                                    addHeader(HttpHeaders.AUTHORIZATION, request.getHeader(HttpHeaders.AUTHORIZATION));
                                }

                                @Override
                                public long getContentLengthLong() {
                                    return -1;
                                }

                                @Override
                                public int getContentLength() {
                                    return -1;
                                }
                            };
                        }))
                .andExpect(status().isPayloadTooLarge());
        assertThat(count("SELECT count(*) FROM oro_conversation")).isZero();
        // a normal one goes through
        mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + amani)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Family\",\"memberUserIds\":[]}"))
                .andExpect(status().isCreated());
    }
}
