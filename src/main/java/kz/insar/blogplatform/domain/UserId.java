package kz.insar.blogplatform.domain;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * User stub: taken from the X-User-Id header (REST) or the "user" field (WebSocket).
 */
@EqualsAndHashCode
public class UserId {
    private final String id;

    public UserId(String id) {
        if (id == null) {
            throw new NullPointerException("id must not be null");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "UserId[" + id + "]";
    }

    public static UserId of(@NonNull String id) {
        return new UserId(id);
    }

}
