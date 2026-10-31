package kz.insar.blogplatform.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Same error shape for REST bodies and WebSocket "error" field.
 * Codes: invalid, not_found, key_reuse, unavailable, internal.
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDTO {
    public static final String INVALID = "invalid";
    public static final String NOT_FOUND = "not_found";
    public static final String KEY_REUSE = "key_reuse";
    public static final String UNAVAILABLE = "unavailable";
    public static final String INTERNAL = "internal";

    private String code;
    private String message;

}
