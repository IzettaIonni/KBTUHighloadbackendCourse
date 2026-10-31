package kz.insar.blogplatform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder(toBuilder = true)
public class PostCreate {
    @NonNull
    private final UserId author;
    private final String text;

}
