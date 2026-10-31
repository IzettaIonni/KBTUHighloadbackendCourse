package kz.insar.blogplatform.api.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {"id":"r1","op":"createPost","key":"k1","user":"u1","data":{"text":"..."}}
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class WsRequestDTO {
    public static final String CREATE_POST = "createPost";
    public static final String GET_POST = "getPost";
    public static final String LIST_POSTS = "listPosts";
    public static final String CREATE_COMMENT = "createComment";
    public static final String LIKE_POST = "likePost";
    public static final String SUBSCRIBE = "subscribe";

    private String id;
    private String op;
    private String key;
    private String user;
    private WsRequestDataDTO data;

}
