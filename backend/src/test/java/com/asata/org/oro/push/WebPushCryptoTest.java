package com.asata.org.oro.push;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** the encryption of Web Push against the example of RFC 8291 (section 5), byte for byte */
class WebPushCryptoTest {

    private static byte[] b64(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    @Test
    void theExampleOfRfc8291() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] asPublic = b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] asPrivate = b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec p256 = parameters.getParameterSpec(ECParameterSpec.class);
        KeyPair asKeys = new KeyPair(WebPushCrypto.publicKey(asPublic),
                KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, asPrivate), p256)));

        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, authSecret, asKeys, salt);

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void theVapidKeysRoundTripAndSign() throws Exception {
        KeyPair generated = WebPushCrypto.newKeyPair();
        String pkcs8 = Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded());
        KeyPair read = WebPushCrypto.keyPair(pkcs8);
        // the public key derived from the private one is the one generated with it
        assertThat(WebPushCrypto.uncompressed((ECPublicKey) read.getPublic()))
                .isEqualTo(WebPushCrypto.uncompressed((ECPublicKey) generated.getPublic()));
        byte[] data = "header.claims".getBytes(StandardCharsets.US_ASCII);
        byte[] jws = WebPushCrypto.es256(read.getPrivate(), data);
        assertThat(jws).hasSize(64);
        // back to DER, verified by the JDK
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(generated.getPublic());
        verify.update(data);
        assertThat(verify.verify(jws)).isTrue();
    }
}
