# sseclient-java
A java implementation of Server Sent Events (SSE), particularly for Firebase streaming.

Open a connection to a server:
```java

// for firebase streaming of the root, hit /.json.
final URL url = new URL("https://myapp.firebaseio.com/.json");

// firebase auth requires the secret in the 'auth' querystring parameter
final Map<String, Object> queryParameters = new HashMap<String,Object>();
queryParameters.put("auth", "firebasesecret1230981");

// connect to the server
final SSEClient client = new SSEClient(url, null, 3000, null, queryParameters);

// read off messages indefinitely
for (SSEMessage message : client) {
  System.out.println("Got message: " + message);
}
```

