package kz.insar.blogplatform.repositories;

import kz.insar.blogplatform.domain.PostCursor;
import kz.insar.blogplatform.domain.PostId;
import kz.insar.blogplatform.repositories.entities.PostEntity;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    /**
     * Stores a new post and assigns its id.
     */
    PostEntity save(PostEntity entity);

    Optional<PostEntity> findById(Long id);

    default Optional<PostEntity> findById(PostId id) {
        return findById(id.getId());
    }

    /**
     * Newest first, ordered by (created_at, id); returns posts strictly after the cursor.
     */
    List<PostEntity> findPage(@Nullable PostCursor after, int limit);

    long count();

    void deleteAll();
}
