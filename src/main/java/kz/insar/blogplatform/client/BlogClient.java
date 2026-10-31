package kz.insar.blogplatform.client;

import kz.insar.blogplatform.api.CommentDTO;
import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.api.PostPageDTO;
import org.springframework.lang.Nullable;

/**
 * Same operations over REST (RestBlogClient) and WebSocket (WsBlogClient).
 * Reads and likePost are always retried; createPost / createComment only with an idempotency key.
 */
public interface BlogClient extends AutoCloseable {
    PostDTO createPost(String user, @Nullable String key, String text);

    PostDTO getPost(long id);

    PostPageDTO listPosts(@Nullable String cursor, int limit);

    CommentDTO createComment(String user, @Nullable String key, long postId, String text);

    PostDTO likePost(String user, long postId);

    TrafficCounter getTraffic();

    @Override
    void close();
}
