package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.Instant;

/**
 * Keyset position in the timeline: the (created_at, id) of the last post of the previous page.
 */
@Data
@Builder(toBuilder = true)
public class PostCursor {
    @NonNull
    private final Instant createdAt;
    @NonNull
    private final PostId id;

    public static PostCursor of(Post post) {
        return new PostCursor(post.getCreatedAt(), post.getId());
    }

}
