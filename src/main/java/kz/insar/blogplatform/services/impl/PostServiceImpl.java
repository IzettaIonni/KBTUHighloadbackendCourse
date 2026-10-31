package kz.insar.blogplatform.services.impl;

import kz.insar.blogplatform.common.CurrentInstantSupplier;
import kz.insar.blogplatform.common.RequestHasher;
import kz.insar.blogplatform.converters.EntityConverter;
import kz.insar.blogplatform.domain.*;
import kz.insar.blogplatform.domain.exceptions.InvalidDataException;
import kz.insar.blogplatform.domain.exceptions.ObjectNotFoundException;
import kz.insar.blogplatform.repositories.CommentRepository;
import kz.insar.blogplatform.repositories.LikeRepository;
import kz.insar.blogplatform.repositories.PostRepository;
import kz.insar.blogplatform.repositories.entities.PostEntity;
import kz.insar.blogplatform.services.IdempotencyService;
import kz.insar.blogplatform.services.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Supplier;

@Service
public class PostServiceImpl implements PostService {

    public static final int MAX_TEXT_LENGTH = 5000;

    private final PostRepository postRepository;

    private final CommentRepository commentRepository;

    private final LikeRepository likeRepository;

    private final IdempotencyService idempotencyService;

    private final EntityConverter converter;

    private final Supplier<Instant> currentTime;

    public PostServiceImpl(PostRepository postRepository, CommentRepository commentRepository,
                           LikeRepository likeRepository, IdempotencyService idempotencyService,
                           EntityConverter converter, Supplier<Instant> currentTime) {
        if (postRepository == null || commentRepository == null || likeRepository == null
                || idempotencyService == null || converter == null
                || currentTime == null) {
            throw new NullPointerException();
        }
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.idempotencyService = idempotencyService;
        this.converter = converter;
        this.currentTime = currentTime;
    }

    @Autowired
    public PostServiceImpl(PostRepository postRepository, CommentRepository commentRepository,
                           LikeRepository likeRepository, IdempotencyService idempotencyService) {
        this(postRepository, commentRepository, likeRepository, idempotencyService,
                new EntityConverter(), new CurrentInstantSupplier());
    }

    @Override
    public Post createPost(PostCreate request, @Nullable String idempotencyKey) {
        validateText(request.getText());
        var hash = RequestHasher.hash("createPost", request.getText());

        return idempotencyService.execute(request.getAuthor(), idempotencyKey, hash, () -> {
            var entity = converter.fromDomain(request, currentTime.get());
            postRepository.save(entity);
            return converter.toDomain(entity);
        });
    }

    @Override
    public Post getPost(PostId id) {
        return converter.toDomain(findPost(id));
    }

    @Override
    public PostPage listPosts(PostPageParams params) {
        if (params.getLimit() < 1) {
            throw new InvalidDataException("limit must be positive");
        }
        int limit = Math.min(params.getLimit(), PostPageParams.MAX_LIMIT);

        // one extra row tells whether a next page exists
        var entities = postRepository.findPage(params.getCursor(), limit + 1);
        var posts = entities.stream().limit(limit).map(converter::toDomain).toList();

        PostCursor nextCursor = null;
        if (entities.size() > limit) {
            nextCursor = PostCursor.of(posts.get(posts.size() - 1));
        }
        return PostPage.builder()
                .posts(posts)
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public Comment createComment(CommentCreate request, @Nullable String idempotencyKey) {
        validateText(request.getText());
        var hash = RequestHasher.hash("createComment", request.getPostId().getId(), request.getText());

        return idempotencyService.execute(request.getAuthor(), idempotencyKey, hash, () -> {
            var post = findPost(request.getPostId());
            var entity = converter.fromDomain(request, currentTime.get());
            commentRepository.save(entity);
            post.getCommentCount().incrementAndGet();
            return converter.toDomain(entity);
        });
    }

    @Override
    public Post likePost(PostId id, UserId user) {
        var post = findPost(id);
        if (likeRepository.save(id, user)) {
            post.getLikeCount().incrementAndGet();
        }
        return converter.toDomain(post);
    }

    private PostEntity findPost(PostId id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("post is not found: " + id));
    }

    private static void validateText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidDataException("text must not be blank");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new InvalidDataException("text is longer than " + MAX_TEXT_LENGTH);
        }
    }
}
