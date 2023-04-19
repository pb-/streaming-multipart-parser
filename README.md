# streaming-multipart-parser

A tiny library for decoding `multipart/form-data` that uses constant memory and no disk I/O. No dependencies apart from JDK 11+.


## Usage example

Read all parts and print their file names, content types, and lengths.

```java
StreamingMultipartParser parser = new StreamingMultipartParser(someInputStream);
while (parser.hasNext()) {
    StreamingMultipartParser.Part part = parser.next();

    System.out.println(part.getHeaders().getFileName());
    System.out.println(part.getHeaders().getHeaderValue("content-type"));
    System.out.println(part.getInputStream().transferTo(OutputStream.nullOutputStream()));
}
```

Note that you have to exhaust the stream of each part (until hitting EOF) before you can move on to the next part.


## Limitations

 * No thread safety guaranteed whatsoever.
 * Searching for the part boundary (`Buffer.find()`) is using a very naive algorithm.
