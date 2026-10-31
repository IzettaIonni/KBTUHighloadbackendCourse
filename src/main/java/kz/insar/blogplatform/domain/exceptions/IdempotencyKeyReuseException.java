package kz.insar.blogplatform.domain.exceptions;

public class IdempotencyKeyReuseException extends BlogPlatformException {
    public IdempotencyKeyReuseException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdempotencyKeyReuseException(String message) {
        this(message, null);
    }

    public IdempotencyKeyReuseException(Throwable cause) {
        this(null, cause);
    }

    public IdempotencyKeyReuseException() {
        this(null, null);
    }
}
