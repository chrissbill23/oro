package com.asata.org.oro.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * Oro's VAPID keys (RFC 8292): the browsers subscribe with the public one (applicationServerKey), Oro signs its pushes
 * with the private one. Without a key, a new one at each start: the subscriptions die with it (development only).
 */
@Slf4j
@Component
public class PushKeys {

    private final KeyPair keys;
    private final String publicKey;
    private final boolean generated;

    public PushKeys(PushProperties properties) {
        String configured = properties.getVapidPrivateKey();
        this.generated = configured == null || configured.isBlank();
        this.keys = generated ? WebPushCrypto.newKeyPair() : WebPushCrypto.keyPair(configured);
        if (generated && properties.isEnabled()) {
            log.warn("oro.push.vapid-private-key (ORO_PUSH_VAPID_PRIVATE_KEY) is not set: new push keys for this run, "
                    + "the browsers subscribe again after a restart (development only)");
        }
        this.publicKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(WebPushCrypto.uncompressed((ECPublicKey) keys.getPublic()));
    }

    /** the public key as the browsers take it: base64url of the 65 bytes */
    public String publicKey() {
        return publicKey;
    }

    KeyPair keys() {
        return keys;
    }

    public boolean generated() {
        return generated;
    }
}
