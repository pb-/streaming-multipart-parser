package dev.baecher.multipart;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class HeadersTest {
    @Test
    void testEmpty() {
        assertTrue(Headers.fromBytes(new byte[] {}).getAll().isEmpty());
    }

    @Test
    void testBasic() {
        Headers headers = Headers.fromBytes("key: value\r\nKEY2: value2".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, headers.getAll().size());
        assertEquals("value", headers.getHeaderValue("KEY"));
        assertEquals("value2", headers.getHeaderValue("key2"));
        assertNull(headers.getHeaderValue("non-existent"));
    }

    @Test
    void testDuplicateHeader() {
        Headers headers = Headers.fromBytes("key: value\r\nkey: value2".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, headers.getAll().size());
        assertEquals("value", headers.getHeaderValue("key"));
    }

    @Test
    void testRobustParsing() {
        Headers headers = Headers.fromBytes("key:value\r\nkey2:value:2".getBytes(StandardCharsets.UTF_8));
        assertEquals("value", headers.getHeaderValue("key"));
        assertEquals("value:2", headers.getHeaderValue("key2"));
    }

    @Test
    void testGetName() {
        Headers headers = Headers.fromBytes(
                "Content-Disposition: form-data;name=token".getBytes(StandardCharsets.UTF_8));
        assertEquals("token", headers.getName());

        headers = Headers.fromBytes(
                "Content-Disposition: form-data;name=\"token value\"".getBytes(StandardCharsets.UTF_8));
        assertEquals("token value", headers.getName());
    }

    @Test
    void testGetFilename() {
        Headers headers = Headers.fromBytes(
                "Content-Disposition: form-data; filename=data.bin".getBytes(StandardCharsets.UTF_8));
        assertEquals("data.bin", headers.getFilename());

        headers = Headers.fromBytes(
                "Content-Disposition: form-data; filename=data.bin; filename*=utf-8''%e2%82%ac-data.bin"
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals("€-data.bin", headers.getFilename());
    }

    @Test
    void testMalformedHeader() {
        assertThrows(IllegalArgumentException.class,
                () -> Headers.fromBytes("key=value".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testMalformedContentDisposition() {
        Headers headers = Headers.fromBytes("Content-Disposition: asdf".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, headers::getFilename);

        headers = Headers.fromBytes("Content-Disposition: asdf; asdf".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, headers::getFilename);
    }
}
