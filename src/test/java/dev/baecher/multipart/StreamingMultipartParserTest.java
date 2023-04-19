package dev.baecher.multipart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class StreamingMultipartParserTest {
    @Test
    void testBasicInput() throws IOException {
        InputStream is = new ByteArrayInputStream((""
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo; filename=first.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is the first file.\r\n"
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo2; filename*=utf-8''second%20%E2%82%AC.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is the second file.\r\n"
                + "--C7AHVyJbNc--\r\n").getBytes(StandardCharsets.UTF_8));

        StreamingMultipartParser parser = new StreamingMultipartParser(is);

        assertTrue(parser.hasNext());
        StreamingMultipartParser.Part firstPart = parser.next();
        assertEquals("foo", firstPart.getHeaders().getName());
        assertEquals("first.txt", firstPart.getHeaders().getFilename());
        assertEquals("This is the first file.",
                new String(firstPart.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertTrue(parser.hasNext());
        StreamingMultipartParser.Part secondPart = parser.next();
        assertEquals("foo2", secondPart.getHeaders().getName());
        assertEquals("second €.txt", secondPart.getHeaders().getFilename());
        assertEquals("This is the second file.",
                new String(secondPart.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        assertFalse(parser.hasNext());
    }

    @Test
    void testVariousBufferSizes() throws IOException {
        String fullData = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        for (int i = 15; i < 100; i++) {
            for (int j = 0; j < fullData.length(); j++) {
                String data = fullData.substring(0, j);
                byte[] input = (""
                        + "----0\r\n"
                        + "\r\n"
                        + "\r\n"
                        + data.substring(0, j) + "\r\n"
                        + "----0\r\n"
                        + "\r\n"
                        + "\r\n"
                        + "hi\r\n"
                        + "----0--\r\n").getBytes(StandardCharsets.UTF_8);

                StreamingMultipartParser parser = new StreamingMultipartParser(new ByteArrayInputStream(input), i);
                assertEquals(data, new String(parser.next().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                assertEquals("hi",
                        new String(parser.next().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                assertFalse(parser.hasNext());
            }
        }
    }

    @Test
    void testPrematureEndOfData() throws IOException {
        InputStream is = new ByteArrayInputStream((""
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo; filename=first.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is some data.\r\n").getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> new StreamingMultipartParser(is).next().getInputStream().readAllBytes());
    }

    @Test
    void testStreamsFullyRead() throws IOException {
        InputStream is = new ByteArrayInputStream((""
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo; filename=first.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is the first file.\r\n"
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo2; filename*=utf-8''second%20%E2%82%AC.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is the second file.\r\n"
                + "--C7AHVyJbNc--\r\n").getBytes(StandardCharsets.UTF_8));

        StreamingMultipartParser parser = new StreamingMultipartParser(is);

        parser.next();
        assertThrows(IllegalStateException.class, parser::next);
    }

    @Test
    void testStreamsReadableOnce() throws IOException {
        InputStream is = new ByteArrayInputStream((""
                + "--C7AHVyJbNc\r\n"
                + "Content-Disposition: form-data; name=foo; filename=first.txt\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "This is the first file.\r\n"
                + "--C7AHVyJbNc--\r\n").getBytes(StandardCharsets.UTF_8));

        StreamingMultipartParser parser = new StreamingMultipartParser(is);

        InputStream partStream = parser.next().getInputStream();
        partStream.readAllBytes();

        assertThrows(IllegalStateException.class, partStream::readAllBytes);
    }
}
