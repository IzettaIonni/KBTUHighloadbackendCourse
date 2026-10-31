package kz.insar.blogplatform.services.impl;

import kz.insar.blogplatform.common.CurrentInstantSupplier;
import kz.insar.blogplatform.domain.UserId;
import kz.insar.blogplatform.domain.exceptions.IdempotencyKeyReuseException;
import kz.insar.blogplatform.repositories.IdempotencyRepository;
import kz.insar.blogplatform.repositories.entities.IdempotencyRecordEntity;
import kz.insar.blogplatform.services.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@Service
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;

    private final Supplier<Instant> currentTime;

    private final Duration ttl;

    public IdempotencyServiceImpl(IdempotencyRepository idempotencyRepository,
                                  Supplier<Instant> currentTime, Duration ttl) {
        if (idempotencyRepository == null || currentTime == null || ttl == null) {
            throw new NullPointerException();
        }
        this.idempotencyRepository = idempotencyRepository;
        this.currentTime = currentTime;
        this.ttl = ttl;
    }

    @Autowired
    public IdempotencyServiceImpl(IdempotencyRepository idempotencyRepository,
                                  @Value("${blog.idempotency.ttl:24h}") Duration ttl) {
        this(idempotencyRepository, new CurrentInstantSupplier(), ttl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(UserId user, @Nullable String key, String requestHash, Supplier<T> action) {
        if (key == null) {
            return action.get();
        }
        var storageKey = user.getId() + ":" + key;
        while (true) {
            var now = currentTime.get();
            var record = IdempotencyRecordEntity.builder()
                    .requestHash(requestHash)
                    .response(new CompletableFuture<>())
                    .expiresAt(now.plus(ttl))
                    .build();

            var existing = idempotencyRepository.putIfAbsent(storageKey, record);
            if (existing == null) {
                return runFirst(storageKey, record, action);
            }
            if (existing.isExpired(now)) {
                idempotencyRepository.remove(storageKey, existing);
                continue;
            }
            if (!existing.getRequestHash().equals(requestHash)) {
                log.debug("idempotency key " + key + " reused with another request body");
                throw new IdempotencyKeyReuseException("idempotency key was used with another request");
            }
            log.debug("idempotency key " + key + " replayed, return saved response");
            return (T) await(existing.getResponse());
        }
    }

    private <T> T runFirst(String storageKey, IdempotencyRecordEntity record, Supplier<T> action) {
        try {
            T response = action.get();
            record.getResponse().complete(response);
            return response;
        } catch (RuntimeException e) {
            // a failed request must not block the key: forget it, so the client can retry
            idempotencyRepository.remove(storageKey, record);
            record.getResponse().completeExceptionally(e);
            throw e;
        }
    }

    private static Object await(CompletableFuture<Object> response) {
        try {
            return response.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    @Scheduled(fixedDelayString = "PT1H")
    public void deleteExpired() {
        idempotencyRepository.deleteExpired(currentTime.get());
    }
}
