package kz.insar.blogplatform.repositories;

import kz.insar.blogplatform.domain.PostId;
import kz.insar.blogplatform.domain.UserId;

public interface LikeRepository {
    /**
     * @return true if the like is new, false if this user already liked the post
     */
    boolean save(Long postId, String userId);

    default boolean save(PostId postId, UserId userId) {
        return save(postId.getId(), userId.getId());
    }

    void deleteAll();
}
