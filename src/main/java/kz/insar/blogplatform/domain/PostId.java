package kz.insar.blogplatform.domain;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode
public class PostId {
    private final Long id;

    public PostId(Long id) {
        if (id == null) {
            throw new NullPointerException("id must not be null");
        }
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "PostId[" + id + "]";
    }

    public static PostId of(@NonNull Long id) {
        return new PostId(id);
    }

}
