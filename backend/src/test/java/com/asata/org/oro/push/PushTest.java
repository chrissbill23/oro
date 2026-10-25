package com.asata.org.oro.push;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestTokens;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.conversation.entity.Conversation;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.live.LiveHub;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pushes of Oro, against a push service of the test: to the members away only, who and where but never the text,
 * encrypted for the browser (decrypted here with its private key), signed by Oro; the endpoints of unknown services
 * refused; a subscription gone removed.
 */
@AutoConfigureMockMvc
class PushTest extends PostgresIntegrationTest {

    /** what the push service of the test received */
    record Received(String path, Map<String, String> headers, byte[] body) {
    }

    private static final HttpServer SERVICE;
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static volatile int answer = 201;

    static {
        try {
            SERVICE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVICE.createContext("/push/", exchange -> {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.getFirst()));
            RECEIVED.add(new Received(exchange.getRequestURI().getPath(), headers, exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        SERVICE.start();
    }

    @DynamicPropertySource
    static void pushService(DynamicPropertyRegistry registry) {
        registry.add("oro.push.allowed-hosts", () -> "localhost");
        registry.add("oro.push.allow-http", () -> "true");
    }

    @AfterAll
    static void stop() {
        SERVICE.stop(0);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PushKeys pushKeys;
    @Autowired
    private LiveHub hub;
    @Autowired
    private PushOutboxEventHandler pushHandler;

    /** a browser: its key pair and auth secret, as pushManager.subscribe makes them */
    record Browser(KeyPair keys, byte[] auth, String endpoint) {
        String p256dh() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(WebPushCrypto.uncompressed((ECPublicKey) keys.getPublic()));
        }

        String authB64() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
        }
    }

    private Browser browser() {
        byte[] auth = new byte[16];
        new Random().nextBytes(auth);
        return new Browser(WebPushCrypto.newKeyPair(), auth,
                "http://localhost:" + SERVICE.getAddress().getPort() + "/push/" + UUID.randomUUID());
    }

    private ResultActions subscribe(String userId, String endpoint, String p256dh, String auth) throws Exception {
        return mvc.perform(put("/push/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.oro(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint, p256dh, auth)));
    }

    private void subscribe(String userId, Browser browser) throws Exception {
        subscribe(userId, browser.endpoint(), browser.p256dh(), browser.authB64()).andExpect(status().isNoContent());
    }

    /** the outbox published, then the pushes (on their threads) received */
    private List<Received> publishAndWait(int expected) throws InterruptedException {
        outboxWorker.runOnce("test");
        long deadline = System.currentTimeMillis() + 5000;
        while (RECEIVED.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(200);
        return List.copyOf(RECEIVED);
    }

    /** what the browser reads: the payload decrypted with its private key (RFC 8291) */
    private static String decrypt(Browser browser, byte[] body) throws Exception {
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        in.getInt();
        byte[] asPublic = new byte[in.get()];
        in.get(asPublic);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.keys().getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] uaPublic = WebPushCrypto.uncompressed((ECPublicKey) browser.keys().getPublic());
        byte[] info = ByteBuffer.allocate(14 + 65 + 65).put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(uaPublic).put(asPublic).array();
        byte[] ikm = WebPushCrypto.hkdf(browser.auth(), agreement.generateSecret(), info, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        return new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void reset() {
        RECEIVED.clear();
        answer = 201;
    }

    @Test
    void onlyTheEndpointsOfKnownPushServices() throws Exception {
        oroUser("amani");
        Browser browser = browser();
        subscribe("oro-amani", "https://evil.example/push/1", browser.p256dh(), browser.authB64()).andExpect(status().isBadRequest());
        subscribe("oro-amani", "http://169.254.169.254/latest/meta-data", browser.p256dh(), browser.authB64()).andExpect(status().isBadRequest());
        subscribe("oro-amani", "http://localhost.evil.example/push/1", browser.p256dh(), browser.authB64()).andExpect(status().isBadRequest());
        subscribe("oro-amani", browser.endpoint(), "not-a-key", browser.authB64()).andExpect(status().isBadRequest());
        subscribe("oro-amani", browser.endpoint(), browser.p256dh(), browser.authB64()).andExpect(status().isNoContent());
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_push_subscription", Integer.class)).isEqualTo(1);
    }

    @Test
    void someoneAwayGetsWhoAndWhereNeverTheText() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID direct = conversations.openDirect(Actor.oro(amani), baraka).getId();
        acceptPending();
        Browser barakas = browser();
        subscribe("oro-baraka", barakas);
        subscribe("oro-amani", browser());

        say(Actor.oro(amani), direct, "The secret of the family recipe");
        List<Received> received = publishAndWait(1);

        // Baraka only (not its sender), once
        assertThat(received).singleElement().satisfies(push -> {
            assertThat(push.path()).isEqualTo(java.net.URI.create(barakas.endpoint()).getPath());
            assertThat(push.headers()).containsEntry("content-encoding", "aes128gcm").containsEntry("ttl", "86400")
                    .containsEntry("topic", direct.toString().replace("-", ""));
            assertThat(push.headers().get("authorization")).startsWith("vapid t=").endsWith("k=" + pushKeys.publicKey());
            assertThat(new String(push.body(), StandardCharsets.ISO_8859_1)).doesNotContain("secret");
            String payload = decrypt(barakas, push.body());
            assertThat(payload).doesNotContain("secret", "recipe");
            assertThat((String) JsonPath.read(payload, "$.body")).isEqualTo("New message");
            assertThat((String) JsonPath.read(payload, "$.conversationId")).isEqualTo(direct.toString());
        });
    }

    @Test
    void noPushToWhoIsConnectedMutedOrNotYetAccepted() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID nia = oroUser("nia");
        UUID chausiku = oroUser("chausiku");
        Conversation family = conversations.createGroup(Actor.oro(amani), "Family", null, List.of(baraka, chausiku));
        acceptPending();
        // Nia, a stranger, invited: pending
        conversations.addMember(Actor.oro(amani), family.getId(), nia, false);
        for (String user : List.of("oro-baraka", "oro-nia", "oro-chausiku")) {
            subscribe(user, browser());
        }
        // Baraka is here (a stream open), Chausiku muted the family
        hub.connect(Actor.oro(baraka), "oro-baraka", Instant.now().plusSeconds(600));
        conversations.mute(Actor.oro(chausiku), family.getId(), OffsetDateTime.now().plusHours(1));

        say(Actor.oro(amani), family.getId(), "Dinner at 7");
        assertThat(publishAndWait(1)).isEmpty();
        hub.close(baraka);
    }

    /** ORO-SCL-010: the outbox delivers at least once; a push goes at most once per event and device */
    @Test
    void anEventHandledAgainPushesNothingTwice() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID direct = conversations.openDirect(Actor.oro(amani), baraka).getId();
        acceptPending();
        subscribe("oro-baraka", browser());
        say(Actor.oro(amani), direct, "Hello");
        assertThat(publishAndWait(1)).hasSize(1);

        // replayed (ops/outbox-replay.sh, or a retry after another handler failed): the push is not sent again
        admin.update("""
                UPDATE oro_outbox_event SET status = 'pending', next_attempt_at = now(), processed_at = NULL, locked_by = NULL
                WHERE event_type = 'message.created'""");
        assertThat(publishAndWait(2)).hasSize(1);

        // a device subscribed after the first time gets it on the replay: it never had it
        subscribe("oro-baraka", browser());
        admin.update("""
                UPDATE oro_outbox_event SET status = 'pending', next_attempt_at = now(), processed_at = NULL, locked_by = NULL
                WHERE event_type = 'message.created'""");
        assertThat(publishAndWait(2)).hasSize(2);
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_push_delivery", Integer.class)).isEqualTo(2);

        // an hour later the records go
        admin.update("UPDATE oro_push_delivery SET created_at = now() - interval '2 hours'");
        assertThat(pushHandler.purgeDeliveries()).isEqualTo(2);
    }

    @Test
    void aSubscriptionGoneIsRemoved() throws Exception {
        UUID amani = oroUser("amani");
        UUID baraka = oroUser("baraka");
        UUID direct = conversations.openDirect(Actor.oro(amani), baraka).getId();
        acceptPending();
        subscribe("oro-baraka", browser());
        answer = 410;
        say(Actor.oro(amani), direct, "Hello");
        assertThat(publishAndWait(1)).hasSize(1);
        long deadline = System.currentTimeMillis() + 3000;
        while (admin.queryForObject("SELECT count(*) FROM oro_push_subscription", Integer.class) > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(admin.queryForObject("SELECT count(*) FROM oro_push_subscription", Integer.class)).isZero();
    }

    @Test
    void aGroupSaysItsNameAndTheSendersFirstName() throws Exception {
        // the user ids of Oro's identities are Asata accounts: their first name
        UUID amaniAccount = UUID.randomUUID();
        admin.update("INSERT INTO asata_user(id, username, email) VALUES (?, 'amani', 'amani@example.org')", amaniAccount);
        admin.update("INSERT INTO asata_user_personal_info(user_id, given_name) VALUES (?, 'Amani')", amaniAccount);
        UUID amani = identities.provision(Product.ORO, amaniAccount.toString());
        UUID baraka = oroUser("baraka");
        Conversation family = conversations.createGroup(Actor.oro(amani), "Family", null, List.of(baraka));
        acceptPending();
        Browser barakas = browser();
        subscribe("oro-baraka", barakas);

        say(Actor.oro(amani), family.getId(), "Hi all");
        String payload = decrypt(barakas, publishAndWait(1).getFirst().body());
        assertThat((String) JsonPath.read(payload, "$.title")).isEqualTo("Family");
        assertThat((String) JsonPath.read(payload, "$.body")).isEqualTo("New message from Amani");
    }
}
