package kz.insar.blogplatform.services;

import kz.insar.blogplatform.domain.UserId;
import org.springframework.lang.Nullable;

import java.util.function.Supplier;

public interface IdempotencyService {
    /**
     * Runs the action once per (user, key). Same key and same request hash returns the saved response,
     * same key and another hash throws IdempotencyKeyReuseException. Without a key the action just runs.
     */
    <T> T execute(UserId user, @Nullable String key, String requestHash, Supplier<T> action);

    void deleteExpired();

}
