package kz.insar.blogplatform.client;

import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.common.FaultInjector;
import kz.insar.blogplatform.domain.Post;
import kz.insar.blogplatform.helpers.BlogPlatformServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Idempotency, retries, timeouts and pagination through the real REST client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.Random.class)
public class BlogClientIT {

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

    private BlogClient client() {
        BlogClient client = new RestBlogClient("http://localhost:" + port);
        clients.add(client);
        return client;
    }

    private void awaitServerRequests(long expected) {
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertEquals(expected, faultInjector.getRequestCount()));
    }

    // 1. idempotency

    @Test
    void testCreatePost_ShouldReturnSavedPostForSameKey() {
        var client = client();

        var first = client.createPost("u1", "k1", "hello");
        var second = client.createPost("u1", "k1", "hello");

        assertEquals(first, second);
        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    @Test
    void testCreatePost_ShouldFailIfKeyIsReusedWithOtherBody() {
        var client = client();
        client.createPost("u1", "k1", "hello");

        var e = assertThrows(BlogClientRequestException.class, () -> client.createPost("u1", "k1", "other text"));

        assertEquals("key_reuse", e.getCode());
        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    // 2. concurrent requests with one key

    @Test
    void testCreatePost_ShouldCreateOnePostFor20ThreadsWithOneKey() throws Exception {
        //given: every thread has its own client, so the requests really race on the server
        int threads = 20;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<PostDTO>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var client = client();
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

    @Test
    void testCreatePost_ShouldNotDuplicateWhenResponseIsLost() {
        var client = client();
        faultInjector.dropNextResponses(1);

        var post = client.createPost("u1", "k1", "hello");

        assertNotNull(post.getId());
        assertEquals(1, blogPlatformServiceHelper.countPosts());
        awaitServerRequests(2);
    }

    @Test
    void testCreatePost_ShouldNotRetryWithoutKey() {
        var client = client();
        faultInjector.dropNextResponses(1);

        assertThrows(BlogClientTimeoutException.class, () -> client.createPost("u1", null, "hello"));

        // the post is written, but the client does not know it: that is why mutations need a key to retry
        assertEquals(1, blogPlatformServiceHelper.countPosts());
        awaitServerRequests(1);
    }

    @Test
    void testLikePost_ShouldBeRetriedAndCountedOnce() {
        var client = client();
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.dropNextResponses(1);

        var actual = client.likePost("u1", post.getId().getId());

        assertEquals(1, actual.getLikeCount());
        awaitServerRequests(2);
    }

    // 4. retry after unavailable; 4xx is not retried

    @Test
    void testGetPost_ShouldSucceedAfterUnavailable() {
        var client = client();
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.failNextRequests(2);

        var actual = client.getPost(post.getId().getId());

        assertEquals(post.getId().getId(), actual.getId());
        assertEquals(3, faultInjector.getRequestCount());
    }

    @Test
    void testGetPost_ShouldGiveUpAfterThreeAttempts() {
        var client = client();
        var post = blogPlatformServiceHelper.createRandomPost();
        faultInjector.failNextRequests(RetryExecutor.MAX_ATTEMPTS);

        assertThrows(BlogClientUnavailableException.class, () -> client.getPost(post.getId().getId()));

        assertEquals(RetryExecutor.MAX_ATTEMPTS, faultInjector.getRequestCount());
    }

    @Test
    void testGetPost_ShouldNotRetryNotFound() {
        var client = client();

        var e = assertThrows(BlogClientRequestException.class, () -> client.getPost(999999));

        assertEquals("not_found", e.getCode());
        assertEquals(1, faultInjector.getRequestCount());
    }

    @Test
    void testCreateComment_ShouldNotRetryValidationError() {
        var client = client();
        var post = blogPlatformServiceHelper.createRandomPost();

        var e = assertThrows(BlogClientRequestException.class,
                () -> client.createComment("u1", "c1", post.getId().getId(), " "));

        assertEquals("invalid", e.getCode());
        assertEquals(1, faultInjector.getRequestCount());
    }

    // 5. timeout on a slow server

    @Test
    void testGetPost_ShouldTimeOutOnSlowServer() {
        var client = client();
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

    @Test
    void testListPosts_ShouldTraverseAllPostsWithoutDuplicatesAndGaps() {
        //given
        var client = client();
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
