package dev.baecher.multipart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class BufferTest {
    private InputStream inputStreamFromString(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testStartsWith() throws IOException {
        Buffer b = new Buffer(inputStreamFromString("hello world"), 100);
        b.refill();

        assertFalse(b.startsWith("HELLO".getBytes(StandardCharsets.UTF_8)));
        assertFalse(b.startsWith("ello".getBytes(StandardCharsets.UTF_8)));
        assertTrue(b.startsWith("hello".getBytes(StandardCharsets.UTF_8)));

        assertThrows(IllegalStateException.class,
                () -> b.startsWith("hello world but longer".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testFind() throws IOException {
        Buffer b = new Buffer(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}), 100);
        assertEquals(0, b.find(new byte[]{}));
        assertEquals(-1, b.find(new byte[]{1, 2}));

        b.refill();
        assertEquals(0, b.find(new byte[]{1, 2}));
        assertEquals(1, b.find(new byte[]{2, 3}));
        assertEquals(7, b.find(new byte[]{8, 9}));
        assertEquals(-1, b.find(new byte[]{9, 10}));

        b.skip(1);
        assertEquals(6, b.find(new byte[]{8, 9}));
        assertEquals(0, b.find(new byte[]{2, 3, 4, 5, 6, 7, 8, 9}));
        assertEquals(-1, b.find(new byte[]{2, 3, 4, 5, 6, 7, 8, 9, 10}));
    }

    @Test
    void testConsume() throws IOException {
        Buffer b = new Buffer(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}), 100);
        b.refill();

        byte[] dest = new byte[2];
        b.consume(dest, 0, 2);
        assertEquals(1, dest[0]);
        assertEquals(2, dest[1]);
        assertEquals(7, b.getLength());

        b.consume(dest, 0, 1);
        assertEquals(3, dest[0]);
        assertEquals(2, dest[1]);
        assertEquals(6, b.getLength());

        b.consume(dest, 1, 1);
        assertEquals(3, dest[0]);
        assertEquals(4, dest[1]);
        assertEquals(5, b.getLength());

        b.skip(5);
        assertEquals(0, b.getLength());

        assertThrows(IllegalStateException.class, () -> b.skip(1));
        assertThrows(IllegalStateException.class, () -> b.consume(dest, 0, 2));
    }

    @Test
    void testRefill() throws IOException {
        Buffer b = new Buffer(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}), 4);
        b.refill();

        byte[] dest = new byte[4];
        b.consume(dest, 0, 4);
        assertEquals(1, dest[0]);

        b.refill();
        b.consume(dest, 0, 4);
        assertEquals(5, dest[0]);

        b.refill();
        b.consume(dest, 0, 1);
        assertEquals(9, dest[0]);

        assertThrows(IllegalStateException.class, () -> b.consume(dest, 0, 1));
    }

    @Test
    void testEof() throws IOException {
        Buffer b = new Buffer(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7}), 4);

        b.refill();
        assertFalse(b.isEof());

        b.skip(4);
        b.refill();
        assertTrue(b.isEof());
    }
}
