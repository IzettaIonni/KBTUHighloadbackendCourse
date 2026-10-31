package kz.insar.blogplatform.helpers.post;

import kz.insar.blogplatform.domain.Post;
import kz.insar.blogplatform.domain.PostCreate;
import kz.insar.blogplatform.domain.UserId;
import kz.insar.blogplatform.services.PostService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class TestPostCreator {
    @NonNull
    private final PostService postService;

    private String user;
    private String text;
    private String key;

    public static String getRandomUser() {
        return "user-" + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    public static String getRandomText() {
        return "text " + UUID.randomUUID();
    }

    public TestPostCreator withUser(String user) {
        this.user = user;
        return this;
    }

    public TestPostCreator withRandomUser() {
        return withUser(getRandomUser());
    }

    public TestPostCreator withText(String text) {
        this.text = text;
        return this;
    }

    public TestPostCreator withRandomText() {
        return withText(getRandomText());
    }

    public TestPostCreator withKey(String key) {
        this.key = key;
        return this;
    }

    public TestPostCreator withRandomParams() {
        withRandomUser();
        withRandomText();
        return this;
    }

    public Post create() {
        return postService.createPost(PostCreate.builder()
                .author(UserId.of(user))
                .text(text)
                .build(), key);
    }
}
