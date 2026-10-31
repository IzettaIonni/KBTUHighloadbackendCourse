package kz.insar.blogplatform.client;

public class BlogClientTimeoutException extends BlogClientRetryableException {
    public BlogClientTimeoutException(String message, Throwable cause) {
        super("timeout", message, cause);
    }

    public BlogClientTimeoutException(String message) {
        this(message, null);
    }
}
