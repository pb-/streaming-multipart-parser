# streaming-multipart-parser

A tiny library for decoding `multipart/form-data` that uses constant memory and no disk I/O. No dependencies apart from JDK 11+.

Many libraries or frameworks to parse this content type will either put files into memory or store them on disk. In some cases neither is an acceptable option. This library effectively provides a standard `Iterator` of `InputStream`s so that each part can be (sequentially) processed as a stream.


## Getting it

 * Group id: `dev.baecher.multipart`
 * Artifact id: `streaming-multipart-parser`
 * Version: `0.9.7`


## Usage example

Read all parts and print their file names, content types, and lengths.

```java
// The parser implements the `Iterator` interface and takes any InputStream
StreamingMultipartParser parser = new StreamingMultipartParser(someInputStream);
while (parser.hasNext()) {
    StreamingMultipartParser.Part part = parser.next();

    // A convenience method to parse the filename out of the Content-Disposition header
    System.out.println(part.getHeaders().getFileName());

    // Any header can be looked up case-insensitively by name, returning the raw value
    System.out.println(part.getHeaders().getHeaderValue("content-type"));

    // A standard java.io.InputStream of the body
    System.out.println(part.getInputStream().transferTo(OutputStream.nullOutputStream()));
}
```

Note that you have to exhaust the stream of each part (until hitting EOF) before you can move on to the next part. If you want to ignore the body of the part, you can use a construction like in the example above.


## Limitations

 * No thread safety guaranteed whatsoever.
 * Searching for the part boundary (`Buffer.find()`) is using a very naive algorithm.
