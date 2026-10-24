package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.config.metrics.OroMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** what Prometheus reads (the management port in production, the API's in the tests), without a token */
@AutoConfigureMockMvc
class MetricsTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OroMetrics metrics;

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
    }

    /** the value of a series (its name, then its labels or none) */
    private static double value(String scrape, String series) {
        Matcher m = Pattern.compile("^" + Pattern.quote(series) + "(\\{[^}]*\\})? ([0-9.eE+-]+|NaN)$", Pattern.MULTILINE).matcher(scrape);
        assertThat(m.find()).as(series).isTrue();
        return Double.parseDouble(m.group(2));
    }

    @Test
    void theGaugesOfOroFollowTheDatabase() throws Exception {
        UUID amani = oroUser("amani");
        admin.update("""
                INSERT INTO oro_outbox_event(id, product_id, event_type, aggregate_type, aggregate_id, payload, status, created_at)
                VALUES (gen_random_uuid(), 'ORO', 'message.created', 'message', ?, '{}', 'failed', now() - interval '1 hour'),
                       (gen_random_uuid(), 'ORO', 'message.created', 'message', ?, '{}', 'pending', now() - interval '2 minutes')""",
                amani, amani);
        metrics.refresh();

        String scrape = scrape();
        assertThat(value(scrape, "oro_outbox_failed")).isEqualTo(1.0);
        assertThat(value(scrape, "oro_outbox_pending")).isEqualTo(1.0);
        assertThat(value(scrape, "oro_outbox_oldest_pending_seconds")).isBetween(110.0, 200.0);
        assertThat(value(scrape, "oro_attachments_scan_pending")).isZero();
        assertThat(value(scrape, "oro_streams_open")).isZero();
        assertThat(value(scrape, "oro_attachments_disk_free_bytes")).isPositive();
        // the WAL archiving of the database of the tests is off: no last archive, NaN
        assertThat(scrape).contains("oro_wal_archive_age_seconds", "oro_files_backup_age_seconds", "jvm_memory_used_bytes",
                "oro_restore_check_age_seconds{application=\"oro\",what=\"database\"}",
                "oro_restore_check_age_seconds{application=\"oro\",what=\"files\"}",
                "hikaricp_connections_active", "logback_events_total");
    }

    @Test
    void theRequestsAreCountedAndTimed() throws Exception {
        String chausiku = TestTokens.oro("oro-chausiku");
        String group = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/conversations/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + chausiku).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Metrics\",\"memberUserIds\":[]}")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/conversations/" + group + "/messages").header(HttpHeaders.AUTHORIZATION, "Bearer " + chausiku)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientMessageId\":\"metrics-0001\",\"type\":\"TEXT\",\"body\":\"Hello\"}"));
        // five groups at once at most: the sixth refused
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/conversations/groups").header(HttpHeaders.AUTHORIZATION, "Bearer " + chausiku)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"More\",\"memberUserIds\":[]}"));
        }

        String scrape = scrape();
        assertThat(scrape).containsPattern("oro_messages_send_seconds_count\\{[^}]*outcome=\"created\"[^}]*\\} [1-9]")
                .containsPattern("oro_conversation_lock_seconds_count\\{[^}]*\\} [1-9]")
                .containsPattern("oro_rate_limit_refused_total\\{[^}]*kind=\"create-group\"[^}]*\\} [1-9]")
                .containsPattern("http_server_requests_seconds_count\\{[^}]*status=\"429\"[^}]*uri=\"/conversations/groups\"");
    }
}
