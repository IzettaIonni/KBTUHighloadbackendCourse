package kz.insar.blogplatform.api.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Union of all operation arguments; each operation reads only its own fields.
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsRequestDataDTO {
    private Long id;
    private Long postId;
    private String text;
    private String cursor;
    private Integer limit;

}
