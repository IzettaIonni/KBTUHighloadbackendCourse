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

/**
 * REST/JSON client on java.net.http.HttpClient over HTTP/2: requests are multiplexed on one connection.
 */
public class RestBlogClient implements BlogClient {

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
                .version(HttpClient.Version.HTTP_2)
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
        // the connection belongs to the HttpClient, which may be shared between clients
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
        traffic.addBodyBytes(utf8Length(requestBody) + utf8Length(response.body()));

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
}
