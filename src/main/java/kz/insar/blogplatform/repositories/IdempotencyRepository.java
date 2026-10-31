package kz.insar.blogplatform.repositories;

import kz.insar.blogplatform.repositories.entities.IdempotencyRecordEntity;

import java.time.Instant;

public interface IdempotencyRepository {
    /**
     * Atomic check-and-insert.
     *
     * @return the existing record, or null if the given record was stored
     */
    IdempotencyRecordEntity putIfAbsent(String key, IdempotencyRecordEntity entity);

    void remove(String key, IdempotencyRecordEntity entity);

    void deleteExpired(Instant now);

    long count();

    void deleteAll();
}
