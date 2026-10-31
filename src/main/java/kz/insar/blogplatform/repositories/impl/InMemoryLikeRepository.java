package kz.insar.blogplatform.repositories.impl;

import kz.insar.blogplatform.repositories.LikeRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryLikeRepository implements LikeRepository {

    private final Set<String> likes = ConcurrentHashMap.newKeySet();

    @Override
    public boolean save(Long postId, String userId) {
        return likes.add(postId + ":" + userId);
    }

    @Override
    public void deleteAll() {
        likes.clear();
    }
}
