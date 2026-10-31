package kz.insar.blogplatform.api.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import kz.insar.blogplatform.api.ErrorDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response: {"id":"r1","ok":true,"data":{...}} or {"id":"r1","ok":false,"error":{...}}.
 * Event:    {"event":"comment.created","data":{...}} (no id).
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsResponseDTO {
    public static final String COMMENT_CREATED = "comment.created";

    private String id;
    private Boolean ok;
    private String event;
    private Object data;
    private ErrorDTO error;

    public static WsResponseDTO ok(String id, Object data) {
        return WsResponseDTO.builder().id(id).ok(true).data(data).build();
    }

    public static WsResponseDTO error(String id, String code, String message) {
        return WsResponseDTO.builder().id(id).ok(false).error(new ErrorDTO(code, message)).build();
    }

    public static WsResponseDTO event(String event, Object data) {
        return WsResponseDTO.builder().event(event).data(data).build();
    }
}
