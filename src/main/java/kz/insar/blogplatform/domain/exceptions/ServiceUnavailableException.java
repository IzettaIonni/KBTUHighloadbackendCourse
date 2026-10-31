package kz.insar.blogplatform.domain.exceptions;

public class ServiceUnavailableException extends BlogPlatformException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceUnavailableException(String message) {
        this(message, null);
    }

    public ServiceUnavailableException(Throwable cause) {
        this(null, cause);
    }

    public ServiceUnavailableException() {
        this(null, null);
    }
}
