package dev.baecher.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BoundaryInputStream extends InputStream {
    private final InputStream source;
    private final byte[] buffer;
    private int bufferOffset;
    private int validLength;
    private final byte[] singleByteBuffer = new byte[1];

    private byte[] boundary;
    private int[] boundaryByteTable;
    private int[] boundaryOffsetTable;

    public static class Builder {
        private final InputStream source;
        private int bufferSize = 1 << 14;
        private byte[] boundary;

        private Builder(InputStream source) {
            this.source = source;
        }

        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder boundary(byte[] boundary) {
            this.boundary = boundary;
            return this;
        }

        public BoundaryInputStream build() {
            return new BoundaryInputStream(source, bufferSize, boundary);
        }
    }

    public static Builder builder(InputStream source) {
        return new Builder(source);
    }

    private BoundaryInputStream(InputStream is, int bufferSize, byte[] boundary) {
        source = is;
        buffer = new byte[bufferSize];

        if (boundary != null) {
            setBoundary(boundary);
        }
    }

    public void setBoundary(byte[] b) {
        if (b == null) {
            throw new IllegalArgumentException("boundary must not be null, use clearBoundary() to unset");
        }

        if (b.length == 0) {
            throw new IllegalArgumentException("boundary must have non-zero length");
        }

        if (b.length > buffer.length) {
            throw new IllegalArgumentException("boundary is too large for buffer");
        }

        boundary = b.clone();
        boundaryByteTable = byteTable(boundary);
        boundaryOffsetTable = offsetTable(boundary);
    }

    public void clearBoundary() {
        boundary = null;
    }

    @Override
    public int read() throws IOException {
        int n = read(singleByteBuffer, 0, singleByteBuffer.length);
        if (n == -1) {
            return -1;
        } else {
            return singleByteBuffer[0] & 0xff;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        refillBuffer();

        int length = readableLength(len);
        if (length == 0) {
            return -1;
        }

        int rightLength = Math.min(length, buffer.length - bufferOffset);
        int leftLength = length - rightLength;

        System.arraycopy(buffer, bufferOffset, b, off, rightLength);
        System.arraycopy(buffer, 0, b, off + rightLength, leftLength);

        bufferOffset = (bufferOffset + length) % buffer.length;
        validLength -= length;

        return length;
    }

    private int readableLength(int requestedReadLength) {
        if (boundary == null) {
            return Math.min(requestedReadLength, validLength);
        }

        int searchWindowLength = Math.min(requestedReadLength + boundary.length - 1, validLength);

        return Math.min(requestedReadLength, boundaryFreeLength(searchWindowLength));
    }

    private int boundaryFreeLength(int windowLength) {
        int index = indexOfBoundary(windowLength);
        if (index != -1) {
            return index;
        }

        if (validLength < buffer.length) {
            // Source is exhausted and no boundary found, so we
            // can safely release all bytes.
            return windowLength;
        }

        // Must withhold some bytes because there could be a
        // prefix of the boundary in them and there is still
        // more data to be read from the source.
        return windowLength - boundary.length + 1;
    }

    private void refillBuffer() throws IOException {
        int offset = (bufferOffset + validLength) % buffer.length;
        int length = buffer.length - validLength;
        int rightLength = Math.min(length, buffer.length - offset);
        int leftLength = length - rightLength;
        int bytesRead = 0;

        bytesRead += source.readNBytes(buffer, offset, rightLength);
        bytesRead += source.readNBytes(buffer, 0, leftLength);

        validLength += bytesRead;
    }

    private int indexOfBoundary(int maxLength) {
        for (int i = boundary.length - 1, j; i < maxLength; ) {
            for (j = boundary.length - 1; boundary[j] == buffer[(bufferOffset + i) % buffer.length]; --i, --j) {
                if (j == 0) {
                    return i;
                }
            }
            i += Math.max(
                    boundaryOffsetTable[boundary.length - 1 - j],
                    boundaryByteTable[buffer[(bufferOffset + i) % buffer.length] & 0xff]);
        }

        return -1;
    }

    private static int[] byteTable(byte[] b) {
        int[] table = new int[256];
        Arrays.fill(table, b.length);

        for (int i = 0; i < b.length; ++i) {
            table[b[i] & 0xff] = b.length - 1 - i;
        }

        return table;
    }

    private static int[] offsetTable(byte[] b) {
        int[] table = new int[b.length];
        int lastPrefixPosition = b.length;

        for (int i = b.length; i > 0; --i) {
            if (isPrefix(b, i)) {
                lastPrefixPosition = i;
            }
            table[b.length - i] = lastPrefixPosition - i + b.length;
        }

        for (int i = 0; i < b.length - 1; ++i) {
            int len = suffixLength(b, i);
            table[len] = b.length - 1 - i + len;
        }

        return table;
    }

    private static boolean isPrefix(byte[] b, int position) {
        for (int i = position, j = 0; i < b.length; ++i, ++j) {
            if (b[i] != b[j]) {
                return false;
            }
        }

        return true;
    }

    private static int suffixLength(byte[] b, int position) {
        int len = 0;

        for (int i = position, j = b.length - 1;
             i >= 0 && b[i] == b[j]; --i, --j) {
            len += 1;
        }

        return len;
    }
}
