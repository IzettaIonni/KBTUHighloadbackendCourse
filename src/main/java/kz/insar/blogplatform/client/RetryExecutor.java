package kz.insar.blogplatform.client;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * At most 3 attempts, exponential backoff with full jitter: pause is random in [0, 20 * 2^(n-1)] ms.
 * Only BlogClientRetryableException is retried.
 */
public class RetryExecutor {

    public static final int MAX_ATTEMPTS = 3;
    public static final long BASE_BACKOFF_MILLIS = 20;

    /**
     * @param retryAllowed false for a mutation without an idempotency key
     */
    public <T> T execute(boolean retryAllowed, Supplier<T> call) {
        int attempt = 1;
        while (true) {
            try {
                return call.get();
            } catch (BlogClientRetryableException e) {
                if (!retryAllowed || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(ThreadLocalRandom.current().nextLong(backoffMillis(attempt) + 1));
                attempt++;
            }
        }
    }

    public static long backoffMillis(int attempt) {
        return BASE_BACKOFF_MILLIS << (attempt - 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlogClientConnectionException("interrupted while waiting for retry", e);
        }
    }
}
