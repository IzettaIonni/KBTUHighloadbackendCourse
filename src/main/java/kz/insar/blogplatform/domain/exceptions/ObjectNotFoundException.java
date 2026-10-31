package kz.insar.blogplatform.domain.exceptions;

public class ObjectNotFoundException extends BlogPlatformException {
    public ObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectNotFoundException(String message) {
        this(message, null);
    }

    public ObjectNotFoundException(Throwable cause) {
        this(null, cause);
    }

    public ObjectNotFoundException() {
        this(null, null);
    }
}
