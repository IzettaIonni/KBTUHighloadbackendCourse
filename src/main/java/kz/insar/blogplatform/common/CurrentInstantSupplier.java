package kz.insar.blogplatform.common;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * Current time truncated to millis, so the time inside a cursor matches the stored one exactly.
 */
public class CurrentInstantSupplier implements Supplier<Instant> {

    private final Clock clock;

    public CurrentInstantSupplier(Clock clock) {
        if (clock == null) {
            throw new NullPointerException();
        }
        this.clock = clock;
    }

    public CurrentInstantSupplier() {
        this(Clock.systemUTC());
    }

    @Override
    public Instant get() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
