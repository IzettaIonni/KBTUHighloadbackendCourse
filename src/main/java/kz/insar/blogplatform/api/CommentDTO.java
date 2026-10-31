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
public class CommentDTO {
    private Long id;
    @JsonProperty("post_id")
    private Long postId;
    private String author;
    private String text;
    @JsonProperty("created_at")
    private Instant createdAt;

}
