import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open streams for the load test: one per person (the first N tokens), each read on a virtual thread, opened again
 * when it ends with Umoja's delays (1, 2, 5, 10, then 30 s; back to 1 s after a hello). Prints every 10 s: the streams
 * open, the hellos, the events by type, the failures.
 *
 * Tracked clients (the first T of them) also check the delivery, as Umoja's client catches up: on their first hello
 * they read their list (what they have); on every later hello the list again and, for each conversation whose last seq
 * is past what they have, the messages after it (?after=, by pages of 100). Every message they get is written to the
 * events file, live or caught up, with the time: compared afterwards with the database (analyze.py), a committed
 * message none of them got is a lost event, one got twice a duplicate.
 *
 * A live message past the next seq of its conversation is a gap: what is missing is read first, as Umoja's chat panel
 * does (it reads after the last message it shows on every message event).
 *
 * Usage: java Streams.java <base url> <tokens.json> <orgs.json> <count> <seconds> [tracked] [events file]
 * Events file lines (epoch ms): S time user conv seq (what it had at its first hello), L time user conv seq (a live
 * message event, change created), C time user conv seq (caught up), G time user conv had seq (a gap seen live),
 * H time user n (its n-th hello), E time user (its stream ended), K time user ms (catch-up done, how long),
 * F time user what (a failure).
 */
public class Streams {

    static final long[] DELAYS = {1000, 2000, 5000, 10000, 30000};

    static BufferedWriter out;

    static synchronized void log(String line) {
        if (out == null) {
            return;
        }
        try {
            out.write(line);
            out.write('\n');
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: java Streams.java <base url> <tokens.json> <orgs.json> <count> <seconds> [tracked] [events file]");
            System.exit(2);
        }
        String base = args[0];
        String tokens = Files.readString(Path.of(args[1]));
        String orgs = Files.readString(Path.of(args[2]));
        int count = Integer.parseInt(args[3]);
        long seconds = Long.parseLong(args[4]);
        int tracked = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        if (tracked > 0) {
            if (args.length < 7) {
                System.err.println("tracked clients need an events file");
                System.exit(2);
            }
            out = Files.newBufferedWriter(Path.of(args[6]), StandardCharsets.UTF_8);
            // docker stop: the events written before the end
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (Streams.class) {
                    try {
                        if (out != null) {
                            out.close();
                            out = null;
                        }
                    } catch (IOException e) {
                        System.err.println("events file not closed: " + e);
                    }
                }
            }));
        }
        Matcher entries = Pattern.compile("\\{\"user\":\"(u(\\d+)-\\d+)\",\"org\":\\d+,\"token\":\"([^\"]+)\"\\}").matcher(tokens);
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10)).build();
        AtomicInteger open = new AtomicInteger();
        AtomicLong failures = new AtomicLong();
        AtomicLong hellos = new AtomicLong();
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
            if (!org.find()) {
                System.err.println("no organization for " + user + " in orgs.json");
                System.exit(2);
            }
            String token = entries.group(3);
            String organization = org.group(1);
            Client client = started < tracked ? new Client(http, base, user, token, organization) : null;
            started++;
            Thread.ofVirtual().start(() -> {
                int retries = 0;
                int hello = 0;
                while (System.currentTimeMillis() < end) {
                    try {
                        HttpResponse<java.io.InputStream> response = http.send(HttpRequest.newBuilder(URI.create(base + "/stream"))
                                .header("Authorization", "Bearer " + token).header("x-organization-id", organization)
                                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                        if (response.statusCode() != 200) {
                            failures.incrementAndGet();
                            if (client != null) {
                                log("F " + System.currentTimeMillis() + " " + user + " status-" + response.statusCode());
                            }
                            response.body().close();
                            Thread.sleep(DELAYS[Math.min(retries++, DELAYS.length - 1)]);
                            continue;
                        }
                        open.incrementAndGet();
                        try (BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            for (String line; (line = lines.readLine()) != null && System.currentTimeMillis() < end; ) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                Matcher t = type.matcher(line);
                                if (!t.find()) {
                                    continue;
                                }
                                events.computeIfAbsent(t.group(1), k -> new AtomicLong()).incrementAndGet();
                                if (t.group(1).equals("hello")) {
                                    retries = 0;
                                    hello++;
                                    hellos.incrementAndGet();
                                    if (client != null) {
                                        log("H " + System.currentTimeMillis() + " " + user + " " + hello);
                                        client.hello(hello == 1);
                                    }
                                } else if (client != null && t.group(1).equals("message")) {
                                    client.live(Json.parse(line.substring(5).strip()));
                                }
                            }
                        } finally {
                            open.decrementAndGet();
                            if (client != null) {
                                log("E " + System.currentTimeMillis() + " " + user);
                            }
                        }
                        Thread.sleep(DELAYS[Math.min(retries++, DELAYS.length - 1)]);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        if (client != null) {
                            log("F " + System.currentTimeMillis() + " " + user + " " + e.getClass().getSimpleName());
                        }
                        try {
                            Thread.sleep(DELAYS[Math.min(retries++, DELAYS.length - 1)]);
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
        if (started < count) {
            System.err.println("only " + started + " tokens for " + count + " streams");
            System.exit(2);
        }
        while (System.currentTimeMillis() < end) {
            Thread.sleep(10_000);
            System.out.printf("%d streams open %d, hellos %d, failures %d, events %s%n", System.currentTimeMillis(), open.get(),
                    hellos.get(), failures.get(), new TreeMap<>(events));
            synchronized (Streams.class) {
                if (out != null) {
                    out.flush();
                }
            }
        }
        synchronized (Streams.class) {
            if (out != null) {
                out.close();
                out = null;
            }
        }
        System.exit(0);
    }

    /** a tracked client: what it has of each conversation (the last seq), and its catch-up */
    static final class Client {
        private final HttpClient http;
        private final String base;
        private final String user;
        private final String token;
        private final String organization;
        private final Map<String, Long> have = new HashMap<>();

        Client(HttpClient http, String base, String user, String token, String organization) {
            this.http = http;
            this.base = base;
            this.user = user;
            this.token = token;
            this.organization = organization;
        }

        /**
         * on the reading thread of its stream: one at a time. A message past the next seq: a gap (the seqs of a
         * conversation follow each other), what is missing read first (?after=), as Umoja's chat panel reads after
         * the last message it shows on every message event
         */
        void live(Object event) throws IOException, InterruptedException {
            if (!(event instanceof Map<?, ?> e) || !"created".equals(e.get("change"))) {
                return;
            }
            String conversation = (String) e.get("conversationId");
            long seq = ((Number) e.get("seq")).longValue();
            Long had = have.get(conversation);
            if (had != null && seq > had + 1) {
                log("G " + System.currentTimeMillis() + " " + user + " " + conversation + " " + had + " " + seq);
                catchUp(conversation, had, seq - 1);
            }
            log("L " + System.currentTimeMillis() + " " + user + " " + conversation + " " + seq);
            have.merge(conversation, seq, Math::max);
        }

        /** the messages after from, up to last at least, by pages of 100 */
        private boolean catchUp(String id, long from, long last) throws IOException, InterruptedException {
            while (from < last) {
                Object page = get("/conversations/" + id + "/messages?after=" + from + "&size=100");
                if (!(page instanceof Map<?, ?> p) || !(p.get("messages") instanceof List<?> messages)) {
                    log("F " + System.currentTimeMillis() + " " + user + " after");
                    return false;
                }
                for (Object m : messages) {
                    long seq = ((Number) ((Map<?, ?>) m).get("seq")).longValue();
                    log("C " + System.currentTimeMillis() + " " + user + " " + id + " " + seq);
                    from = Math.max(from, seq);
                }
                have.merge(id, from, Math::max);
                if (messages.size() < 100) {
                    break;
                }
            }
            return true;
        }

        void hello(boolean first) throws IOException, InterruptedException {
            long started = System.currentTimeMillis();
            Object list = get("/conversations");
            if (!(list instanceof List<?> conversations)) {
                log("F " + started + " " + user + " list");
                return;
            }
            for (Object item : conversations) {
                Map<?, ?> conversation = (Map<?, ?>) item;
                String id = (String) conversation.get("id");
                long last = ((Number) conversation.get("lastMessageSeq")).longValue();
                if (first) {
                    have.put(id, last);
                    log("S " + started + " " + user + " " + id + " " + last);
                    continue;
                }
                if (!catchUp(id, have.getOrDefault(id, 0L), last)) {
                    return;
                }
            }
            log("K " + System.currentTimeMillis() + " " + user + " " + (System.currentTimeMillis() - started));
        }

        private Object get(String path) throws IOException, InterruptedException {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Authorization", "Bearer " + token).header("x-organization-id", organization)
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log("F " + System.currentTimeMillis() + " " + user + " get-" + response.statusCode());
                return null;
            }
            return Json.parse(response.body());
        }
    }

    /** a small JSON reader: objects as maps, arrays as lists, numbers as Long or Double */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.space();
            if (json.i != text.length()) {
                throw new IllegalArgumentException("trailing JSON at " + json.i);
            }
            return value;
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            space();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                space();
                if (s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                while (true) {
                    space();
                    String key = string();
                    space();
                    i++; // :
                    map.put(key, value());
                    space();
                    if (s.charAt(i++) == '}') {
                        return map;
                    }
                }
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                i++;
                space();
                if (s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                while (true) {
                    list.add(value());
                    space();
                    if (s.charAt(i++) == ']') {
                        return list;
                    }
                }
            }
            if (c == '"') {
                return string();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return true;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return false;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String number = s.substring(start, i);
            return number.matches("-?\\d+") ? (Object) Long.parseLong(number) : (Object) Double.parseDouble(number);
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++; // "
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
        }
    }
}
