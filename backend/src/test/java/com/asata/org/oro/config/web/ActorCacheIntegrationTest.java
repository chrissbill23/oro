package com.asata.org.oro.config.web;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.identity.service.OrganizationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The identity and the access of a request are cached (ActorCache), yet whatever takes them away refuses the very next
 * request: a revocation, a suspension, a synchronization that removes the person; a grant and a reactivation count at
 * once.
 */
@AutoConfigureMockMvc
class ActorCacheIntegrationTest extends PostgresIntegrationTest {

    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrganizationSyncService sync;

    @BeforeEach
    void jordanAtAcme() {
        sync.sync(ACME, 1, List.of("umoja-jordan", "umoja-amara"), List.of());
    }

    private ResultActions list() throws Exception {
        return mvc.perform(get("/conversations").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.umoja("umoja-jordan"))
                .header("x-organization-id", ACME.toString()));
    }

    @Test
    void aRevocationRefusesTheNextRequest() throws Exception {
        list().andExpect(status().isOk());
        list().andExpect(status().isOk());

        mvc.perform(delete("/internal/organizations/{org}/members/umoja-jordan", ACME).header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isNoContent());
        list().andExpect(status().isForbidden());

        mvc.perform(put("/internal/organizations/{org}/members/umoja-jordan", ACME).header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isNoContent());
        list().andExpect(status().isOk());
    }

    @Test
    void aSuspensionRefusesTheNextRequest() throws Exception {
        list().andExpect(status().isOk());
        mvc.perform(post("/internal/identities/umoja-jordan/suspend").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isNoContent());
        list().andExpect(status().isForbidden());
        mvc.perform(post("/internal/identities/umoja-jordan/reactivate").header("x-integration-key", UMOJA_KEY))
                .andExpect(status().isNoContent());
        list().andExpect(status().isOk());
    }

    @Test
    void aSynchronizationWithoutThePersonRefusesItsNextRequest() throws Exception {
        list().andExpect(status().isOk());
        sync.sync(ACME, 2, List.of("umoja-amara"), List.of());
        list().andExpect(status().isForbidden());
    }
}
