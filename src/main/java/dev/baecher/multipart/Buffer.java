package dev.baecher.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class Buffer {
    private final byte[] data;
    private int offset;
    private int length;
    private boolean isEof;
    private final InputStream source;

    public Buffer(InputStream is, int bufferSize) {
        data = new byte[bufferSize];
        offset = 0;
        length = 0;
        isEof = false;
        source = is;
    }

    private void ensureLength(int len) throws IOException {
        if (length < len) {
            throw new IllegalStateException("unexpected end of buffer");
        }
    }

    private void compact() {
        if (offset > 0) {
            System.arraycopy(data, offset, data, 0, length);
            offset = 0;
        }
    }

    public int getLength() {
        return length;
    }

    public int getBufferSize() {
        return data.length;
    }

    public void refill() throws IOException {
        compact();

        int capacity = data.length - offset - length;

        if (capacity > 0) {
            int bytesRead = source.readNBytes(data, offset + length, capacity);
            if (bytesRead < capacity) {
                isEof = true;
            }
            length += bytesRead;
        }
    }

    public int find(byte[] b) {
        // TODO might need a better implementation (BM etc.)
        int lastOffset = offset + length - b.length;
        for (int i = offset; i <= lastOffset; i++) {
            if (Arrays.equals(b, 0, b.length, data, i, i + b.length)) {
                return i - offset;
            }
        }

        return -1;
    }

    public void skip(int len) throws IOException {
        ensureLength(len);
        offset += len;
        length -= len;
    }

    public void consume(byte[] b, int off, int len) throws IOException {
        ensureLength(len);
        System.arraycopy(data, offset, b, off, len);
        offset += len;
        length -= len;
    }

    public byte[] consume(int len) throws IOException {
        byte[] b = new byte[len];
        consume(b, 0, b.length);
        return b;
    }

    public boolean startsWith(byte[] b) throws IOException {
        ensureLength(b.length);
        return Arrays.equals(b, 0, b.length, data, offset, offset + b.length);
    }

    public boolean isEof() {
        return isEof;
    }
}
