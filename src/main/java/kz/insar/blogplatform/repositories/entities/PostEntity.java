package kz.insar.blogplatform.repositories.entities;

import lombok.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class PostEntity {
    private Long postId;

    private String authorId;

    private String text;

    private Instant createdAt;

    @Builder.Default
    private AtomicLong likeCount = new AtomicLong();

    @Builder.Default
    private AtomicLong commentCount = new AtomicLong();

}
