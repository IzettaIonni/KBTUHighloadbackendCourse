package kz.insar.blogplatform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class PostPageDTO {
    private List<PostDTO> posts;
    @JsonProperty("next_cursor")
    private String nextCursor;

}
