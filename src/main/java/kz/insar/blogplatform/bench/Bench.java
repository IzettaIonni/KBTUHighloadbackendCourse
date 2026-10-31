package kz.insar.blogplatform.bench;

import kz.insar.blogplatform.BlogPlatformApplication;
import kz.insar.blogplatform.client.*;
import kz.insar.blogplatform.domain.PostCreate;
import kz.insar.blogplatform.domain.UserId;
import kz.insar.blogplatform.services.PostService;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

/**
 * REST/JSON over HTTP/2 (h2c): latency, throughput and bytes per request on one server (same JVM), localhost.
 * Run: ./mvnw compile exec:java -Dexec.mainClass=kz.insar.blogplatform.bench.Bench
 */
public class Bench {

    static final int SEED_POSTS = 1_000;
    static final int WARMUP_REQUESTS = 2_000;
    static final int MEASURED_REQUESTS = 20_000;
    static final int[] CLIENTS = {1, 16};
    static final int RUNS = 3;
    static final int WIRE_REQUESTS = 2_000;
    static final Path RESULTS_FILE = Path.of("results.md");

    enum Scenario { getPost, listPosts, createComment }

    record Measurement(Scenario scenario, int clients,
                       double p50, double p95, double p99, double max, double rps,
                       double bodyBytes, double wireBytes, long errors) {
    }

    private final int port;
    private final List<Long> postIds;
    private final HttpClient httpClient = newHttpClient();

    Bench(int port, List<Long> postIds) {
        this.port = port;
        this.postIds = postIds;
    }

    public static void main(String[] args) throws Exception {
        var context = new SpringApplicationBuilder(BlogPlatformApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .run("--server.port=0", "--logging.level.kz.insar.blogplatform=WARN");
        int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);

        var postService = context.getBean(PostService.class);
        List<Long> postIds = new ArrayList<>();
        for (int i = 0; i < SEED_POSTS; i++) {
            var post = postService.createPost(PostCreate.builder()
                    .author(UserId.of("seed-user-" + (i % 50)))
                    .text("Seed post #" + i + ": a short blog post text of about one hundred characters, like a tweet.")
                    .build(), null);
            postIds.add(post.getId().getId());
        }

        var bench = new Bench(port, postIds);
        bench.checkHttp2();
        List<List<Measurement>> runs = new ArrayList<>();
        for (int run = 1; run <= RUNS; run++) {
            System.out.println("run " + run + " of " + RUNS);
            List<Measurement> measurements = new ArrayList<>();
            for (Scenario scenario : Scenario.values()) {
                double wireBytes = bench.measureWireBytes(scenario);
                for (int clients : CLIENTS) {
                    var m = bench.measure(scenario, clients, wireBytes);
                    System.out.printf("  %-13s %2d clients: p50 %.3f ms, p99 %.3f ms, %.0f rps%n",
                            scenario, clients, m.p50(), m.p99(), m.rps());
                    measurements.add(m);
                }
            }
            runs.add(measurements);
        }

        var table = renderTable(median(runs));
        System.out.println();
        System.out.println(table);
        Files.writeString(RESULTS_FILE, renderReport(table));
        System.out.println("written to " + RESULTS_FILE.toAbsolutePath());

        context.close();
        System.exit(0);
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(ClientTimeouts.CONNECT)
                .build();
    }

    /**
     * Fails fast if the server did not upgrade the connection to HTTP/2.
     */
    void checkHttp2() throws Exception {
        var response = httpClient.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/posts?limit=1"))
                .GET()
                .build(), HttpResponse.BodyHandlers.discarding());
        if (response.version() != HttpClient.Version.HTTP_2) {
            throw new IllegalStateException("server answered with " + response.version() + ", expected HTTP/2");
        }
    }

    /**
     * Bytes on the wire per request (both directions): HTTP/2 frames with HPACK headers and bodies,
     * including the connection setup spread over WIRE_REQUESTS requests. Sent through ByteCountingProxy
     * on a fresh connection, one client.
     */
    double measureWireBytes(Scenario scenario) throws Exception {
        try (var proxy = new ByteCountingProxy(port)) {
            var client = new RestBlogClient("http://127.0.0.1:" + proxy.getPort(), newHttpClient(), new RetryExecutor());
            for (int i = 0; i < WIRE_REQUESTS; i++) {
                call(client, scenario);
            }
            // let the relay threads count the last frames
            Thread.sleep(100);
            return (double) proxy.getBytes() / WIRE_REQUESTS;
        }
    }

    Measurement measure(Scenario scenario, int clients, double wireBytes) throws Exception {
        List<BlogClient> pool = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            pool.add(new RestBlogClient("http://localhost:" + port, httpClient, new RetryExecutor()));
        }
        try {
            load(pool, scenario, WARMUP_REQUESTS);
            pool.forEach(client -> client.getTraffic().reset());

            var errors = new AtomicLong();
            long started = System.nanoTime();
            long[] latencies = load(pool, scenario, MEASURED_REQUESTS, errors);
            double seconds = (System.nanoTime() - started) / 1e9;

            Arrays.sort(latencies);
            long requests = pool.stream().mapToLong(client -> client.getTraffic().getRequests()).sum();
            long body = pool.stream().mapToLong(client -> client.getTraffic().getBodyBytes()).sum();
            return new Measurement(scenario, clients,
                    millis(percentile(latencies, 0.50)), millis(percentile(latencies, 0.95)),
                    millis(percentile(latencies, 0.99)), millis(latencies[latencies.length - 1]),
                    latencies.length / seconds,
                    (double) body / requests, wireBytes, errors.get());
        } finally {
            pool.forEach(BlogClient::close);
        }
    }

    private long[] load(List<BlogClient> pool, Scenario scenario, int total) throws Exception {
        return load(pool, scenario, total, new AtomicLong());
    }

    private long[] load(List<BlogClient> pool, Scenario scenario, int total, AtomicLong errors) throws Exception {
        int perClient = total / pool.size();
        long[] latencies = new long[perClient * pool.size()];
        ExecutorService executor = Executors.newFixedThreadPool(pool.size());
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(pool.size());
        for (int c = 0; c < pool.size(); c++) {
            var client = pool.get(c);
            int offset = c * perClient;
            executor.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perClient; i++) {
                        long t0 = System.nanoTime();
                        try {
                            call(client, scenario);
                        } catch (BlogClientException e) {
                            errors.incrementAndGet();
                        }
                        latencies[offset + i] = System.nanoTime() - t0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();
        return latencies;
    }

    private void call(BlogClient client, Scenario scenario) {
        var random = ThreadLocalRandom.current();
        long postId = postIds.get(random.nextInt(postIds.size()));
        switch (scenario) {
            case getPost -> client.getPost(postId);
            case listPosts -> client.listPosts(null, 20);
            case createComment -> client.createComment("bench-user", UUID.randomUUID().toString(), postId,
                    "Benchmark comment: thanks for the post!");
        }
    }

    private static long percentile(long[] sorted, double q) {
        int index = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double millis(long nanos) {
        return nanos / 1e6;
    }

    /**
     * Each metric is the median of its values over the runs.
     */
    static List<Measurement> median(List<List<Measurement>> runs) {
        List<Measurement> result = new ArrayList<>();
        for (int i = 0; i < runs.get(0).size(); i++) {
            int index = i;
            var same = runs.stream().map(run -> run.get(index)).toList();
            var first = same.get(0);
            result.add(new Measurement(first.scenario(), first.clients(),
                    median(same, Measurement::p50), median(same, Measurement::p95),
                    median(same, Measurement::p99), median(same, Measurement::max),
                    median(same, Measurement::rps), median(same, Measurement::bodyBytes),
                    median(same, Measurement::wireBytes),
                    same.stream().mapToLong(Measurement::errors).sum()));
        }
        return result;
    }

    private static double median(List<Measurement> values, ToDoubleFunction<Measurement> metric) {
        double[] sorted = values.stream().mapToDouble(metric).sorted().toArray();
        return sorted[sorted.length / 2];
    }

    static String renderTable(List<Measurement> measurements) {
        var sb = new StringBuilder();
        sb.append("| Scenario | Clients | p50, ms | p95, ms | p99, ms | max, ms | RPS "
                + "| Body, B/req | Headers + framing, B/req | Wire total, B/req | Errors |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var m : measurements) {
            sb.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f | %.0f | %.0f | %.0f | %.0f | %d |%n",
                    m.scenario(), m.clients(), m.p50(), m.p95(), m.p99(), m.max(), m.rps(),
                    m.bodyBytes(), m.wireBytes() - m.bodyBytes(),
                    m.wireBytes(), m.errors()));
        }
        return sb.toString();
    }

    private static String renderReport(String table) {
        return "# Benchmark results\n\n"
                + "Generated by `Bench` on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ".\n\n"
                + "- Environment: " + environment() + "\n"
                + "- Server and clients in one JVM, localhost. " + SEED_POSTS + " seeded posts.\n"
                + "- Per scenario: " + WARMUP_REQUESTS + " warm-up requests, then " + MEASURED_REQUESTS
                + " measured requests split between the clients. Median of " + RUNS + " runs.\n"
                + "- REST over HTTP/2 cleartext (h2c), `java.net.http.HttpClient`: all clients share one multiplexed connection.\n"
                + "- Body: request body + response body.\n"
                + "- Wire total: real bytes over the socket in both directions (HTTP/2 frames, HPACK headers, bodies),"
                + " measured through a TCP relay on " + WIRE_REQUESTS + " sequential requests on a fresh connection;"
                + " headers + framing = wire total - body.\n\n"
                + table;
    }

    private static String environment() {
        var cpu = "unknown CPU";
        try {
            var process = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string").start();
            cpu = new String(process.getInputStream().readAllBytes()).trim();
        } catch (IOException ignored) {
            // not macOS
        }
        return cpu + ", " + Runtime.getRuntime().availableProcessors() + " cores, "
                + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + ", Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }
}
