package kz.insar.blogplatform.domain.exceptions;

public class BlogPlatformException extends RuntimeException {
    public BlogPlatformException(String message, Throwable cause) {
        super(message, cause);
    }

    public BlogPlatformException(String message) {
        this(message, null);
    }

    public BlogPlatformException(Throwable cause) {
        this(null, cause);
    }

    public BlogPlatformException() {
        this(null, null);
    }
}
