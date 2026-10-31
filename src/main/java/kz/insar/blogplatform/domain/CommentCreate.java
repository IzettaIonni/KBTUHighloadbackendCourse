package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder(toBuilder = true)
public class CommentCreate {
    @NonNull
    private final PostId postId;
    @NonNull
    private final UserId author;
    private final String text;

}
