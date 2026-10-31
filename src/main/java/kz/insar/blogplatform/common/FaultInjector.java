package kz.insar.blogplatform.common;

import kz.insar.blogplatform.domain.exceptions.ServiceUnavailableException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side failures for tests. Everything is off by default.
 * Rates are random fractions; the "next" counters make a test deterministic.
 */
@Component
@Getter
@Setter
public class FaultInjector {

    private volatile long extraDelayMillis = 0;

    private volatile double unavailableRate = 0;

    private volatile double dropResponseRate = 0;

    /**
     * REST cannot really lose a response, so it holds the response longer than any client timeout.
     */
    private volatile long lostResponseHoldMillis = 1000;

    private final AtomicInteger unavailableNext = new AtomicInteger();

    private final AtomicInteger dropResponseNext = new AtomicInteger();

    private final AtomicLong requestCount = new AtomicLong();

    public void failNextRequests(int count) {
        unavailableNext.set(count);
    }

    public void dropNextResponses(int count) {
        dropResponseNext.set(count);
    }

    /**
     * Called before an operation: adds delay or answers "unavailable".
     */
    public void beforeRequest() {
        requestCount.incrementAndGet();
        if (extraDelayMillis > 0) {
            sleep(extraDelayMillis);
        }
        if (take(unavailableNext) || roll(unavailableRate)) {
            throw new ServiceUnavailableException("injected fault: service unavailable");
        }
    }

    /**
     * Called after an operation is written: true means "do not send the response".
     */
    public boolean shouldDropResponse() {
        return take(dropResponseNext) || roll(dropResponseRate);
    }

    public void holdLostResponse() {
        sleep(lostResponseHoldMillis);
    }

    public void reset() {
        extraDelayMillis = 0;
        unavailableRate = 0;
        dropResponseRate = 0;
        unavailableNext.set(0);
        dropResponseNext.set(0);
        requestCount.set(0);
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    private static boolean take(AtomicInteger counter) {
        return counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }

    private static boolean roll(double rate) {
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
