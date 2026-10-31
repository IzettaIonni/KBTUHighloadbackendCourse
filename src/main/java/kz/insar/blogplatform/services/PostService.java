package kz.insar.blogplatform.services;

import kz.insar.blogplatform.domain.*;
import org.springframework.lang.Nullable;

public interface PostService {
    Post createPost(PostCreate request, @Nullable String idempotencyKey);

    Post getPost(PostId id);

    PostPage listPosts(PostPageParams params);

    Comment createComment(CommentCreate request, @Nullable String idempotencyKey);

    Post likePost(PostId id, UserId user);

}
