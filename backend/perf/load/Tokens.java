import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;

/**
 * The tokens of the load test, signed as Umoja signs (RS256, a kid, issuer umoja, audience umoja and oro), with a key
 * of the test only: jwks.json (the public key, for oro.security.products.UMOJA.jwk-set) and tokens.json (a token per
 * generated user u<org>-<n>, valid for hours). Usage: java Tokens.java <dir> <orgs> <per org> <hours>
 */
public class Tokens {

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int orgs = Integer.parseInt(args[1]);
        int perOrg = Integer.parseInt(args[2]);
        long expires = Instant.now().plusSeconds(Long.parseLong(args[3]) * 3600).getEpochSecond();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        String kid = "load-test";
        Files.writeString(dir.resolve("jwks.json"), "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"%s\",\"n\":\"%s\",\"e\":\"%s\"}]}"
                .formatted(kid, b64(unsigned(pub.getModulus())), b64(unsigned(pub.getPublicExponent()))));
        String header = b64(("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withRSA");
        StringBuilder out = new StringBuilder("[");
        long now = Instant.now().getEpochSecond();
        for (int org = 1; org <= orgs; org++) {
            for (int n = 1; n <= perOrg; n++) {
                String user = "u" + org + "-" + n;
                String claims = b64(("{\"iss\":\"umoja\",\"sub\":\"%s\",\"aud\":[\"umoja\",\"oro\"],\"iat\":%d,\"exp\":%d}"
                        .formatted(user, now, expires)).getBytes(StandardCharsets.UTF_8));
                signer.initSign(pair.getPrivate());
                signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
                String token = header + "." + claims + "." + b64(signer.sign());
                out.append(out.length() > 1 ? "," : "").append("{\"user\":\"").append(user).append("\",\"org\":").append(org)
                        .append(",\"token\":\"").append(token).append("\"}");
            }
        }
        Files.writeString(dir.resolve("tokens.json"), out.append("]").toString());
        System.out.println("tokens: " + orgs * perOrg);
    }
}
