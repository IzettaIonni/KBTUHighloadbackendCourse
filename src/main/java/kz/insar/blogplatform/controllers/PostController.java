package kz.insar.blogplatform.controllers;

import kz.insar.blogplatform.api.*;
import kz.insar.blogplatform.common.FaultInjector;
import kz.insar.blogplatform.converters.ApiConverter;
import kz.insar.blogplatform.domain.CommentCreate;
import kz.insar.blogplatform.domain.PostCreate;
import kz.insar.blogplatform.domain.PostId;
import kz.insar.blogplatform.domain.exceptions.*;
import kz.insar.blogplatform.services.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.function.Supplier;

/**
 * REST/JSON baseline.
 */
@RestController
@RequestMapping("posts")
@Slf4j
public class PostController {

    public static final String USER_HEADER = "X-User-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private PostService postService;

    @Autowired
    private ApiConverter apiConverter;

    @Autowired
    private FaultInjector faultInjector;

    @PostMapping
    public ResponseEntity<PostDTO> createPost(@Nullable @RequestHeader(value = USER_HEADER, required = false) String user,
                                              @Nullable @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String key,
                                              @RequestBody TextDTO body) {
        log.info("request createPost by user = " + user + " with key = " + key);
        var request = PostCreate.builder()
                .author(apiConverter.toDomainUser(user))
                .text(body.getText())
                .build();
        var response = withFaults(() -> apiConverter.toApi(postService.createPost(request, key)));
        log.info("request successfully done");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public PostDTO getPost(@PathVariable long id) {
        log.info("request getPost with id = " + id);
        var response = withFaults(() -> apiConverter.toApi(postService.getPost(PostId.of(id))));
        log.info("request successfully done");
        return response;
    }

    @GetMapping
    public PostPageDTO listPosts(@Nullable @RequestParam(required = false) String cursor,
                                 @Nullable @RequestParam(required = false) Integer limit) {
        log.info("request listPosts with cursor = " + cursor + " and limit = " + limit);
        var params = apiConverter.toDomainPageParams(cursor, limit);
        var response = withFaults(() -> apiConverter.toApi(postService.listPosts(params)));
        log.info("request successfully done");
        return response;
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentDTO> createComment(@PathVariable long id,
                                                    @Nullable @RequestHeader(value = USER_HEADER, required = false) String user,
                                                    @Nullable @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String key,
                                                    @RequestBody TextDTO body) {
        log.info("request createComment on post = " + id + " by user = " + user + " with key = " + key);
        var request = CommentCreate.builder()
                .postId(PostId.of(id))
                .author(apiConverter.toDomainUser(user))
                .text(body.getText())
                .build();
        var response = withFaults(() -> apiConverter.toApi(postService.createComment(request, key)));
        log.info("request successfully done");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/like")
    public PostDTO likePost(@PathVariable long id,
                            @Nullable @RequestHeader(value = USER_HEADER, required = false) String user) {
        log.info("request likePost on post = " + id + " by user = " + user);
        var userId = apiConverter.toDomainUser(user);
        var response = withFaults(() -> apiConverter.toApi(postService.likePost(PostId.of(id), userId)));
        log.info("request successfully done");
        return response;
    }

    private <T> T withFaults(Supplier<T> action) {
        faultInjector.beforeRequest();
        var response = action.get();
        if (faultInjector.shouldDropResponse()) {
            throw new ResponseDroppedException("injected fault: response is lost");
        }
        return response;
    }

    @ExceptionHandler({ObjectNotFoundException.class})
    public ResponseEntity<ErrorDTO> notFoundException(ObjectNotFoundException e) {
        log.debug("ObjectNotFoundException caught, return NotFound response");
        return error(HttpStatus.NOT_FOUND, ErrorDTO.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidDataException.class})
    public ResponseEntity<ErrorDTO> badRequestException(InvalidDataException e) {
        log.debug("InvalidDataException caught, return BadRequest response");
        return error(HttpStatus.BAD_REQUEST, ErrorDTO.INVALID, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorDTO> malformedRequestException(Exception e) {
        log.debug("malformed request caught, return BadRequest response");
        return error(HttpStatus.BAD_REQUEST, ErrorDTO.INVALID, "malformed request");
    }

    @ExceptionHandler({IdempotencyKeyReuseException.class})
    public ResponseEntity<ErrorDTO> keyReuseException(IdempotencyKeyReuseException e) {
        log.debug("IdempotencyKeyReuseException caught, return UnprocessableEntity response");
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ErrorDTO.KEY_REUSE, e.getMessage());
    }

    @ExceptionHandler({ServiceUnavailableException.class})
    public ResponseEntity<ErrorDTO> serviceUnavailableException(ServiceUnavailableException e) {
        log.debug("ServiceUnavailableException caught, return ServiceUnavailable response");
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorDTO.UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler({ResponseDroppedException.class})
    public ResponseEntity<ErrorDTO> responseDroppedException(ResponseDroppedException e) {
        log.debug("ResponseDroppedException caught, hold the response until the client gives up");
        faultInjector.holdLostResponse();
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorDTO.UNAVAILABLE, e.getMessage());
    }

    private static ResponseEntity<ErrorDTO> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorDTO(code, message));
    }

}
