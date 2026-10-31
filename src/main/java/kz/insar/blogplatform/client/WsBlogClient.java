package kz.insar.blogplatform.client;

import kz.insar.blogplatform.api.CommentDTO;
import kz.insar.blogplatform.api.ErrorDTO;
import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.api.PostPageDTO;
import kz.insar.blogplatform.api.ws.WsRequestDTO;
import kz.insar.blogplatform.api.ws.WsRequestDataDTO;
import kz.insar.blogplatform.api.ws.WsResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket client on java.net.http.WebSocket. One connection; requests are matched to responses by id,
 * so several threads may call it at once. A lost connection is reopened on the next attempt.
 */
@Slf4j
public class WsBlogClient implements BlogClient {

    private final URI uri;

    private final HttpClient httpClient;

    private final RetryExecutor retryExecutor;

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final TrafficCounter traffic = new TrafficCounter();

    private final AtomicLong requestIds = new AtomicLong();

    private final Map<String, CompletableFuture<WsResponseDTO>> pending = new ConcurrentHashMap<>();

    private final Object sendLock = new Object();

    private volatile WebSocket webSocket;

    private volatile Consumer<WsResponseDTO> eventListener = event -> { };

    public WsBlogClient(String url, HttpClient httpClient, RetryExecutor retryExecutor) {
        if (url == null || httpClient == null || retryExecutor == null) {
            throw new NullPointerException();
        }
        this.uri = URI.create(url);
        this.httpClient = httpClient;
        this.retryExecutor = retryExecutor;
    }

    public WsBlogClient(String url) {
        this(url, HttpClient.newHttpClient(), new RetryExecutor());
    }

    @Override
    public PostDTO createPost(String user, @Nullable String key, String text) {
        var data = WsRequestDataDTO.builder().text(text).build();
        return retryExecutor.execute(key != null,
                () -> call(WsRequestDTO.CREATE_POST, key, user, data, ClientTimeouts.WRITE, PostDTO.class));
    }

    @Override
    public PostDTO getPost(long id) {
        var data = WsRequestDataDTO.builder().id(id).build();
        return retryExecutor.execute(true,
                () -> call(WsRequestDTO.GET_POST, null, null, data, ClientTimeouts.READ, PostDTO.class));
    }

    @Override
    public PostPageDTO listPosts(@Nullable String cursor, int limit) {
        var data = WsRequestDataDTO.builder().cursor(cursor).limit(limit).build();
        return retryExecutor.execute(true,
                () -> call(WsRequestDTO.LIST_POSTS, null, null, data, ClientTimeouts.READ, PostPageDTO.class));
    }

    @Override
    public CommentDTO createComment(String user, @Nullable String key, long postId, String text) {
        var data = WsRequestDataDTO.builder().postId(postId).text(text).build();
        return retryExecutor.execute(key != null,
                () -> call(WsRequestDTO.CREATE_COMMENT, key, user, data, ClientTimeouts.WRITE, CommentDTO.class));
    }

    @Override
    public PostDTO likePost(String user, long postId) {
        var data = WsRequestDataDTO.builder().postId(postId).build();
        return retryExecutor.execute(true,
                () -> call(WsRequestDTO.LIKE_POST, null, user, data, ClientTimeouts.WRITE, PostDTO.class));
    }

    /**
     * WebSocket only: the server pushes {"event":"comment.created"} for new comments of the post.
     * Subscription lives with the connection, so it is lost on reconnect.
     */
    public void subscribe(long postId, Consumer<WsResponseDTO> listener) {
        eventListener = listener;
        var data = WsRequestDataDTO.builder().postId(postId).build();
        retryExecutor.execute(true,
                () -> call(WsRequestDTO.SUBSCRIBE, null, null, data, ClientTimeouts.READ, Object.class));
    }

    @Override
    public TrafficCounter getTraffic() {
        return traffic;
    }

    @Override
    public void close() {
        var ws = webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(ClientTimeouts.WRITE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                ws.abort();
            }
        }
    }

    private <T> T call(String op, @Nullable String key, @Nullable String user, WsRequestDataDTO data,
                       Duration timeout, Class<T> type) {
        var ws = connect();
        var id = "r" + requestIds.incrementAndGet();
        var json = jsonMapper.writeValueAsString(WsRequestDTO.builder()
                .id(id).op(op).key(key).user(user).data(data)
                .build());

        var future = new CompletableFuture<WsResponseDTO>();
        pending.put(id, future);
        try {
            traffic.addRequest();
            send(ws, json, timeout);
            var response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(response.getOk())) {
                return jsonMapper.convertValue(response.getData(), type);
            }
            var error = response.getError() == null ? new ErrorDTO(ErrorDTO.INTERNAL, "no error") : response.getError();
            if (ErrorDTO.UNAVAILABLE.equals(error.getCode())) {
                throw new BlogClientUnavailableException(error.getMessage());
            }
            throw new BlogClientRequestException(error.getCode(), error.getMessage());
        } catch (TimeoutException e) {
            throw new BlogClientTimeoutException(op + " " + id + ": no response in " + timeout.toMillis() + " ms", e);
        } catch (ExecutionException e) {
            throw new BlogClientConnectionException(op + " " + id + ": connection lost", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlogClientConnectionException("interrupted", e);
        } finally {
            pending.remove(id);
        }
    }

    private void send(WebSocket ws, String json, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        // java.net.http.WebSocket allows only one outstanding send
        synchronized (sendLock) {
            ws.sendText(json, true).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        long length = json.getBytes(StandardCharsets.UTF_8).length;
        traffic.addBytes(length, frameHeaderSize(length, true));
    }

    private WebSocket connect() {
        var ws = webSocket;
        if (isOpen(ws)) {
            return ws;
        }
        synchronized (this) {
            if (isOpen(webSocket)) {
                return webSocket;
            }
            try {
                log.debug("connecting to " + uri);
                webSocket = httpClient.newWebSocketBuilder()
                        .connectTimeout(ClientTimeouts.CONNECT)
                        .buildAsync(uri, new Listener())
                        .get(ClientTimeouts.CONNECT.toMillis(), TimeUnit.MILLISECONDS);
                return webSocket;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BlogClientConnectionException("interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new BlogClientConnectionException("cannot connect to " + uri, e);
            }
        }
    }

    private static boolean isOpen(@Nullable WebSocket ws) {
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    private void onMessage(String text) {
        long length = text.getBytes(StandardCharsets.UTF_8).length;
        traffic.addBytes(length, frameHeaderSize(length, false));
        WsResponseDTO response;
        try {
            response = jsonMapper.readValue(text, WsResponseDTO.class);
        } catch (JacksonException e) {
            log.warn("malformed ws message: " + text);
            return;
        }
        if (response.getId() != null) {
            var future = pending.get(response.getId());
            if (future != null) {
                future.complete(response);
            }
        } else if (response.getEvent() != null) {
            eventListener.accept(response);
        }
    }

    private void failPending(Throwable cause) {
        pending.values().forEach(future -> future.completeExceptionally(cause));
    }

    /**
     * RFC 6455: 2 bytes + extended length (2 or 8) + 4 bytes mask on client frames.
     */
    static long frameHeaderSize(long payloadLength, boolean masked) {
        long size = 2;
        if (payloadLength > 65535) {
            size += 8;
        } else if (payloadLength > 125) {
            size += 2;
        }
        return masked ? size + 4 : size;
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                var text = buffer.toString();
                buffer.setLength(0);
                onMessage(text);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.debug("ws closed: " + statusCode + " " + reason);
            failPending(new BlogClientConnectionException("connection closed: " + statusCode));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.debug("ws error: " + error);
            failPending(new BlogClientConnectionException("connection error", error));
        }
    }
}
