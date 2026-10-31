package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
public class Post {

    @NonNull
    private final PostId id;
    @NonNull
    private final UserId author;
    @NonNull
    private final String text;
    @NonNull
    private final Instant createdAt;
    private final long likeCount;
    private final long commentCount;

}
