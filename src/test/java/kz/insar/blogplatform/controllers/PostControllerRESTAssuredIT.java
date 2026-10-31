package kz.insar.blogplatform.controllers;

import kz.insar.blogplatform.domain.Post;
import kz.insar.blogplatform.domain.PostPageParams;
import kz.insar.blogplatform.helpers.BlogPlatformServiceHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.mockMvc;
import static kz.insar.blogplatform.controllers.PostController.IDEMPOTENCY_KEY_HEADER;
import static kz.insar.blogplatform.controllers.PostController.USER_HEADER;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.Random.class)
public class PostControllerRESTAssuredIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BlogPlatformServiceHelper blogPlatformServiceHelper;

    @BeforeEach
    void setUp() {
        mockMvc(mvc);
        blogPlatformServiceHelper.cleanUp();
    }

    @AfterEach
    void tearDown() {
        blogPlatformServiceHelper.cleanUp();
    }

    @Test
    void testCreatePost_shouldReturnCreatedPost() {
        given()
                .header(USER_HEADER, "u1")
                .contentType("application/json")
                .body("{\"text\":\"hello\"}")

        .when()
                .post("/posts")

        .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.CREATED)
                .body("id", notNullValue())
                .body("author", equalTo("u1"))
                .body("text", equalTo("hello"))
                .body("created_at", notNullValue())
                .body("like_count", equalTo(0))
                .body("comment_count", equalTo(0));
    }

    @Test
    void testCreatePost_shouldReturnSavedPostIfKeyIsRepeated() {
        int firstId = createPost("u1", "k1", "hello")
                .then()
                .assertThat()
                .status(HttpStatus.CREATED)
                .extract().path("id");

        createPost("u1", "k1", "hello")
                .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.CREATED)
                .body("id", equalTo(firstId));

        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    @Test
    void testCreatePost_shouldReturnUnprocessableIfKeyIsReusedWithOtherBody() {
        createPost("u1", "k1", "hello");

        createPost("u1", "k1", "another text")
                .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body("code", equalTo("key_reuse"));

        assertEquals(1, blogPlatformServiceHelper.countPosts());
    }

    @Test
    void testCreatePost_shouldReturnBadRequestIfUserIsMissing() {
        given()
                .contentType("application/json")
                .body("{\"text\":\"hello\"}")

        .when()
                .post("/posts")

        .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.BAD_REQUEST)
                .body("code", equalTo("invalid"));
    }

    @Test
    void testCreatePost_shouldReturnBadRequestIfTextIsBlank() {
        createPost("u1", "k1", " ")
                .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.BAD_REQUEST)
                .body("code", equalTo("invalid"));
    }

    @Test
    void testGetPost_shouldReturnNotFoundIfPostDoesNotExist() {
        given()

        .when()
                .get("/posts/999999")

        .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.NOT_FOUND)
                .body("code", equalTo("not_found"));
    }

    @Test
    void testLikePost_shouldCountOneLikePerUser() {
        var post = blogPlatformServiceHelper.createRandomPost();

        likePost(post, "u1").then().assertThat().status(HttpStatus.OK).body("like_count", equalTo(1));
        likePost(post, "u1").then().assertThat().status(HttpStatus.OK).body("like_count", equalTo(1));
        likePost(post, "u2").then().assertThat().status(HttpStatus.OK).body("like_count", equalTo(2));
    }

    @Test
    void testCreateComment_shouldIncrementCommentCount() {
        var post = blogPlatformServiceHelper.createRandomPost();

        given()
                .header(USER_HEADER, "u2")
                .header(IDEMPOTENCY_KEY_HEADER, "c1")
                .contentType("application/json")
                .body("{\"text\":\"nice\"}")

        .when()
                .post("/posts/" + post.getId().getId() + "/comments")

        .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.CREATED)
                .body("post_id", equalTo(post.getId().getId().intValue()))
                .body("text", equalTo("nice"));

        given()
        .when()
                .get("/posts/" + post.getId().getId())
        .then()
                .assertThat()
                .status(HttpStatus.OK)
                .body("comment_count", equalTo(1));
    }

    @Test
    void testListPosts_shouldTraverseAllPostsWithoutDuplicatesAndGaps() {
        //given
        var created = blogPlatformServiceHelper.createRandomPosts(57);
        //when
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var request = given().param("limit", 20);
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            var json = request
                    .when()
                    .get("/posts")
                    .then()
                    .assertThat()
                    .status(HttpStatus.OK)
                    .extract().jsonPath();
            seen.addAll(json.getList("posts.id", Long.class));
            cursor = json.getString("next_cursor");
            pages++;
        } while (cursor != null);
        //then
        var expected = created.stream()
                .sorted(Comparator.comparing(Post::getCreatedAt).thenComparing(post -> post.getId().getId()).reversed())
                .map(post -> post.getId().getId())
                .toList();
        assertEquals(3, pages);
        assertEquals(57, new HashSet<>(seen).size());
        assertEquals(expected, seen);
    }

    @Test
    void testListPosts_shouldLimitPageSizeByMax() {
        blogPlatformServiceHelper.createRandomPosts(PostPageParams.MAX_LIMIT + 5);

        given()
                .param("limit", 1000)

        .when()
                .get("/posts")

        .then()
                .assertThat()
                .status(HttpStatus.OK)
                .body("posts", hasSize(PostPageParams.MAX_LIMIT))
                .body("next_cursor", notNullValue());
    }

    @Test
    void testListPosts_shouldReturnBadRequestIfCursorIsMalformed() {
        given()
                .param("cursor", "%%%")

        .when()
                .get("/posts")

        .then()
                .log().all()
                .assertThat()
                .status(HttpStatus.BAD_REQUEST)
                .body("code", equalTo("invalid"));
    }

    private io.restassured.module.mockmvc.response.MockMvcResponse createPost(String user, String key, String text) {
        return given()
                .header(USER_HEADER, user)
                .header(IDEMPOTENCY_KEY_HEADER, key)
                .contentType("application/json")
                .body("{\"text\":\"" + text + "\"}")
                .when()
                .post("/posts");
    }

    private io.restassured.module.mockmvc.response.MockMvcResponse likePost(Post post, String user) {
        return given()
                .header(USER_HEADER, user)
                .when()
                .put("/posts/" + post.getId().getId() + "/like");
    }
}
