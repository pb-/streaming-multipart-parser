package dev.baecher.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class StreamingMultipartParser implements Iterator<StreamingMultipartParser.Part> {
    private final int CR = 13;
    private final int LF = 10;
    private final byte[] CRLF = new byte[]{CR, LF};
    private final byte[] CRLFCRLF = new byte[]{CR, LF, CR, LF};
    private final byte[] boundaryMarker;
    private final Buffer buffer;

    private enum Status {EXPECT_HEADER_OR_END, READING_DATA}

    private Status status;

    public StreamingMultipartParser(InputStream is) throws IOException {
        this(is, 4096);
    }

    public StreamingMultipartParser(InputStream is, int bufferSize) throws IOException {
        buffer = new Buffer(is, bufferSize);
        buffer.refill();

        int off = buffer.find(CRLF);
        if (off < 0) {
            throw new IllegalArgumentException("no boundary could be found at the start of the stream");
        }

        if (buffer.getBufferSize() < 3 * off) {
            throw new IllegalArgumentException("buffer size should be much larger than boundary marker length");
        }

        boundaryMarker = new byte[off + 2];
        boundaryMarker[0] = CR;
        boundaryMarker[1] = LF;
        buffer.consume(boundaryMarker, 2, off);

        status = Status.EXPECT_HEADER_OR_END;
    }

    @Override
    public boolean hasNext() {
        if (status != Status.EXPECT_HEADER_OR_END) {
            throw new IllegalStateException("must exhaust previous part stream before dealing with next part");
        }

        try {
            buffer.refill();
            return buffer.startsWith(CRLF);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Part next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        try {
            buffer.refill();
            buffer.skip(CRLF.length);

            int off = buffer.find(CRLFCRLF);
            if (off < 0) {
                throw new IllegalArgumentException("could not find end of header");
            }

            Headers headers = Headers.fromBytes(buffer.consume(off));
            buffer.skip(CRLFCRLF.length);

            status = Status.READING_DATA;

            return new Part(headers);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public class Part {
        private final Headers headers;
        private final InputStream inputStream;
        private int partLength;
        private boolean endInSight;
        private boolean streamExhausted;

        private void refill() throws IOException {
            buffer.refill();

            int off = buffer.find(boundaryMarker);
            if (off < 0) {
                if (buffer.isEof()) {
                    throw new IllegalArgumentException("premature end of data, could not find boundary");
                }

                // Must leave some extra space in case part of the boundary is cut off at the end of the buffer.
                partLength = buffer.getLength() - boundaryMarker.length;
            } else {
                partLength = off;
                endInSight = true;
            }
        }

        private Part(Headers headers) {
            this.headers = headers;
            streamExhausted = false;
            inputStream = new InputStream() {
                final byte[] oneByte = new byte[1];

                private void checkStatus() {
                    if (streamExhausted) {
                        throw new IllegalStateException("cannot read from stream any more");
                    }
                }

                @Override
                public int read() throws IOException {
                    int bytesRead = read(oneByte, 0, 1);
                    if (bytesRead <= 0) {
                        return -1;
                    } else {
                        return oneByte[0];
                    }
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    checkStatus();

                    if (partLength == 0) {
                        if (endInSight) {
                            streamExhausted = true;
                            buffer.skip(boundaryMarker.length);
                            status = Status.EXPECT_HEADER_OR_END;
                            return -1;
                        }
                        refill();
                    }

                    int bytesRead = Math.min(len, partLength);
                    buffer.consume(b, off, bytesRead);
                    partLength -= bytesRead;

                    return bytesRead;
                }
            };
        }

        public Headers getHeaders() {
            return headers;
        }

        public InputStream getInputStream() {
            return inputStream;
        }
    }
}
