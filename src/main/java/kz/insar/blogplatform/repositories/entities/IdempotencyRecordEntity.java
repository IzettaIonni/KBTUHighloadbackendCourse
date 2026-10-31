package kz.insar.blogplatform.repositories.entities;

import lombok.*;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Idempotency key record: request hash plus the saved response.
 * The response is a future, so a concurrent request with the same key waits for the first one.
 */
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
public class IdempotencyRecordEntity {
    @NonNull
    private final String requestHash;

    @NonNull
    private final CompletableFuture<Object> response;

    @NonNull
    private final Instant expiresAt;

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

}
