package kz.insar.blogplatform.controllers;

import kz.insar.blogplatform.api.ErrorDTO;
import kz.insar.blogplatform.api.ws.WsRequestDTO;
import kz.insar.blogplatform.api.ws.WsRequestDataDTO;
import kz.insar.blogplatform.api.ws.WsResponseDTO;
import kz.insar.blogplatform.common.FaultInjector;
import kz.insar.blogplatform.converters.ApiConverter;
import kz.insar.blogplatform.domain.CommentCreate;
import kz.insar.blogplatform.domain.PostCreate;
import kz.insar.blogplatform.domain.PostId;
import kz.insar.blogplatform.domain.events.CommentCreatedEvent;
import kz.insar.blogplatform.domain.exceptions.*;
import kz.insar.blogplatform.services.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint /ws: the same operations as PostController, as JSON messages over one connection.
 * Responses carry the request id, so a client may have several requests in flight.
 */
@Component
@Slf4j
public class BlogWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MILLIS = 1000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    @Autowired
    private PostService postService;

    @Autowired
    private ApiConverter apiConverter;

    @Autowired
    private FaultInjector faultInjector;

    @Autowired
    private JsonMapper jsonMapper;

    // session id -> session that is safe to send to from several threads
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // post id -> ids of subscribed sessions
    private final Map<Long, Set<String>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("ws session opened: " + session.getId());
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("ws session closed: " + session.getId() + " with " + status);
        sessions.remove(session.getId());
        subscriptions.values().forEach(subscribers -> subscribers.remove(session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var out = sessions.getOrDefault(session.getId(), session);
        WsRequestDTO request;
        try {
            request = jsonMapper.readValue(message.getPayload(), WsRequestDTO.class);
        } catch (JacksonException e) {
            log.debug("malformed ws message, return invalid error");
            send(out, WsResponseDTO.error(null, ErrorDTO.INVALID, "malformed message"));
            return;
        }

        log.info("ws request " + request.getOp() + " with id = " + request.getId() + " and key = " + request.getKey());
        WsResponseDTO response;
        try {
            faultInjector.beforeRequest();
            var data = dispatch(session.getId(), request);
            if (faultInjector.shouldDropResponse()) {
                log.debug("injected fault: ws response " + request.getId() + " is lost");
                return;
            }
            response = WsResponseDTO.ok(request.getId(), data);
            log.info("ws request successfully done");
        } catch (ObjectNotFoundException e) {
            response = WsResponseDTO.error(request.getId(), ErrorDTO.NOT_FOUND, e.getMessage());
        } catch (InvalidDataException e) {
            response = WsResponseDTO.error(request.getId(), ErrorDTO.INVALID, e.getMessage());
        } catch (IdempotencyKeyReuseException e) {
            response = WsResponseDTO.error(request.getId(), ErrorDTO.KEY_REUSE, e.getMessage());
        } catch (ServiceUnavailableException e) {
            response = WsResponseDTO.error(request.getId(), ErrorDTO.UNAVAILABLE, e.getMessage());
        } catch (RuntimeException e) {
            log.error("ws request failed: ", e);
            response = WsResponseDTO.error(request.getId(), ErrorDTO.INTERNAL, "internal error");
        }
        send(out, response);
    }

    @Nullable
    private Object dispatch(String sessionId, WsRequestDTO request) {
        var data = request.getData() == null ? new WsRequestDataDTO() : request.getData();
        if (request.getOp() == null) {
            throw new InvalidDataException("op is missing");
        }
        return switch (request.getOp()) {
            case WsRequestDTO.CREATE_POST -> apiConverter.toApi(postService.createPost(PostCreate.builder()
                    .author(apiConverter.toDomainUser(request.getUser()))
                    .text(data.getText())
                    .build(), request.getKey()));
            case WsRequestDTO.GET_POST -> apiConverter.toApi(postService.getPost(requirePostId(data.getId())));
            case WsRequestDTO.LIST_POSTS -> apiConverter.toApi(postService.listPosts(
                    apiConverter.toDomainPageParams(data.getCursor(), data.getLimit())));
            case WsRequestDTO.CREATE_COMMENT -> apiConverter.toApi(postService.createComment(CommentCreate.builder()
                    .postId(requirePostId(data.getPostId()))
                    .author(apiConverter.toDomainUser(request.getUser()))
                    .text(data.getText())
                    .build(), request.getKey()));
            case WsRequestDTO.LIKE_POST -> apiConverter.toApi(postService.likePost(
                    requirePostId(data.getPostId()), apiConverter.toDomainUser(request.getUser())));
            case WsRequestDTO.SUBSCRIBE -> subscribe(sessionId, requirePostId(data.getPostId()));
            default -> throw new InvalidDataException("unknown op: " + request.getOp());
        };
    }

    private Map<String, Long> subscribe(String sessionId, PostId postId) {
        postService.getPost(postId);
        subscriptions.computeIfAbsent(postId.getId(), id -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("ws session " + sessionId + " subscribed on " + postId);
        return Map.of("postId", postId.getId());
    }

    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        var comment = event.getComment();
        var subscribers = subscriptions.get(comment.getPostId().getId());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        var payload = new TextMessage(jsonMapper.writeValueAsString(
                WsResponseDTO.event(WsResponseDTO.COMMENT_CREATED, apiConverter.toApi(comment))));
        for (String sessionId : subscribers) {
            var session = sessions.get(sessionId);
            if (session != null) {
                send(session, payload);
            }
        }
    }

    private static PostId requirePostId(@Nullable Long id) {
        if (id == null) {
            throw new InvalidDataException("post id is missing");
        }
        return PostId.of(id);
    }

    private void send(WebSocketSession session, WsResponseDTO response) {
        send(session, new TextMessage(jsonMapper.writeValueAsString(response)));
    }

    private void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.warn("cannot send ws message to session " + session.getId() + ": " + e.getMessage());
        }
    }
}
