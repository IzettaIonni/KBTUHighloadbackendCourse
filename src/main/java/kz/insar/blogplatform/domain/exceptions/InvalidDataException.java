package kz.insar.blogplatform.domain.exceptions;

public class InvalidDataException extends BlogPlatformException {
    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDataException(String message) {
        this(message, null);
    }

    public InvalidDataException(Throwable cause) {
        this(null, cause);
    }

    public InvalidDataException() {
        this(null, null);
    }
}
