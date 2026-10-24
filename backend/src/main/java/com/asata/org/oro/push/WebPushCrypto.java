package com.asata.org.oro.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * The encryption of a Web Push message (RFC 8291, aes128gcm of RFC 8188) with the JDK only: an ephemeral P-256 key
 * agreed with the subscription's (p256dh), mixed with its auth secret; the push service relays bytes it cannot read.
 * And the keys and signatures of VAPID (RFC 8292, ES256).
 */
public final class WebPushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256;

    static {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            P256 = parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebPushCrypto() {
    }

    /** the body to post: salt, record size, the sender's ephemeral public key, then the encrypted payload */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256);
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return encrypt(plaintext, uaPublic, authSecret, generator.generateKeyPair(), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot encrypt a push", e);
        }
    }

    /** with a given ephemeral key and salt: the test vector of RFC 8291 */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        byte[] asPublic = uncompressed((ECPublicKey) asKeys.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // the input keying material: the auth secret, the shared secret and both keys (RFC 8291 3.4)
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // one record, the last: the plaintext and the delimiter 0x02
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(concat(plaintext, new byte[]{2}));
        if (encrypted.length > RECORD_SIZE) {
            throw new IllegalArgumentException("a push payload of 3 KB at most");
        }
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), encrypted);
    }

    /** an EC P-256 key pair: the VAPID keys of Oro (generated when none is configured) */
    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** the private key of PKCS#8 (base64) and its public key */
    public static KeyPair keyPair(String pkcs8Base64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            ECPrivateKey key = (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(pkcs8Base64.replaceAll("\\s", ""))));
            if (!key.getParams().getCurve().equals(P256.getCurve())) {
                throw new IllegalStateException("oro.push.vapid-private-key: a P-256 key");
            }
            ECPoint point = multiply(key.getS());
            return new KeyPair(factory.generatePublic(new ECPublicKeySpec(point, P256)), key);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("oro.push.vapid-private-key: not a PKCS#8 EC P-256 key in base64", e);
        }
    }

    /** the public key as the browsers take it (applicationServerKey, the k of VAPID): 65 bytes, uncompressed */
    public static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    /** a 65-byte uncompressed P-256 point (a subscription's p256dh) as a key; refused when not on the curve */
    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new InvalidKeyException("not an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    /** an ES256 signature in the form of JWS (r and s, 32 bytes each) */
    public static byte[] es256(PrivateKey key, byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(key);
        signature.update(data);
        return signature.sign();
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] fixed(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /** d·G on P-256: the public point of a private key (the JDK has no public API for it) */
    private static ECPoint multiply(BigInteger d) {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = P256.getGenerator();
        for (int i = 0; i < d.bitLength(); i++) {
            if (d.testBit(i)) {
                result = add(result, addend);
            }
            addend = add(addend, addend);
        }
        return result;
    }

    private static ECPoint add(ECPoint a, ECPoint b) {
        if (a.equals(ECPoint.POINT_INFINITY)) {
            return b;
        }
        if (b.equals(ECPoint.POINT_INFINITY)) {
            return a;
        }
        BigInteger p = ((ECFieldFp) P256.getCurve().getField()).getP();
        BigInteger lambda;
        if (a.equals(b)) {
            BigInteger aCurve = P256.getCurve().getA();
            lambda = a.getAffineX().pow(2).multiply(BigInteger.valueOf(3)).add(aCurve)
                    .multiply(a.getAffineY().shiftLeft(1).modInverse(p)).mod(p);
        } else {
            if (a.getAffineX().equals(b.getAffineX())) {
                return ECPoint.POINT_INFINITY;
            }
            lambda = b.getAffineY().subtract(a.getAffineY()).multiply(b.getAffineX().subtract(a.getAffineX()).modInverse(p)).mod(p);
        }
        BigInteger x = lambda.pow(2).subtract(a.getAffineX()).subtract(b.getAffineX()).mod(p);
        BigInteger y = lambda.multiply(a.getAffineX().subtract(x)).subtract(a.getAffineY()).mod(p);
        return new ECPoint(x, y);
    }
}
