package kz.insar.blogplatform.helpers;

import kz.insar.blogplatform.common.FaultInjector;
import kz.insar.blogplatform.domain.Post;
import kz.insar.blogplatform.helpers.post.TestPostCreator;
import kz.insar.blogplatform.repositories.CommentRepository;
import kz.insar.blogplatform.repositories.IdempotencyRepository;
import kz.insar.blogplatform.repositories.LikeRepository;
import kz.insar.blogplatform.repositories.PostRepository;
import kz.insar.blogplatform.services.PostService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BlogPlatformServiceHelper {

    @NonNull
    private final PostService postService;
    @NonNull
    private final PostRepository postRepository;
    @NonNull
    private final CommentRepository commentRepository;
    @NonNull
    private final LikeRepository likeRepository;
    @NonNull
    private final IdempotencyRepository idempotencyRepository;
    @NonNull
    private final FaultInjector faultInjector;

    public TestPostCreator buildPost() {
        return new TestPostCreator(postService);
    }

    public Post createRandomPost() {
        return buildPost().withRandomParams().create();
    }

    public List<Post> createRandomPosts(int count) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            posts.add(createRandomPost());
        }
        return posts;
    }

    public long countPosts() {
        return postRepository.count();
    }

    public long countComments() {
        return commentRepository.count();
    }

    public void cleanUp() {
        faultInjector.reset();
        idempotencyRepository.deleteAll();
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
    }
}
