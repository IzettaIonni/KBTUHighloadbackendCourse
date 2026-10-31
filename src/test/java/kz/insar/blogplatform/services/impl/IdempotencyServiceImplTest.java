package kz.insar.blogplatform.services.impl;

import kz.insar.blogplatform.domain.UserId;
import kz.insar.blogplatform.domain.exceptions.IdempotencyKeyReuseException;
import kz.insar.blogplatform.domain.exceptions.InvalidDataException;
import kz.insar.blogplatform.repositories.impl.InMemoryIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class)
public class IdempotencyServiceImplTest {

    static final UserId USER = UserId.of("u1");
    static final Duration TTL = Duration.ofHours(24);

    private AtomicReference<Instant> now;
    private InMemoryIdempotencyRepository repository;
    private IdempotencyServiceImpl service;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(Instant.parse("2026-10-30T20:30:20Z"));
        repository = new InMemoryIdempotencyRepository();
        service = new IdempotencyServiceImpl(repository, now::get, TTL);
    }

    @Test
    void testCtor_ShouldThrowNPEIfRepositoryMissing() {
        assertThrows(NullPointerException.class, () -> new IdempotencyServiceImpl(null, now::get, TTL));
    }

    @Test
    void testExecute_ShouldReturnSavedResponseForSameKeyAndHash() {
        //given
        var calls = new AtomicInteger();
        //when
        var first = service.execute(USER, "k1", "hash", calls::incrementAndGet);
        var second = service.execute(USER, "k1", "hash", calls::incrementAndGet);
        //then
        assertEquals(1, first);
        assertEquals(1, second);
        assertEquals(1, calls.get());
    }

    @Test
    void testExecute_ShouldThrowIfSameKeyHasOtherHash() {
        service.execute(USER, "k1", "hash", () -> "first");
        assertThrows(IdempotencyKeyReuseException.class, () -> service.execute(USER, "k1", "other", () -> "second"));
    }

    @Test
    void testExecute_ShouldRunEveryTimeWithoutKey() {
        var calls = new AtomicInteger();
        service.execute(USER, null, "hash", calls::incrementAndGet);
        service.execute(USER, null, "hash", calls::incrementAndGet);
        assertEquals(2, calls.get());
        assertEquals(0, repository.count());
    }

    @Test
    void testExecute_ShouldKeepKeysOfDifferentUsersApart() {
        var calls = new AtomicInteger();
        service.execute(USER, "k1", "hash", calls::incrementAndGet);
        service.execute(UserId.of("u2"), "k1", "hash", calls::incrementAndGet);
        assertEquals(2, calls.get());
    }

    @Test
    void testExecute_ShouldRunAgainAfterTtl() {
        var calls = new AtomicInteger();
        service.execute(USER, "k1", "hash", calls::incrementAndGet);
        now.set(now.get().plus(TTL));
        service.execute(USER, "k1", "other", calls::incrementAndGet);
        assertEquals(2, calls.get());
    }

    @Test
    void testExecute_ShouldReleaseKeyIfActionFails() {
        assertThrows(InvalidDataException.class, () -> service.execute(USER, "k1", "hash", () -> {
            throw new InvalidDataException("boom");
        }));
        var actual = service.execute(USER, "k1", "hash", () -> "ok");
        assertEquals("ok", actual);
    }

    @Test
    void testExecute_ShouldRunActionOnceFor20ConcurrentRequests() throws Exception {
        //given
        int threads = 20;
        var calls = new AtomicInteger();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        //when
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return service.execute(USER, "k1", "hash", () -> {
                    sleep(50);
                    return calls.incrementAndGet();
                });
            }));
        }
        start.countDown();
        var responses = new HashSet<Integer>();
        for (var result : results) {
            responses.add(result.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();
        //then
        assertEquals(1, calls.get());
        assertEquals(1, responses.size());
    }

    @Test
    void testDeleteExpired_ShouldRemoveOnlyExpiredKeys() {
        service.execute(USER, "k1", "hash", () -> "old");
        now.set(now.get().plus(Duration.ofHours(1)));
        service.execute(USER, "k2", "hash", () -> "new");
        now.set(now.get().plus(Duration.ofHours(23)));
        service.deleteExpired();
        assertEquals(1, repository.count());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
