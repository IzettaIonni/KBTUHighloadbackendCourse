package kz.insar.blogplatform.domain.exceptions;

public class ResponseDroppedException extends BlogPlatformException {
    public ResponseDroppedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResponseDroppedException(String message) {
        this(message, null);
    }

    public ResponseDroppedException(Throwable cause) {
        this(null, cause);
    }

    public ResponseDroppedException() {
        this(null, null);
    }
}
