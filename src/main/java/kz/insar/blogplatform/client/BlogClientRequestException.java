package kz.insar.blogplatform.client;

/**
 * 4xx / validation errors: the same request fails again, so it is never retried.
 */
public class BlogClientRequestException extends BlogClientException {
    public BlogClientRequestException(String code, String message) {
        super(code, message);
    }
}
