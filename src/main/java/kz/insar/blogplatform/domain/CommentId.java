package kz.insar.blogplatform.domain;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode
public class CommentId {
    private final Long id;

    public CommentId(Long id) {
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
        return "CommentId[" + id + "]";
    }

    public static CommentId of(@NonNull Long id) {
        return new CommentId(id);
    }

}
