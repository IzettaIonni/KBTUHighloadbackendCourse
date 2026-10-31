package kz.insar.blogplatform.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Request and response body bytes per client, for the benchmark.
 * HTTP/2 headers are HPACK-compressed inside frames, so the client cannot see their size;
 * the benchmark measures full wire bytes separately.
 */
public class TrafficCounter {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong bodyBytes = new AtomicLong();

    public void addRequest() {
        requests.incrementAndGet();
    }

    public void addBodyBytes(long body) {
        bodyBytes.addAndGet(body);
    }

    public long getRequests() {
        return requests.get();
    }

    public long getBodyBytes() {
        return bodyBytes.get();
    }

    public void reset() {
        requests.set(0);
        bodyBytes.set(0);
    }
}
