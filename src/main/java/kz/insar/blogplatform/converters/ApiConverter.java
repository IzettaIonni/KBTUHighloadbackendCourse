package kz.insar.blogplatform.converters;

import kz.insar.blogplatform.api.CommentDTO;
import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.api.PostPageDTO;
import kz.insar.blogplatform.domain.*;
import kz.insar.blogplatform.domain.exceptions.InvalidDataException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class ApiConverter {

    public PostDTO toApi(Post post) {
        return PostDTO.builder()
                .id(post.getId().getId())
                .author(post.getAuthor().getId())
                .text(post.getText())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .build();
    }

    public CommentDTO toApi(Comment comment) {
        return CommentDTO.builder()
                .id(comment.getId().getId())
                .postId(comment.getPostId().getId())
                .author(comment.getAuthor().getId())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public PostPageDTO toApi(PostPage page) {
        return PostPageDTO.builder()
                .posts(page.getPosts().stream().map(this::toApi).toList())
                .nextCursor(toApiCursor(page.getNextCursor()))
                .build();
    }

    /**
     * Cursor is base64url("createdAtMillis:postId").
     */
    @Nullable
    public String toApiCursor(@Nullable PostCursor cursor) {
        if (cursor == null) {
            return null;
        }
        var raw = cursor.getCreatedAt().toEpochMilli() + ":" + cursor.getId().getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    public PostCursor toDomainCursor(@Nullable String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var parts = raw.split(":");
            if (parts.length != 2) {
                throw new InvalidDataException("malformed cursor");
            }
            return PostCursor.builder()
                    .createdAt(Instant.ofEpochMilli(Long.parseLong(parts[0])))
                    .id(PostId.of(Long.parseLong(parts[1])))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException("malformed cursor", e);
        }
    }

    public PostPageParams toDomainPageParams(@Nullable String cursor, @Nullable Integer limit) {
        return PostPageParams.builder()
                .cursor(toDomainCursor(cursor))
                .limit(limit == null ? PostPageParams.DEFAULT_LIMIT : limit)
                .build();
    }

    public UserId toDomainUser(@Nullable String user) {
        if (user == null || user.isBlank()) {
            throw new InvalidDataException("user id is missing");
        }
        return UserId.of(user);
    }
}
