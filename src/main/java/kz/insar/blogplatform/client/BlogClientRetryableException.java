package kz.insar.blogplatform.client;

/**
 * Failures that RetryExecutor may retry: timeout, lost connection, server unavailable.
 */
public class BlogClientRetryableException extends BlogClientException {
    public BlogClientRetryableException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
