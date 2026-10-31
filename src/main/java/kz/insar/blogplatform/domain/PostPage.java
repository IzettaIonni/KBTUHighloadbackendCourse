package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class PostPage {
    @NonNull
    private final List<Post> posts;
    @Nullable
    private final PostCursor nextCursor;

}
