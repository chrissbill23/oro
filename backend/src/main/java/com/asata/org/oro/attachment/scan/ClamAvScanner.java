package com.asata.org.oro.attachment.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * ClamAV's clamd (oro.attachments.scan: clamav), over TCP with its INSTREAM command: the file in chunks, each with its
 * length, then a zero length; clamd answers "stream: OK", "stream: &lt;signature&gt; FOUND" or "... ERROR" (a file
 * beyond its StreamMaxLength, for one). Its limits must cover oro.attachments.max-size-bytes.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "oro.attachments", name = "scan", havingValue = "clamav")
public class ClamAvScanner implements MalwareScanner {

    private static final int CHUNK = 64 * 1024;

    private final String host;
    private final int port;
    private final Duration timeout;

    public ClamAvScanner(@Value("${oro.attachments.clamav.host:localhost}") String host,
                         @Value("${oro.attachments.clamav.port:3310}") int port,
                         @Value("${oro.attachments.clamav.timeout:PT2M}") Duration timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        log.info("attachments scanned by clamd on {}:{}", host, port);
    }

    @Override
    public Verdict scan(InputStream content) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) Math.min(timeout.toMillis(), 10_000));
            socket.setSoTimeout((int) timeout.toMillis());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK];
            for (int read; (read = content.read(buffer)) != -1; ) {
                out.writeInt(read);
                out.write(buffer, 0, read);
            }
            out.writeInt(0);
            out.flush();
            String reply = readReply(socket.getInputStream());
            return verdict(reply);
        }
    }

    /** clamd's answer, up to its terminating NUL */
    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        for (int b; (b = in.read()) != -1 && b != 0; ) {
            reply.write(b);
            if (reply.size() > 4096) {
                break;
            }
        }
        return reply.toString(StandardCharsets.US_ASCII).strip();
    }

    static Verdict verdict(String reply) throws IOException {
        if (reply.endsWith(": OK")) {
            return Verdict.clean();
        }
        if (reply.endsWith(" FOUND")) {
            String signature = reply.substring(reply.indexOf(':') + 1, reply.length() - " FOUND".length()).strip();
            return new Verdict(Verdict.Result.INFECTED, signature);
        }
        if (reply.endsWith(" ERROR")) {
            return new Verdict(Verdict.Result.FAILED, null);
        }
        // anything else: clamd did not answer as it does; the file stays pending, asked again later
        throw new IOException("unexpected answer of clamd");
    }
}
