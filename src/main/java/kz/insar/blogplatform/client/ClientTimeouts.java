package kz.insar.blogplatform.client;

import java.time.Duration;

/**
 * All client timeouts in one place.
 */
public final class ClientTimeouts {
    public static final Duration READ = Duration.ofMillis(100);
    public static final Duration WRITE = Duration.ofMillis(300);
    public static final Duration CONNECT = Duration.ofMillis(300);

    private ClientTimeouts() {
    }
}
