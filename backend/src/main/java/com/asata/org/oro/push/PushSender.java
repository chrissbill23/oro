package com.asata.org.oro.push;

import com.asata.org.oro.context.DbSession;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Posts a push to a push service (RFC 8030): the payload encrypted for the subscription (RFC 8291), signed by Oro
 * (VAPID, RFC 8292), a topic per conversation (a newer push replaces one not delivered yet). Best effort, on a virtual
 * thread: the outbox never waits for a push service. A subscription the service says gone (404, 410) is removed.
 */
@Slf4j
@Component
public class PushSender {

    public record Subscription(UUID id, String endpoint, String p256dh, String auth) {
    }

    private final PushKeys keys;
    private final PushProperties properties;
    private final DbSession session;
    private final MeterRegistry meters;
    private final HttpClient http;
    private final ExecutorService senders = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("oro-push-", 0).factory());

    public PushSender(PushKeys keys, PushProperties properties, DbSession session, MeterRegistry meters) {
        this.keys = keys;
        this.properties = properties;
        this.session = session;
        this.meters = meters;
        this.http = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** queued: sent on its own thread */
    public void send(Subscription subscription, String payloadJson, String topic) {
        senders.execute(() -> sendNow(subscription, payloadJson, topic));
    }

    /** sent now; the outcome counted: sent, gone, failed */
    String sendNow(Subscription subscription, String payloadJson, String topic) {
        String outcome;
        try {
            URI endpoint = URI.create(subscription.endpoint());
            byte[] body = WebPushCrypto.encrypt(payloadJson.getBytes(StandardCharsets.UTF_8),
                    Base64.getUrlDecoder().decode(subscription.p256dh()), Base64.getUrlDecoder().decode(subscription.auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(properties.getTimeout())
                    .header("TTL", Long.toString(properties.getTtl().toSeconds()))
                    .header("Urgency", "normal")
                    .header("Topic", topic)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + vapid(endpoint) + ", k=" + keys.publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                outcome = "sent";
                update("UPDATE oro_push_subscription SET last_sent_at = now(), failures = 0 WHERE id = ?", subscription.id());
            } else if (status == 404 || status == 410) {
                outcome = "gone";
                update("DELETE FROM oro_push_subscription WHERE id = ?", subscription.id());
            } else {
                outcome = "failed";
                log.info("push refused by its service: HTTP {}", status);
                update("UPDATE oro_push_subscription SET failures = failures + 1 WHERE id = ?", subscription.id());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "failed";
        } catch (Exception e) {
            // never the endpoint nor the payload in the log
            log.info("push not sent: {}", e.getClass().getSimpleName());
            outcome = "failed";
        }
        meters.counter("oro.push.sent", "outcome", outcome).increment();
        return outcome;
    }

    /** the JWT of VAPID: the origin of the push service, 12 hours, Oro's contact */
    private String vapid(URI endpoint) throws java.security.GeneralSecurityException {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String audience = endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        String claims = b64.encodeToString("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                .formatted(audience, Instant.now().plusSeconds(12 * 3600).getEpochSecond(), properties.getSubject())
                .getBytes(StandardCharsets.UTF_8));
        String signed = header + "." + claims;
        return signed + "." + b64.encodeToString(WebPushCrypto.es256(keys.keys().getPrivate(), signed.getBytes(StandardCharsets.US_ASCII)));
    }

    private void update(String sql, UUID id) {
        session.systemTransaction().executeWithoutResult(status -> {
            session.enterSystem();
            session.jdbc().update(sql, id);
        });
    }

    @jakarta.annotation.PreDestroy
    void stop() {
        senders.shutdown();
    }
}
