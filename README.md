# sseclient-java
A java implementation of Server Sent Events (SSE), particularly for Firebase streaming.

Open a connection to a server:
```java

// for firebase streaming of the root, hit /.json.
final URL url = new URL("https://myapp.firebaseio.com/.json");

final SSEClient client = new SSEClient(url, null, 3000, null, queryParameters);

for (SSEMessage message : client) {
  System.out.println("Got message: " + message);
}
```

