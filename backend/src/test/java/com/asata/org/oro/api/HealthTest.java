package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What Docker's health check reads (docker/healthcheck.sh, the management port in production, the API's in the
 * tests), without a token: liveness the process, readiness the database too, no details for anyone.
 */
@AutoConfigureMockMvc
class HealthTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private HealthEndpointGroups groups;

    @Test
    void livenessAndReadinessAnswerUpWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}", true));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void readinessNeedsTheDatabaseLivenessDoesNot() {
        assertThat(groups.get("readiness").isMember("db")).isTrue();
        assertThat(groups.get("readiness").isMember("readinessState")).isTrue();
        assertThat(groups.get("liveness").isMember("db")).isFalse();
    }
}
