package kz.insar.blogplatform.client;

import kz.insar.blogplatform.api.*;
import kz.insar.blogplatform.controllers.PostController;
import org.springframework.lang.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST/JSON client on java.net.http.HttpClient. HTTP/1.1 with keep-alive (the client pools connections).
 */
public class RestBlogClient implements BlogClient {

    private static final String USER_AGENT = "Java-http-client/" + System.getProperty("java.version");

    private final String baseUrl;

    private final HttpClient httpClient;

    private final RetryExecutor retryExecutor;

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final TrafficCounter traffic = new TrafficCounter();

    public RestBlogClient(String baseUrl, HttpClient httpClient, RetryExecutor retryExecutor) {
        if (baseUrl == null || httpClient == null || retryExecutor == null) {
            throw new NullPointerException();
        }
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.retryExecutor = retryExecutor;
    }

    public RestBlogClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(ClientTimeouts.CONNECT)
                .build(), new RetryExecutor());
    }

    @Override
    public PostDTO createPost(String user, @Nullable String key, String text) {
        var body = toJson(new TextDTO(text));
        var request = withKey(newRequest("/posts", ClientTimeouts.WRITE), key)
                .header(PostController.USER_HEADER, user)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return retryExecutor.execute(key != null, () -> send(request, body, PostDTO.class));
    }

    @Override
    public PostDTO getPost(long id) {
        var request = newRequest("/posts/" + id, ClientTimeouts.READ).GET().build();
        return retryExecutor.execute(true, () -> send(request, "", PostDTO.class));
    }

    @Override
    public PostPageDTO listPosts(@Nullable String cursor, int limit) {
        var path = "/posts?limit=" + limit;
        if (cursor != null) {
            path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        var request = newRequest(path, ClientTimeouts.READ).GET().build();
        return retryExecutor.execute(true, () -> send(request, "", PostPageDTO.class));
    }

    @Override
    public CommentDTO createComment(String user, @Nullable String key, long postId, String text) {
        var body = toJson(new TextDTO(text));
        var request = withKey(newRequest("/posts/" + postId + "/comments", ClientTimeouts.WRITE), key)
                .header(PostController.USER_HEADER, user)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return retryExecutor.execute(key != null, () -> send(request, body, CommentDTO.class));
    }

    @Override
    public PostDTO likePost(String user, long postId) {
        var request = newRequest("/posts/" + postId + "/like", ClientTimeouts.WRITE)
                .header(PostController.USER_HEADER, user)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return retryExecutor.execute(true, () -> send(request, "", PostDTO.class));
    }

    @Override
    public TrafficCounter getTraffic() {
        return traffic;
    }

    @Override
    public void close() {
        // keep-alive connections belong to the HttpClient, which may be shared between clients
    }

    private HttpRequest.Builder newRequest(String path, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json");
    }

    private static HttpRequest.Builder withKey(HttpRequest.Builder builder, @Nullable String key) {
        if (key != null) {
            builder.header(PostController.IDEMPOTENCY_KEY_HEADER, key);
        }
        return builder;
    }

    private <T> T send(HttpRequest request, String requestBody, Class<T> type) {
        traffic.addRequest();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new BlogClientTimeoutException(request.method() + " " + request.uri() + " timed out", e);
        } catch (IOException e) {
            throw new BlogClientConnectionException(request.method() + " " + request.uri() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlogClientConnectionException("interrupted", e);
        }
        traffic.addBytes(utf8Length(requestBody) + utf8Length(response.body()),
                requestHeadersSize(request, requestBody) + responseHeadersSize(response));

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return fromJson(response.body(), type);
        }
        var error = parseError(response.body());
        if (status == 502 || status == 503 || status == 504) {
            throw new BlogClientUnavailableException("HTTP " + status + ": " + error.getMessage());
        }
        if (status >= 400 && status < 500) {
            throw new BlogClientRequestException(error.getCode(), "HTTP " + status + ": " + error.getMessage());
        }
        throw new BlogClientException(error.getCode(), "HTTP " + status + ": " + error.getMessage());
    }

    private ErrorDTO parseError(String body) {
        try {
            var error = jsonMapper.readValue(body, ErrorDTO.class);
            if (error.getCode() != null) {
                return error;
            }
        } catch (JacksonException ignored) {
            // not our error body (e.g. a proxy page)
        }
        return new ErrorDTO(ErrorDTO.INTERNAL, body);
    }

    private String toJson(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private <T> T fromJson(String body, Class<T> type) {
        try {
            return jsonMapper.readValue(body, type);
        } catch (JacksonException e) {
            throw new BlogClientException(ErrorDTO.INTERNAL, "malformed response", e);
        }
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Estimate: request line + headers we set + the ones HttpClient adds itself (Host, User-Agent, Content-Length).
     */
    private static long requestHeadersSize(HttpRequest request, String body) {
        var uri = request.uri();
        var target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        long size = (request.method() + " " + target + " HTTP/1.1\r\n").length();
        size += ("Host: " + uri.getHost() + ":" + uri.getPort() + "\r\n").length();
        size += ("User-Agent: " + USER_AGENT + "\r\n").length();
        if (!request.method().equals("GET")) {
            size += ("Content-Length: " + utf8Length(body) + "\r\n").length();
        }
        size += headersSize(request.headers().map());
        return size + 2;
    }

    private static long responseHeadersSize(HttpResponse<String> response) {
        long size = ("HTTP/1.1 " + response.statusCode() + " \r\n").length();
        size += headersSize(response.headers().map());
        return size + 2;
    }

    private static long headersSize(Map<String, List<String>> headers) {
        long size = 0;
        for (var header : headers.entrySet()) {
            for (String value : header.getValue()) {
                size += header.getKey().length() + 2 + value.length() + 2;
            }
        }
        return size;
    }
}
