package kz.insar.blogplatform.client;

public class BlogClientUnavailableException extends BlogClientRetryableException {
    public BlogClientUnavailableException(String message, Throwable cause) {
        super("unavailable", message, cause);
    }

    public BlogClientUnavailableException(String message) {
        this(message, null);
    }
}
