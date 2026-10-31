package kz.insar.blogplatform.repositories.impl;

import kz.insar.blogplatform.repositories.IdempotencyRepository;
import kz.insar.blogplatform.repositories.entities.IdempotencyRecordEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

    private final ConcurrentHashMap<String, IdempotencyRecordEntity> records = new ConcurrentHashMap<>();

    @Override
    public IdempotencyRecordEntity putIfAbsent(String key, IdempotencyRecordEntity entity) {
        return records.putIfAbsent(key, entity);
    }

    @Override
    public void remove(String key, IdempotencyRecordEntity entity) {
        records.remove(key, entity);
    }

    @Override
    public void deleteExpired(Instant now) {
        records.values().removeIf(entity -> entity.isExpired(now));
    }

    @Override
    public long count() {
        return records.size();
    }

    @Override
    public void deleteAll() {
        records.clear();
    }
}
