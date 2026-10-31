package kz.insar.blogplatform.repositories.impl;

import kz.insar.blogplatform.repositories.CommentRepository;
import kz.insar.blogplatform.repositories.entities.CommentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCommentRepository implements CommentRepository {

    private final AtomicLong sequence = new AtomicLong();

    private final ConcurrentHashMap<Long, CommentEntity> comments = new ConcurrentHashMap<>();

    @Override
    public CommentEntity save(CommentEntity entity) {
        entity.setCommentId(sequence.incrementAndGet());
        comments.put(entity.getCommentId(), entity);
        return entity;
    }

    @Override
    public List<CommentEntity> findAllByPostId(Long postId) {
        return comments.values().stream()
                .filter(comment -> comment.getPostId().equals(postId))
                .toList();
    }

    @Override
    public long count() {
        return comments.size();
    }

    @Override
    public void deleteAll() {
        comments.clear();
    }
}
