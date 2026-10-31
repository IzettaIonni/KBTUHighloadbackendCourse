package kz.insar.blogplatform.converters;

import kz.insar.blogplatform.api.PostDTO;
import kz.insar.blogplatform.domain.*;
import kz.insar.blogplatform.domain.exceptions.InvalidDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class)
public class ApiConverterTest {

    private ApiConverter service;

    @BeforeEach
    void setUp() {
        service = new ApiConverter();
    }

    static Instant T(String time) {
        return Instant.parse(time);
    }

    @Test
    void testToApiPost() {
        //given
        var post = Post.builder()
                .id(PostId.of(10L))
                .author(UserId.of("u1"))
                .text("hello")
                .createdAt(T("2026-10-30T20:30:20Z"))
                .likeCount(3)
                .commentCount(5)
                .build();
        //when
        var actual = service.toApi(post);
        //then
        var expected = PostDTO.builder()
                .id(10L)
                .author("u1")
                .text("hello")
                .createdAt(T("2026-10-30T20:30:20Z"))
                .likeCount(3)
                .commentCount(5)
                .build();
        assertEquals(expected, actual);
    }

    @Test
    void testToApiCursor_ShouldBeDecodedBack() {
        //given
        var cursor = PostCursor.builder()
                .createdAt(T("2026-10-30T20:30:20.123Z"))
                .id(PostId.of(42L))
                .build();
        //when
        var actual = service.toDomainCursor(service.toApiCursor(cursor));
        //then
        assertEquals(cursor, actual);
    }

    @Test
    void testToApiCursor_ShouldReturnNullIfNoCursor() {
        assertNull(service.toApiCursor(null));
    }

    @Test
    void testToDomainCursor_ShouldReturnNullIfEmpty() {
        assertNull(service.toDomainCursor(null));
        assertNull(service.toDomainCursor(""));
    }

    @Test
    void testToDomainCursor_ShouldThrowIfNotBase64() {
        assertThrows(InvalidDataException.class, () -> service.toDomainCursor("not base64 !!"));
    }

    @Test
    void testToDomainCursor_ShouldThrowIfMalformed() {
        var cursor = Base64.getUrlEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidDataException.class, () -> service.toDomainCursor(cursor));
    }

    @Test
    void testToDomainPageParams_ShouldUseDefaultLimit() {
        var actual = service.toDomainPageParams(null, null);
        var expected = PostPageParams.builder()
                .cursor(null)
                .limit(PostPageParams.DEFAULT_LIMIT)
                .build();
        assertEquals(expected, actual);
    }

    @Test
    void testToDomainUser_ShouldThrowIfMissing() {
        assertThrows(InvalidDataException.class, () -> service.toDomainUser(null));
        assertThrows(InvalidDataException.class, () -> service.toDomainUser(" "));
    }
}
