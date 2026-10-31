package kz.insar.blogplatform.client;

import lombok.Getter;

/**
 * Error code is the server error code (invalid, not_found, key_reuse, ...) or a client one (timeout, connection).
 */
@Getter
public class BlogClientException extends RuntimeException {
    private final String code;

    public BlogClientException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BlogClientException(String code, String message) {
        this(code, message, null);
    }
}
