package kz.insar.blogplatform.repositories.entities;

import lombok.*;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
public class CommentEntity {
    private Long commentId;

    private Long postId;

    private String authorId;

    private String text;

    private Instant createdAt;

}
