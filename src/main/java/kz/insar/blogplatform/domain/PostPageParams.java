package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@Builder(toBuilder = true)
public class PostPageParams {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    @Nullable
    private final PostCursor cursor;
    private final int limit;

}
