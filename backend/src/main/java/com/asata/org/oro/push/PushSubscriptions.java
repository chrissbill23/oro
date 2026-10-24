package com.asata.org.oro.push;

import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Base64;

/**
 * The browsers of an identity that take its notifications. A subscription names the push service Oro will post to:
 * only the known ones (oro.push.allowed-hosts), over HTTPS, never an address of Oro's network. Written as oro_system (a
 * browser moves from an identity to another), read and removed by its identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptions {

    private final DbSession session;
    private final JdbcTemplate jdbc;
    private final PushProperties properties;

    public void subscribe(Actor actor, String endpoint, String p256dh, String auth, String userAgent) {
        requireEndpoint(endpoint);
        try {
            WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(p256dh));
            if (Base64.getUrlDecoder().decode(auth).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("keys: p256dh, a P-256 point, and auth, 16 bytes, in base64url");
        }
        String agent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512));
        session.systemTransaction().executeWithoutResult(status -> {
            session.enterSystem();
            session.jdbc().update("""
                    INSERT INTO oro_push_subscription(identity_id, product_id, endpoint, p256dh, auth, user_agent)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (endpoint) DO UPDATE SET identity_id = EXCLUDED.identity_id, product_id = EXCLUDED.product_id,
                           p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth, user_agent = EXCLUDED.user_agent, failures = 0""",
                    actor.identityId(), actor.product().code(), endpoint, p256dh, auth, agent);
            // its browsers: the newest ones
            session.jdbc().update("""
                    DELETE FROM oro_push_subscription WHERE identity_id = ? AND id NOT IN (
                        SELECT id FROM oro_push_subscription WHERE identity_id = ? ORDER BY created_at DESC LIMIT ?)""",
                    actor.identityId(), actor.identityId(), properties.getMaxSubscriptions());
        });
        log.info("identity {} subscribed a browser to its pushes", actor.identityId());
    }

    @Transactional
    public void unsubscribe(Actor actor, String endpoint) {
        session.enterUser(actor);
        jdbc.update("DELETE FROM oro_push_subscription WHERE endpoint = ?", endpoint);
    }

    /** the endpoint of a known push service, over HTTPS (http: the tests only) */
    void requireEndpoint(String endpoint) {
        URI uri;
        try {
            uri = endpoint == null || endpoint.length() > 1024 ? null : URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        String host = uri == null || uri.getHost() == null ? null : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean scheme = uri != null && ("https".equals(uri.getScheme()) || (properties.isAllowHttp() && "http".equals(uri.getScheme())));
        boolean known = host != null && properties.getAllowedHosts().stream()
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
        if (!scheme || !known || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("endpoint: the HTTPS address of a known push service");
        }
    }

}
