package kz.insar.blogplatform.client;

import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.common.FaultInjector;
import kz.insar.blogplatform.domain.Post;
import kz.insar.blogplatform.helpers.BlogPlatformServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The same scenarios for both protocols: idempotency, retries, timeouts and pagination through the real clients.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.Random.class)
public class BlogClientIT {

    static final String REST = "rest";
    static final String WS = "ws";

    @LocalServerPort
    private int port;
    @Autowired
    private BlogPlatformServiceHelper blogPlatformServiceHelper;
    @Autowired
    private FaultInjector faultInjector;

    private final List<BlogClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        blogPlatformServiceHelper.cleanUp();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(BlogClient::close);
        clients.clear();
        blogPlatformServiceHelper.cleanUp();
    }

    private BlogClient client(String protocol) {
        BlogClient client = REST.equals(protocol)
                ? new RestBlogClient("http://localhost:" + port)
                : new WsBlogClient("ws://localhost:" + port + "/ws");
        clients.add(client);
        return client;
    }

    private void awaitServerRequests(long expected) {
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertEquals(expected, faultInjector.getRequestCount()));
    }

    // 1. idempotency

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreatePost_ShouldReturnSavedPostForSameKey(String protocol) {
        var client = client(protocol);

        var first = client.createPost("u1", "k1", "hello");
        var second = client.createPost("u1", "k1", "hello");

        assertEquals(first, second);
        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreatePost_ShouldFailIfKeyIsReusedWithOtherBody(String protocol) {
        var client = client(protocol);
        client.createPost("u1", "k1", "hello");

        var e = assertThrows(BlogClientRequestException.class, () -> client.createPost("u1", "k1", "other text"));

        assertEquals("key_reuse", e.getCode());
        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    // 2. concurrent requests with one key

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreatePost_ShouldCreateOnePostFor20ThreadsWithOneKey(String protocol) throws Exception {
        //given: every thread has its own client (own connection), so the requests really race on the server
        int threads = 20;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<PostDTO>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var client = client(protocol);
            results.add(executor.submit(() -> {
                start.await();
                return client.createPost("u1", "k1", "hello");
            }));
        }
        //when
        start.countDown();
        var ids = new HashSet<Long>();
        for (var result : results) {
            ids.add(result.get(5, TimeUnit.SECONDS).getId());
        }
        executor.shutdown();
        //then
        assertEquals(1, ids.size());
        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    // 3. lost response -> retry -> no duplicate

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreatePost_ShouldNotDuplicateWhenResponseIsLost(String protocol) {
        var client = client(protocol);
        faultInjector.dropNextResponses(1);

        var post = client.createPost("u1", "k1", "hello");

        assertNotNull(post.getId());
        assertEquals(1, blogPlatformServiceHelper.countPosts());
        awaitServerRequests(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreatePost_ShouldNotRetryWithoutKey(String protocol) {
        var client = client(protocol);
        faultInjector.dropNextResponses(1);

        assertThrows(BlogClientTimeoutException.class, () -> client.createPost("u1", null, "hello"));

        // the post is written, but the client does not know it: that is why mutations need a key to retry
        assertEquals(1, blogPlatformServiceHelper.countPosts());
        awaitServerRequests(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testLikePost_ShouldBeRetriedAndCountedOnce(String protocol) {
        var client = client(protocol);
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.dropNextResponses(1);

        var actual = client.likePost("u1", post.getId().getId());

        assertEquals(1, actual.getLikeCount());
        awaitServerRequests(2);
    }

    // 4. retry after unavailable; 4xx is not retried

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testGetPost_ShouldSucceedAfterUnavailable(String protocol) {
        var client = client(protocol);
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.failNextRequests(2);

        var actual = client.getPost(post.getId().getId());

        assertEquals(post.getId().getId(), actual.getId());
        assertEquals(3, faultInjector.getRequestCount());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testGetPost_ShouldGiveUpAfterThreeAttempts(String protocol) {
        var client = client(protocol);
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.failNextRequests(RetryExecutor.MAX_ATTEMPTS);

        assertThrows(BlogClientUnavailableException.class, () -> client.getPost(post.getId().getId()));

        assertEquals(RetryExecutor.MAX_ATTEMPTS, faultInjector.getRequestCount());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testGetPost_ShouldNotRetryNotFound(String protocol) {
        var client = client(protocol);

        var e = assertThrows(BlogClientRequestException.class, () -> client.getPost(999999));

        assertEquals("not_found", e.getCode());
        assertEquals(1, faultInjector.getRequestCount());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testCreateComment_ShouldNotRetryValidationError(String protocol) {
        var client = client(protocol);
        var post = blogPlatformServiceHelper.createRandomPost();

        var e = assertThrows(BlogClientRequestException.class,
                () -> client.createComment("u1", "c1", post.getId().getId(), " "));

        assertEquals("invalid", e.getCode());
        assertEquals(1, faultInjector.getRequestCount());
    }

    // 5. timeout on a slow server

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testGetPost_ShouldTimeOutOnSlowServer(String protocol) {
        var client = client(protocol);
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.setExtraDelayMillis(ClientTimeouts.READ.toMillis() * 2);

        long started = System.nanoTime();
        assertThrows(BlogClientTimeoutException.class, () -> client.getPost(post.getId().getId()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(elapsedMillis >= ClientTimeouts.READ.toMillis() * RetryExecutor.MAX_ATTEMPTS,
                "client gave up too early: " + elapsedMillis + " ms");
        // the server still works through the queued attempts after the client gave up
        awaitServerRequests(RetryExecutor.MAX_ATTEMPTS);
    }

    // 6. pagination

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {REST, WS})
    void testListPosts_ShouldTraverseAllPostsWithoutDuplicatesAndGaps(String protocol) {
        //given
        var client = client(protocol);
        var created = blogPlatformServiceHelper.createRandomPosts(45);
        //when
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        do {
            var page = client.listPosts(cursor, 10);
            page.getPosts().forEach(post -> seen.add(post.getId()));
            cursor = page.getNextCursor();
        } while (cursor != null);
        //then
        var expected = created.stream()
                .sorted(Comparator.comparing(Post::getCreatedAt).thenComparing(post -> post.getId().getId()).reversed())
                .map(post -> post.getId().getId())
                .toList();
        assertEquals(expected, seen);
    }
}
