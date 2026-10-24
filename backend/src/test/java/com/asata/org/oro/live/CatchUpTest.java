package com.asata.org.oro.live;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract of a client whose stream was cut (backend README, Catching up), over HTTP as a browser does it: the
 * stream cut, two messages sent meanwhile, the stream opened again; hello, then the list and the messages after the
 * last one the client had give what it missed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatchUpTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String amani = TestTokens.oro("oro-amani");
    private final String baraka = TestTokens.oro("oro-baraka");

    private HttpRequest.Builder request(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oro" + path))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10));
    }

    private String get(String path, String token) throws Exception {
        HttpResponse<String> response = http.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private String post(String path, String token, String json) throws Exception {
        HttpResponse<String> response = http.send(request(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        return response.body();
    }

    private void send(String conversation, String token, String key, String body) throws Exception {
        post("/conversations/" + conversation + "/messages", token,
                "{\"clientMessageId\":\"%s\",\"type\":\"TEXT\",\"body\":\"%s\"}".formatted(key, body));
    }

    /** a stream opened, read up to its hello: the open stream, to cut */
    private InputStream openStream(String token) throws Exception {
        HttpResponse<InputStream> response = http.send(request("/stream", token).header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(1)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        for (String line; (line = lines.readLine()) != null; ) {
            if (line.startsWith("data:") && line.contains("\"hello\"")) {
                return response.body();
            }
        }
        throw new AssertionError("the stream ended before its hello");
    }

    @Test
    void aClientWhoseStreamWasCutReadsWhatItMissed() throws Exception {
        get("/me", baraka);
        String direct = JsonPath.read(post("/conversations/direct", amani, "{\"userId\":\"oro-baraka\"}"), "$.id");
        send(direct, amani, "catch-up-0001", "Before the cut");

        // Amani's stream, then cut (a train in a tunnel)
        InputStream stream = openStream(amani);
        long lastSeq = ((Number) JsonPath.read(get("/conversations/" + direct + "/messages", amani), "$.messages[0].seq")).longValue();
        stream.close();

        send(direct, baraka, "catch-up-0002", "While you were away");
        send(direct, baraka, "catch-up-0003", "Still away?");

        // back: hello, then the list and the messages after the last one Amani had
        openStream(amani).close();
        String list = get("/conversations", amani);
        assertThat((Integer) JsonPath.read(list, "$[0].unreadCount")).isEqualTo(2);
        assertThat((String) JsonPath.read(list, "$[0].lastMessage.body")).isEqualTo("Still away?");
        List<String> missed = JsonPath.read(get("/conversations/" + direct + "/messages?after=" + lastSeq, amani), "$.messages[*].body");
        assertThat(missed).containsExactly("While you were away", "Still away?");
    }
}
