package kz.insar.blogplatform.repositories;

import kz.insar.blogplatform.domain.PostId;
import kz.insar.blogplatform.repositories.entities.CommentEntity;

import java.util.List;

public interface CommentRepository {
    /**
     * Stores a new comment and assigns its id.
     */
    CommentEntity save(CommentEntity entity);

    List<CommentEntity> findAllByPostId(Long postId);

    default List<CommentEntity> findAllByPostId(PostId postId) {
        return findAllByPostId(postId.getId());
    }

    long count();

    void deleteAll();
}
