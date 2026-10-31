package kz.insar.blogplatform.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.Random.class)
public class PostIdTest {

    static final Long ID = 123L;
    private PostId service;

    @BeforeEach
    void setUp() {
        service = new PostId(ID);
    }

    @Test
    void testCtor1_ShouldThrowIfNullId() {
        var e = assertThrows(NullPointerException.class, () -> new PostId(null));
        assertEquals("id must not be null", e.getMessage());
    }

    @Test
    void testCtor2_SetIdEqualsRealId() {
        service = new PostId(44L);
        assertEquals(44L, service.getId(), "Id mismatched");
    }

    @Test
    void testToString() {
        //given

        //when
        var actual = service.toString();
        //then
        var expected = "PostId[" + ID + "]";
        assertEquals(expected, actual);
    }

    @Test
    void testEquals_SpecialCases() {
        assertTrue(service.equals(service));
        assertFalse(service.equals(null));
        assertFalse(service.equals(this));
    }

    @Test
    void testEqualsAndHashCode() {
        var a = new PostId(12L);
        var b = new PostId(12L);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testOf_ShouldReturnPostIdObject() {
        var actual = PostId.of(ID);
        var expected = new PostId(ID);
        assertEquals(expected, actual, ".of method creates object with wrong id");
    }
}
