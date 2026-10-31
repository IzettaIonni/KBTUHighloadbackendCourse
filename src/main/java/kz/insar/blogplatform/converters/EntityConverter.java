package kz.insar.blogplatform.converters;

import kz.insar.blogplatform.domain.*;
import kz.insar.blogplatform.repositories.entities.CommentEntity;
import kz.insar.blogplatform.repositories.entities.PostEntity;

import java.time.Instant;

public class EntityConverter {
    public Post toDomain(PostEntity entity) {
        return Post.builder()
                .id(PostId.of(entity.getPostId()))
                .author(UserId.of(entity.getAuthorId()))
                .text(entity.getText())
                .createdAt(entity.getCreatedAt())
                .likeCount(entity.getLikeCount().get())
                .commentCount(entity.getCommentCount().get())
                .build();
    }

    public PostEntity fromDomain(PostCreate request, Instant createdAt) {
        return PostEntity.builder()
                .authorId(request.getAuthor().getId())
                .text(request.getText())
                .createdAt(createdAt)
                .build();
    }

    public Comment toDomain(CommentEntity entity) {
        return Comment.builder()
                .id(CommentId.of(entity.getCommentId()))
                .postId(PostId.of(entity.getPostId()))
                .author(UserId.of(entity.getAuthorId()))
                .text(entity.getText())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public CommentEntity fromDomain(CommentCreate request, Instant createdAt) {
        return CommentEntity.builder()
                .postId(request.getPostId().getId())
                .authorId(request.getAuthor().getId())
                .text(request.getText())
                .createdAt(createdAt)
                .build();
    }
}
