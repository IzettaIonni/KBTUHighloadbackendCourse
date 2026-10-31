# blogplatform — SIS #2 (T10 Blog Platform)

WebSocket is the main protocol; REST/JSON over HTTP is the baseline it is compared with.
Both interfaces sit on top of the same service layer, so the only difference measured is the protocol.

> The SIS #1 text and diagrams were not found next to the brief, so this file has only the
> code structure, *How to run* and *Results*. Paste the SIS #1 sections above them.

## Code structure

```
                REST /posts (PostController)        WebSocket /ws (BlogWebSocketHandler)
controllers  ─────────────── api DTOs ◄── ApiConverter ──► api/ws DTOs ───────────────
                                   │  FaultInjector (tests only)  │
services     ─────────── PostService ── IdempotencyService ───────────────────────────
                                   │  EntityConverter             │ CommentCreatedEvent ─► WS subscribers
repositories ─── PostRepository  CommentRepository  LikeRepository  IdempotencyRepository
                 (in memory: ConcurrentHashMap / ConcurrentSkipListMap / AtomicLong)
```

| Package | What is inside | checkbinance analogue |
|---|---|---|
| `api`, `api.ws` | JSON DTOs (`PostDTO`, `ErrorDTO`, `WsRequestDTO`, …) | `api` |
| `controllers` | `PostController` (REST), `BlogWebSocketHandler` (WS) | `controllers` |
| `converters` | `ApiConverter` (domain ↔ DTO, cursor base64), `EntityConverter` (domain ↔ entity) | `converters` |
| `domain`, `domain.exceptions`, `domain.events` | `Post`, `PostId`, `PostCursor`, … ; exceptions; `CommentCreatedEvent` | `domain`, `domain.exeptions` |
| `services`, `services.impl` | `PostService`, `IdempotencyService` | `services` |
| `repositories`, `.entities`, `.impl` | interfaces + in-memory implementations | `repositories` (JPA there) |
| `client` | `RestBlogClient`, `WsBlogClient`, `RetryExecutor`, `ClientTimeouts`, `BlogClient*Exception` | `client` |
| `common` | `FaultInjector`, `RequestHasher`, `CurrentInstantSupplier` | `common` |
| `config` | `WebSocketConfig` | `config` |
| `bench` | `Bench` (main) | gatling simulations |

How a request goes: controller → converter builds a domain object → service (validation, idempotency,
counters, event) → repository. Both controllers call the same `PostService` methods.

- **Idempotency** (`IdempotencyServiceImpl`): key `user:key` → `(sha256 of request, future with response)`, TTL 24 h.
  `putIfAbsent` makes the check atomic; concurrent requests with the same key wait for the first one's future.
  Other body → `IdempotencyKeyReuseException` (REST 422, WS `key_reuse`). Failed action releases the key.
- **Pagination**: keyset by `(created_at, id)`, newest first; cursor = base64url(`createdAtMillis:id`); limit 20 by default, max 50.
- **Timeouts** (`ClientTimeouts`): read 100 ms, write 300 ms. REST uses `HttpRequest.timeout`, WS waits for the response with the same `id`.
- **Retries** (`RetryExecutor`): 3 attempts, full-jitter backoff `random(0, 20·2^(n-1))` ms; retried: timeout, connection loss
  (WS reconnects), 502/503/504, WS `unavailable`. Mutations only with an idempotency key; `likePost` always (idempotent itself).
- **FaultInjector**: off by default; tests set extra delay, `unavailable` (rate or next N), or "write but lose the response".
  On REST a lost response is simulated by holding it longer than the client timeout.
- **WS only**: `{"op":"subscribe","data":{"postId":1}}` → server pushes `{"event":"comment.created","data":{...}}`.

## How to run

Requirements: JDK 21 (`brew install openjdk@21`, then `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).
Maven is not needed: the wrapper `./mvnw` downloads Maven 3.9.16.

Unit tests (`*Test`) and integration tests (`*IT`, real server on a random port):

```bash
./mvnw verify
```

Start the server on port 8080:

```bash
./mvnw spring-boot:run
```

REST examples:

```bash
curl -i -X POST localhost:8080/posts -H 'X-User-Id: u1' -H 'Idempotency-Key: k1' -H 'Content-Type: application/json' -d '{"text":"hello"}'
```

```bash
curl -s 'localhost:8080/posts?limit=20'
```

```bash
curl -s -X PUT localhost:8080/posts/1/like -H 'X-User-Id: u2'
```

WebSocket (any client, e.g. `brew install websocat`):

```bash
websocat ws://localhost:8080/ws
```

then send `{"id":"r1","op":"getPost","data":{"id":1}}` or `{"id":"r2","op":"subscribe","data":{"postId":1}}`.

Benchmark (about 3 minutes, writes `results.md`):

```bash
./mvnw compile exec:java
```

## Results

Machine: MacBook with Apple M1 (8 cores), 8 GB RAM, macOS 26.6.2.
Java: **20.0.2 (Oracle)**: the measured run used JDK 20 (`-Djava.version=20`), because JDK 21 was not installed;
re-run `./mvnw compile exec:java` on JDK 21 to refresh the table.

Server and clients in one JVM on localhost, 2 000 warm-up + 20 000 measured requests per scenario, median of 3 runs.
REST header sizes are an estimate (checked against `curl -v`: a real `GET /posts/1` exchange has ~200 bytes of headers);
WebSocket header bytes are exact frame headers.

| Scenario | Clients | Protocol | p50, ms | p95, ms | p99, ms | max, ms | RPS | Body, B/req | Headers, B/req | Total, B/req | Errors |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| getPost | 1 | REST | 0.087 | 0.120 | 0.174 | 12.102 | 10749 | 203 | 222 (est.) | 425 | 0 |
| getPost | 1 | WebSocket | 0.052 | 0.068 | 0.092 | 5.488 | 18319 | 306 | 10 | 316 | 0 |
| getPost | 16 | REST | 0.451 | 0.797 | 1.040 | 8.201 | 31734 | 203 | 222 (est.) | 425 | 0 |
| getPost | 16 | WebSocket | 0.240 | 0.497 | 0.709 | 4.599 | 52783 | 303 | 10 | 313 | 0 |
| listPosts | 1 | REST | 0.126 | 0.169 | 0.263 | 18.408 | 7451 | 4134 | 228 (est.) | 4362 | 0 |
| listPosts | 1 | WebSocket | 0.110 | 0.136 | 0.159 | 4.242 | 8691 | 4241 | 10 | 4251 | 0 |
| listPosts | 16 | REST | 0.577 | 1.046 | 1.833 | 4.727 | 24976 | 4134 | 228 (est.) | 4362 | 0 |
| listPosts | 16 | WebSocket | 0.454 | 0.863 | 1.489 | 4.383 | 28821 | 4239 | 10 | 4249 | 0 |
| createComment | 1 | REST | 0.119 | 0.171 | 0.262 | 8.070 | 7837 | 187 | 337 (est.) | 524 | 0 |
| createComment | 1 | WebSocket | 0.057 | 0.073 | 0.098 | 9.592 | 16826 | 342 | 12 | 354 | 0 |
| createComment | 16 | REST | 0.536 | 0.936 | 1.257 | 21.452 | 25248 | 188 | 337 (est.) | 525 | 0 |
| createComment | 16 | WebSocket | 0.259 | 0.490 | 0.613 | 10.135 | 51903 | 339 | 12 | 351 | 0 |

What the numbers say: for small messages (getPost, createComment) WebSocket has 1.7–2.1× lower p50 and
1.7–2.1× more RPS, and 26–33 % fewer bytes per request, because it has no per-request HTTP headers
(the WS envelope `id/op/key/user` is in the body instead, so its body is larger). For listPosts (≈4 KB response)
the payload dominates and the gap shrinks to 10–20 %.
