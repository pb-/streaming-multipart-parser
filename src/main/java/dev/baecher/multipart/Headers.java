package dev.baecher.multipart;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Headers {
    private static class Header {
        private final String name;
        private final String value;

        public static Header parseLine(String s) {
            String[] parts = s.split("\\s*:\\s*", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException("malformed header line: " + s);
            }

            return new Header(parts[0], parts[1]);
        }

        private Header(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    private final List<Header> headers;

    private Headers(List<Header> headers) {
        this.headers = headers;
    }

    private static String decodeParameterValue(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        String magicUtf8Indicator = "utf-8''";
        if (value.startsWith(magicUtf8Indicator)) {
            return URLDecoder.decode(value.substring(magicUtf8Indicator.length()), StandardCharsets.UTF_8);
        }

        return value;
    }

    private static Map<String, String> parseDispositionParameters(String contentDispositionValue) {
        // https://www.rfc-editor.org/rfc/rfc6266

        String[] parts = contentDispositionValue.split("\\s*;\\s*", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "malformed content-disposition header value: " + contentDispositionValue);
        }

        // parts[0] ignored
        String remainingParams = parts[1];

        Map<String, String> parameters = new TreeMap<>();

        do {
            parts = remainingParams.split("\\s*;\\s*", 2);
            String[] paramParts = parts[0].split("\\s*=\\s*", 2);
            if (paramParts.length != 2) {
                throw new IllegalArgumentException("malformed parameter in content-disposition header: " + parts[0]);
            }
            parameters.put(paramParts[0].toLowerCase(), decodeParameterValue(paramParts[1]));

            if (parts.length < 2) {
                break;
            }

            remainingParams = parts[1];
        } while (true);

        return parameters;
    }

    public static Headers fromBytes(byte[] b) {
        if (b.length == 0) {
            return new Headers(Collections.emptyList());
        }

        String s = new String(b, StandardCharsets.UTF_8);
        return new Headers(Arrays.stream(s.split("\r\n")).map(Header::parseLine).collect(Collectors.toList()));
    }

    public List<Header> getAll() {
        return headers;
    }

    public String getHeaderValue(String headerName) {
        return headers
                .stream()
                .filter(header -> header.getName().equalsIgnoreCase(headerName))
                .map(Header::getValue)
                .findFirst()
                .orElse(null);
    }

    public String getName() {
        String cd = getHeaderValue("content-disposition");
        if (cd == null) {
            return null;
        }

        return parseDispositionParameters(cd).get("name");
    }

    public String getFilename() {
        String cd = getHeaderValue("content-disposition");
        if (cd == null) {
            return null;
        }

        Map<String, String> parameters = parseDispositionParameters(cd);
        return parameters.getOrDefault("filename*", parameters.get("filename"));
    }
}
