# blogplatform — SIS #2 (T10 Blog Platform)

REST/JSON over HTTP: posts, comments and likes, with idempotency keys, client timeouts and retries.

> The SIS #1 text and diagrams were not found next to the brief, so this file has only the
> code structure, *How to run* and *Results*. Paste the SIS #1 sections above them.

## Code structure

```
                      REST /posts (PostController)
controllers  ──────── api DTOs ◄── ApiConverter ─────────────────────
                           │  FaultInjector (tests only)
services     ──────── PostService ── IdempotencyService ─────────────
                           │  EntityConverter
repositories ─── PostRepository  CommentRepository  LikeRepository  IdempotencyRepository
                 (in memory: ConcurrentHashMap / ConcurrentSkipListMap / AtomicLong)
```

| Package | What is inside |
|---|---|
| `api` | JSON DTOs (`PostDTO`, `ErrorDTO`, `PostPageDTO`, …) |
| `controllers` | `PostController` (REST) |
| `converters` | `ApiConverter` (domain ↔ DTO, cursor base64), `EntityConverter` (domain ↔ entity) |
| `domain`, `domain.exceptions` | `Post`, `PostId`, `PostCursor`, … ; exceptions |
| `services`, `services.impl` | `PostService`, `IdempotencyService` |
| `repositories`, `.entities`, `.impl` | interfaces + in-memory implementations |
| `client` | `RestBlogClient`, `RetryExecutor`, `ClientTimeouts`, `BlogClient*Exception` |
| `common` | `FaultInjector`, `RequestHasher`, `CurrentInstantSupplier` |
| `bench` | `Bench` (main) |

How a request goes: controller → converter builds a domain object → service (validation, idempotency,
counters) → repository.

- **Idempotency** (`IdempotencyServiceImpl`): key `user:key` → `(sha256 of request, future with response)`, TTL 24 h.
  `putIfAbsent` makes the check atomic; concurrent requests with the same key wait for the first one's future.
  Other body → `IdempotencyKeyReuseException` (422, code `key_reuse`). Failed action releases the key.
- **Pagination**: keyset by `(created_at, id)`, newest first; cursor = base64url(`createdAtMillis:id`); limit 20 by default, max 50.
- **Timeouts** (`ClientTimeouts`): read 100 ms, write 300 ms. Set per request with `HttpRequest.timeout`.
- **Retries** (`RetryExecutor`): 3 attempts, full-jitter backoff `random(0, 20·2^(n-1))` ms; retried: timeout, connection loss,
  502/503/504. Mutations only with an idempotency key; `likePost` always (idempotent itself).
- **FaultInjector**: off by default; tests set extra delay, `unavailable` (rate or next N), or "write but lose the response".
  A lost response is simulated by holding it longer than the client timeout.


Test machine:

| | |
|---|---|
| Model | MacBook Air (M1, 2020), `MacBookAir10,1` |
| CPU | Apple M1, 8 cores (4 performance + 4 efficiency) |
| RAM | 8 GB |
| OS | macOS 26.6.2 (build 25G83) |
| Java | Oracle Java SE 21.0.12.1 (arm64) |
| Spring Boot / Tomcat | 4.1.2 / 11.0.27 |
| Power | on battery (80 %) during the run |

Method: server and clients in one JVM on localhost, 1 000 seeded posts. Per scenario 2 000 warm-up requests,
then 20 000 measured requests split between the clients (1 sequential client, or 16 in parallel).
HTTP/1.1 with keep-alive. Median of 3 runs, date 2026-10-31.
Header sizes are an estimate (checked against `curl -v`: a real `GET /posts/1` exchange has ~200 bytes of headers).

| Scenario | Clients | p50, ms | p95, ms | p99, ms | max, ms | RPS | Body, B/req | Headers (est.), B/req | Total, B/req | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| getPost | 1 | 0.086 | 0.113 | 0.169 | 8.163 | 11148 | 203 | 229 | 432 | 0 |
| getPost | 16 | 0.442 | 0.767 | 0.963 | 7.056 | 32912 | 203 | 229 | 432 | 0 |
| listPosts | 1 | 0.122 | 0.173 | 0.274 | 4.186 | 7540 | 4135 | 235 | 4370 | 0 |
| listPosts | 16 | 0.582 | 1.133 | 1.916 | 8.626 | 23978 | 4135 | 235 | 4370 | 0 |
| createComment | 1 | 0.107 | 0.171 | 0.282 | 9.471 | 8604 | 187 | 344 | 531 | 0 |
| createComment | 16 | 0.546 | 0.934 | 1.233 | 13.381 | 26170 | 188 | 344 | 532 | 0 |

What the numbers say: with 16 parallel clients p99 stays under 2 ms (0.963 ms for getPost, 1.916 ms for listPosts,
1.233 ms for createComment), far below the 120 ms target, and one MacBook Air serves 24 000–33 000 RPS.
For getPost and createComment the HTTP headers are larger than the body (229 vs 203 B, 344 vs 188 B);
for listPosts (≈4 KB response) the body dominates. These are localhost numbers: on a real network
the round trip adds to every request.
