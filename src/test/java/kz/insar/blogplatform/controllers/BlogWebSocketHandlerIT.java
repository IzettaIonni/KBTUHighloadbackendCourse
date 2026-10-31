package kz.insar.blogplatform.controllers;

import kz.insar.blogplatform.api.ws.WsResponseDTO;
import kz.insar.blogplatform.client.BlogClientRequestException;
import kz.insar.blogplatform.client.RestBlogClient;
import kz.insar.blogplatform.client.WsBlogClient;
import kz.insar.blogplatform.helpers.BlogPlatformServiceHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.Random.class)
public class BlogWebSocketHandlerIT {

    @LocalServerPort
    private int port;
    @Autowired
    private BlogPlatformServiceHelper blogPlatformServiceHelper;

    private WsBlogClient wsClient;
    private RestBlogClient restClient;

    @BeforeEach
    void setUp() {
        blogPlatformServiceHelper.cleanUp();
        wsClient = new WsBlogClient("ws://localhost:" + port + "/ws");
        restClient = new RestBlogClient("http://localhost:" + port);
    }

    @AfterEach
    void tearDown() {
        wsClient.close();
        restClient.close();
        blogPlatformServiceHelper.cleanUp();
    }

    @Test
    void testSubscribe_shouldPushCommentCreatedEvent() throws Exception {
        //given
        var post = blogPlatformServiceHelper.createRandomPost();
        var events = new LinkedBlockingQueue<WsResponseDTO>();
        wsClient.subscribe(post.getId().getId(), events::add);
        //when
        var comment = restClient.createComment("u2", "c1", post.getId().getId(), "nice");
        //then
        var event = events.poll(2, TimeUnit.SECONDS);
        assertNotNull(event, "comment.created event is not received");
        assertEquals(WsResponseDTO.COMMENT_CREATED, event.getEvent());
        var data = (Map<?, ?>) event.getData();
        assertEquals(comment.getId(), ((Number) data.get("id")).longValue());
        assertEquals("nice", data.get("text"));
    }

    @Test
    void testSubscribe_shouldNotPushCommentsOfOtherPosts() throws Exception {
        var subscribed = blogPlatformServiceHelper.createRandomPost();
        var other = blogPlatformServiceHelper.createRandomPost();
        var events = new LinkedBlockingQueue<WsResponseDTO>();
        wsClient.subscribe(subscribed.getId().getId(), events::add);

        restClient.createComment("u2", "c1", other.getId().getId(), "nice");

        assertNull(events.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void testSubscribe_shouldReturnNotFoundIfPostDoesNotExist() {
        var e = assertThrows(BlogClientRequestException.class, () -> wsClient.subscribe(999999, event -> { }));
        assertEquals("not_found", e.getCode());
    }

    @Test
    void testWebSocket_shouldMatchConcurrentResponsesById() throws Exception {
        //given
        var posts = blogPlatformServiceHelper.createRandomPosts(16);
        var executor = Executors.newFixedThreadPool(posts.size());
        List<Future<Long>> results = new ArrayList<>();
        //when: all requests go through one connection at the same time
        for (var post : posts) {
            results.add(executor.submit(() -> wsClient.getPost(post.getId().getId()).getId()));
        }
        //then
        for (int i = 0; i < posts.size(); i++) {
            assertEquals(posts.get(i).getId().getId(), results.get(i).get(2, TimeUnit.SECONDS));
        }
        executor.shutdown();
    }
}
