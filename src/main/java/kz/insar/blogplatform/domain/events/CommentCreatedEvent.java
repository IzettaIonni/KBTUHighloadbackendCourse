package kz.insar.blogplatform.domain.events;

import kz.insar.blogplatform.domain.Comment;
import lombok.NonNull;
import lombok.Value;

@Value
public class CommentCreatedEvent {
    @NonNull
    Comment comment;
}
