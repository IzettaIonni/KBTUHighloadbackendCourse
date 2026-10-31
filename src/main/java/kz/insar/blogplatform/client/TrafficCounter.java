package kz.insar.blogplatform.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bytes on the wire per client, for the benchmark.
 * body: request body + response body.
 * headers: HTTP headers, estimated.
 */
public class TrafficCounter {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong bodyBytes = new AtomicLong();
    private final AtomicLong headerBytes = new AtomicLong();

    public void addRequest() {
        requests.incrementAndGet();
    }

    public void addBytes(long body, long headers) {
        bodyBytes.addAndGet(body);
        headerBytes.addAndGet(headers);
    }

    public long getRequests() {
        return requests.get();
    }

    public long getBodyBytes() {
        return bodyBytes.get();
    }

    public long getHeaderBytes() {
        return headerBytes.get();
    }

    public void reset() {
        requests.set(0);
        bodyBytes.set(0);
        headerBytes.set(0);
    }
}
