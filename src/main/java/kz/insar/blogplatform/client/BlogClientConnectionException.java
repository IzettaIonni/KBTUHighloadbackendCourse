package kz.insar.blogplatform.client;

public class BlogClientConnectionException extends BlogClientRetryableException {
    public BlogClientConnectionException(String message, Throwable cause) {
        super("connection", message, cause);
    }

    public BlogClientConnectionException(String message) {
        this(message, null);
    }
}
