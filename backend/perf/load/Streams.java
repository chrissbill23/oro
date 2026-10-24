import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open streams for the load test: one per person (the first N tokens), each read on a virtual thread, opened again
 * when it ends (as Umoja's client does, after a second). Prints every 10 s: the streams open, the events by type, the
 * failures. Usage: java Streams.java <base url> <tokens.json> <orgs.json> <count> <seconds>
 */
public class Streams {

    public static void main(String[] args) throws Exception {
        String base = args[0];
        String tokens = Files.readString(Path.of(args[1]));
        String orgs = Files.readString(Path.of(args[2]));
        int count = Integer.parseInt(args[3]);
        long seconds = Long.parseLong(args[4]);
        Matcher entries = Pattern.compile("\\{\"user\":\"(u(\\d+)-\\d+)\",\"org\":\\d+,\"token\":\"([^\"]+)\"\\}").matcher(tokens);
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10)).build();
        AtomicInteger open = new AtomicInteger();
        AtomicLong failures = new AtomicLong();
        ConcurrentHashMap<String, AtomicLong> events = new ConcurrentHashMap<>();
        Pattern type = Pattern.compile("\"type\":\"([a-z]+)\"");
        long end = System.currentTimeMillis() + seconds * 1000;
        int started = 0;
        while (started < count && entries.find()) {
            String user = entries.group(1);
            if (user.equals("u1-1")) {
                continue;
            }
            Matcher org = Pattern.compile("\"" + entries.group(2) + "\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(orgs);
            org.find();
            String token = entries.group(3);
            String organization = org.group(1);
            started++;
            Thread.ofVirtual().start(() -> {
                while (System.currentTimeMillis() < end) {
                    try {
                        HttpResponse<java.io.InputStream> response = http.send(HttpRequest.newBuilder(URI.create(base + "/stream"))
                                .header("Authorization", "Bearer " + token).header("x-organization-id", organization)
                                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() != 200) {
                            failures.incrementAndGet();
                            response.body().close();
                            Thread.sleep(5000);
                            continue;
                        }
                        open.incrementAndGet();
                        try (BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            for (String line; (line = lines.readLine()) != null && System.currentTimeMillis() < end; ) {
                                Matcher t = type.matcher(line);
                                if (line.startsWith("data:") && t.find()) {
                                    events.computeIfAbsent(t.group(1), k -> new AtomicLong()).incrementAndGet();
                                }
                            }
                        } finally {
                            open.decrementAndGet();
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException interrupted) {
                            return;
                        }
                    }
                }
            });
            // a few hundred a second, as people arrive
            if (started % 200 == 0) {
                Thread.sleep(1000);
            }
        }
        while (System.currentTimeMillis() < end) {
            Thread.sleep(10_000);
            System.out.printf("streams open %d, failures %d, events %s%n", open.get(), failures.get(), new java.util.TreeMap<>(events));
        }
        System.exit(0);
    }
}
