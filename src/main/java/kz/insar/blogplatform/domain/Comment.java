package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
public class Comment {

    @NonNull
    private final CommentId id;
    @NonNull
    private final PostId postId;
    @NonNull
    private final UserId author;
    @NonNull
    private final String text;
    @NonNull
    private final Instant createdAt;

}
