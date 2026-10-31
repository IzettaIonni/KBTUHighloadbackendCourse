package kz.insar.blogplatform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class PostDTO {
    private Long id;
    private String author;
    private String text;
    @JsonProperty("created_at")
    private Instant createdAt;
    @JsonProperty("like_count")
    private long likeCount;
    @JsonProperty("comment_count")
    private long commentCount;

}
