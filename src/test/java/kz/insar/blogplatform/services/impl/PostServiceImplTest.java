package kz.insar.blogplatform.services.impl;

import kz.insar.blogplatform.converters.EntityConverter;
import kz.insar.blogplatform.domain.*;
import kz.insar.blogplatform.domain.exceptions.InvalidDataException;
import kz.insar.blogplatform.domain.exceptions.ObjectNotFoundException;
import kz.insar.blogplatform.repositories.CommentRepository;
import kz.insar.blogplatform.repositories.LikeRepository;
import kz.insar.blogplatform.repositories.PostRepository;
import kz.insar.blogplatform.repositories.entities.CommentEntity;
import kz.insar.blogplatform.repositories.entities.PostEntity;
import kz.insar.blogplatform.services.IdempotencyService;
import org.easymock.IMocksControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class)
public class PostServiceImplTest {

    private IMocksControl control;
    private PostRepository postRepositoryMock;
    private CommentRepository commentRepositoryMock;
    private LikeRepository likeRepositoryMock;
    private IdempotencyService idempotencyServiceMock;
    private Supplier<Instant> currentTimeMock;
    private PostServiceImpl service;

    static Instant T(String time) {
        return Instant.parse(time);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        control = createStrictControl();
        postRepositoryMock = control.createMock(PostRepository.class);
        commentRepositoryMock = control.createMock(CommentRepository.class);
        likeRepositoryMock = control.createMock(LikeRepository.class);
        idempotencyServiceMock = control.createMock(IdempotencyService.class);
        currentTimeMock = control.createMock(Supplier.class);
        service = new PostServiceImpl(postRepositoryMock, commentRepositoryMock, likeRepositoryMock,
                idempotencyServiceMock, new EntityConverter(), currentTimeMock);
    }

    private static PostEntity postEntity(long id, String createdAt, long likes) {
        var entity = PostEntity.builder()
                .postId(id)
                .authorId("u1")
                .text("post " + id)
                .createdAt(T(createdAt))
                .build();
        entity.getLikeCount().set(likes);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private <T> void expectIdempotentCall(UserId user, String key) {
        expect(idempotencyServiceMock.<T>execute(eq(user), eq(key), anyString(), anyObject()))
                .andAnswer(() -> ((Supplier<T>) getCurrentArgument(3)).get());
    }

    @Test
    void testCtor_ShouldThrowNPEIfPostRepositoryMissing() {
        assertThrows(NullPointerException.class, () -> new PostServiceImpl(null, commentRepositoryMock,
                likeRepositoryMock, idempotencyServiceMock, new EntityConverter(), currentTimeMock));
    }

    @Test
    void testCtor_ShouldThrowNPEIfIdempotencyServiceMissing() {
        assertThrows(NullPointerException.class, () -> new PostServiceImpl(postRepositoryMock, commentRepositoryMock,
                likeRepositoryMock, null, new EntityConverter(), currentTimeMock));
    }

    @Test
    void testCtor_ShouldThrowNPEIfCurrentTimeMissing() {
        assertThrows(NullPointerException.class, () -> new PostServiceImpl(postRepositoryMock, commentRepositoryMock,
                likeRepositoryMock, idempotencyServiceMock, new EntityConverter(), null));
    }

    @Test
    void testCreatePost_ShouldSavePostThroughIdempotencyService() {
        //given
        var user = UserId.of("u1");
        expectIdempotentCall(user, "k1");
        expect(currentTimeMock.get()).andReturn(T("2026-10-30T20:30:20Z"));
        expect(postRepositoryMock.save(anyObject())).andAnswer(() -> {
            PostEntity entity = getCurrentArgument(0);
            entity.setPostId(7L);
            return entity;
        });
        control.replay();
        //when
        var actual = service.createPost(PostCreate.builder().author(user).text("hello").build(), "k1");
        //then
        control.verify();
        var expected = Post.builder()
                .id(PostId.of(7L))
                .author(user)
                .text("hello")
                .createdAt(T("2026-10-30T20:30:20Z"))
                .likeCount(0)
                .commentCount(0)
                .build();
        assertEquals(expected, actual);
    }

    @Test
    void testCreatePost_ShouldThrowIfTextIsBlank() {
        control.replay();
        assertThrows(InvalidDataException.class, () -> service.createPost(
                PostCreate.builder().author(UserId.of("u1")).text("  ").build(), "k1"));
        control.verify();
    }

    @Test
    void testCreatePost_ShouldThrowIfTextIsTooLong() {
        control.replay();
        var text = "a".repeat(PostServiceImpl.MAX_TEXT_LENGTH + 1);
        assertThrows(InvalidDataException.class, () -> service.createPost(
                PostCreate.builder().author(UserId.of("u1")).text(text).build(), null));
        control.verify();
    }

    @Test
    void testGetPost_ShouldThrowIfNotFound() {
        expect(postRepositoryMock.findById(PostId.of(5L))).andReturn(Optional.empty());
        control.replay();
        assertThrows(ObjectNotFoundException.class, () -> service.getPost(PostId.of(5L)));
        control.verify();
    }

    @Test
    void testLikePost_ShouldIncrementOnFirstLike() {
        //given
        var entity = postEntity(5L, "2026-10-30T20:30:20Z", 1);
        expect(postRepositoryMock.findById(PostId.of(5L))).andReturn(Optional.of(entity));
        expect(likeRepositoryMock.save(PostId.of(5L), UserId.of("u2"))).andReturn(true);
        control.replay();
        //when
        var actual = service.likePost(PostId.of(5L), UserId.of("u2"));
        //then
        control.verify();
        assertEquals(2, actual.getLikeCount());
    }

    @Test
    void testLikePost_ShouldNotIncrementOnRepeatedLike() {
        //given
        var entity = postEntity(5L, "2026-10-30T20:30:20Z", 1);
        expect(postRepositoryMock.findById(PostId.of(5L))).andReturn(Optional.of(entity));
        expect(likeRepositoryMock.save(PostId.of(5L), UserId.of("u2"))).andReturn(false);
        control.replay();
        //when
        var actual = service.likePost(PostId.of(5L), UserId.of("u2"));
        //then
        control.verify();
        assertEquals(1, actual.getLikeCount());
    }

    @Test
    void testListPosts_ShouldReturnNextCursorIfMorePostsExist() {
        //given
        var entities = List.of(
                postEntity(3L, "2026-10-30T20:30:22Z", 0),
                postEntity(2L, "2026-10-30T20:30:21Z", 0),
                postEntity(1L, "2026-10-30T20:30:20Z", 0));
        expect(postRepositoryMock.findPage(null, 3)).andReturn(entities);
        control.replay();
        //when
        var actual = service.listPosts(PostPageParams.builder().limit(2).build());
        //then
        control.verify();
        assertEquals(List.of(PostId.of(3L), PostId.of(2L)), actual.getPosts().stream().map(Post::getId).toList());
        var expectedCursor = PostCursor.builder()
                .createdAt(T("2026-10-30T20:30:21Z"))
                .id(PostId.of(2L))
                .build();
        assertEquals(expectedCursor, actual.getNextCursor());
    }

    @Test
    void testListPosts_ShouldReturnNoCursorOnLastPage() {
        expect(postRepositoryMock.findPage(null, 3)).andReturn(List.of(postEntity(1L, "2026-10-30T20:30:20Z", 0)));
        control.replay();
        var actual = service.listPosts(PostPageParams.builder().limit(2).build());
        control.verify();
        assertEquals(1, actual.getPosts().size());
        assertNull(actual.getNextCursor());
    }

    @Test
    void testListPosts_ShouldLimitPageSizeByMax() {
        expect(postRepositoryMock.findPage(null, PostPageParams.MAX_LIMIT + 1)).andReturn(List.of());
        control.replay();
        service.listPosts(PostPageParams.builder().limit(1000).build());
        control.verify();
    }

    @Test
    void testListPosts_ShouldThrowIfLimitIsNotPositive() {
        control.replay();
        assertThrows(InvalidDataException.class, () -> service.listPosts(PostPageParams.builder().limit(0).build()));
        control.verify();
    }

    @Test
    void testCreateComment_ShouldIncrementCommentCount() {
        //given
        var user = UserId.of("u2");
        var post = postEntity(5L, "2026-10-30T20:30:20Z", 0);
        expectIdempotentCall(user, "k1");
        expect(postRepositoryMock.findById(PostId.of(5L))).andReturn(Optional.of(post));
        expect(currentTimeMock.get()).andReturn(T("2026-10-30T20:31:00Z"));
        expect(commentRepositoryMock.save(anyObject())).andAnswer(() -> {
            CommentEntity entity = getCurrentArgument(0);
            entity.setCommentId(9L);
            return entity;
        });
        control.replay();
        //when
        var actual = service.createComment(CommentCreate.builder()
                .postId(PostId.of(5L))
                .author(user)
                .text("nice")
                .build(), "k1");
        //then
        control.verify();
        assertEquals(CommentId.of(9L), actual.getId());
        assertEquals(1, post.getCommentCount().get());
    }

    @Test
    void testCreateComment_ShouldThrowIfPostNotFound() {
        var user = UserId.of("u2");
        expectIdempotentCall(user, "k1");
        expect(postRepositoryMock.findById(PostId.of(5L))).andReturn(Optional.empty());
        control.replay();
        assertThrows(ObjectNotFoundException.class, () -> service.createComment(CommentCreate.builder()
                .postId(PostId.of(5L))
                .author(user)
                .text("nice")
                .build(), "k1"));
        control.verify();
    }
}
