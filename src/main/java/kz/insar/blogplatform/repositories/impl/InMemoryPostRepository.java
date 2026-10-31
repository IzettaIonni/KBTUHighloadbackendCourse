package kz.insar.blogplatform.repositories.impl;

import kz.insar.blogplatform.domain.PostCursor;
import kz.insar.blogplatform.repositories.PostRepository;
import kz.insar.blogplatform.repositories.entities.PostEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryPostRepository implements PostRepository {

    private record TimelineKey(Instant createdAt, Long postId) {
    }

    private static final Comparator<TimelineKey> NEWEST_FIRST = Comparator
            .comparing(TimelineKey::createdAt)
            .thenComparing(TimelineKey::postId)
            .reversed();

    private final AtomicLong sequence = new AtomicLong();

    private final ConcurrentHashMap<Long, PostEntity> posts = new ConcurrentHashMap<>();

    private final ConcurrentSkipListMap<TimelineKey, PostEntity> timeline = new ConcurrentSkipListMap<>(NEWEST_FIRST);

    @Override
    public PostEntity save(PostEntity entity) {
        entity.setPostId(sequence.incrementAndGet());
        posts.put(entity.getPostId(), entity);
        timeline.put(new TimelineKey(entity.getCreatedAt(), entity.getPostId()), entity);
        return entity;
    }

    @Override
    public Optional<PostEntity> findById(Long id) {
        return Optional.ofNullable(posts.get(id));
    }

    @Override
    public List<PostEntity> findPage(@Nullable PostCursor after, int limit) {
        ConcurrentNavigableMap<TimelineKey, PostEntity> view = timeline;
        if (after != null) {
            view = timeline.tailMap(new TimelineKey(after.getCreatedAt(), after.getId().getId()), false);
        }
        List<PostEntity> result = new ArrayList<>(limit);
        for (PostEntity entity : view.values()) {
            if (result.size() == limit) {
                break;
            }
            result.add(entity);
        }
        return result;
    }

    @Override
    public long count() {
        return posts.size();
    }

    @Override
    public void deleteAll() {
        timeline.clear();
        posts.clear();
    }
}
