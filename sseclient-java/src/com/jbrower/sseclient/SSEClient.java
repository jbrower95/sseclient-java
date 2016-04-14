package com.jbrower.sseclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a client implements for SSE (Server Sent Events).
 * Check out a standard here (https://html.spec.whatwg.org/multipage/comms.html#server-sent-events)
 * 
 * This class allows for easy connection to a server, and then indefinite iteration as you listen for more events.
 * @author Justin
 */
public class SSEClient implements Iterable<SSEMessage>, AutoCloseable {
	
	/* For recording events. */
	public Logger log = Logger.getLogger(SSEClient.class.getName());
	
	/* The URL that this SSEClient is targeting. */
	private URL mUrl;
	private String mLastId;
	private int mRetryPeriod;
	private final Map<String, String> mHeaders;
	private final Map<String, Object> mQueryParameters;
	
	private StringBuffer mBuffer;
	private HttpURLConnection mResponse;
	private boolean mHealthy;
	
	/**
	 * Initializes an SSE client for a specific URL using the default configuration.
	 * @param url The URL at which the SSE service resides.
	 */
	public SSEClient(final URL url) {
		this(url, null, 3000, new HashMap<String, String>(), new HashMap<String, Object>());
	}
	
	/**
	 * Initializes an SSE client.
	 * @param url The URL at which the service resides. 
	 * @param lastId The id of the last message received. Nullable.
	 * @param retry The amount of time to wait before retrying. Recommended default is 3000.
	 * @param headers Any headers to be included with the HTTP request. Nullable.
	 * @param queryParameters Any query parameters to be appended onto the URL. Parameters must be URL encoded.
	 */
	public SSEClient(final URL url, final String lastId, final int retry, final Map<String, String> headers, final Map<String, Object> queryParameters) {
		
		if (headers != null) {
			mHeaders = headers;
		} else {
			mHeaders = new HashMap<String, String>();
		}

        // The SSE specification requires making requests with Cache-Control: nocache
		mHeaders.put("Cache-Control", "no-cache");
		// The Accept header isn't required but better to be safe.
		mHeaders.put("Accept", "text/event-stream");
		
		// Things to put in our header field.
		mQueryParameters = (queryParameters != null ? queryParameters : new HashMap<String, Object>());
		
		mUrl = url;
		mLastId = lastId;
		mRetryPeriod = retry;
		
		mBuffer = new StringBuffer();
		mHealthy = true;
		connect();
	}
	
	/* Connect to the stream, man. */
	private void connect() {
		if (mLastId != null) {
			mHeaders.put("Last-Event-ID", mLastId);
		} else {
			mHeaders.remove("Last-Event-ID");
		}
		
		System.out.println("Connecting to: " + mUrl.toString());
		System.out.println("Headers: " + mHeaders.toString());
		System.out.println("Query Parameters: " + mQueryParameters.toString());
		
		try {
			
			/* modify mUrl based on the query parameters */
			final StringBuilder url = new StringBuilder(mUrl.toString());
			if (mQueryParameters.keySet().size() > 0) {
				url.append("?");
				for (String key : mQueryParameters.keySet()) {
					url.append(key);
					url.append("=");
					url.append(mQueryParameters.get(key));
					url.append("&");
				}
				/* remove last & character */
				mUrl = new URL(url.toString().substring(0, url.toString().length() - 1));
			}
			
			
			// open a connection
			mResponse = (HttpURLConnection) mUrl.openConnection();
			
			// set headers
			for (String key : mHeaders.keySet()) {
				mResponse.setRequestProperty(key, mHeaders.get(key));
			}
			
			System.out.println("Connecting to URL: " + mUrl.toString());
			
			// fire off the connection
			mResponse.connect();
		
			if (mResponse != null && (mResponse.getResponseCode() != 200 && mResponse.getResponseCode() != 307)) {
				log.log(Level.WARNING, String.format("Error: status %d",  mResponse.getResponseCode()));
				mHealthy = false;
			}
			
			System.out.println("Got response: " + mResponse.getResponseCode());
		} catch (IOException e) {
			mHealthy = false;
			e.printStackTrace();
		}
	}

	@Override
	public Iterator<SSEMessage> iterator() {
		return new Iterator<SSEMessage>() {
			@Override
			public boolean hasNext() {
				return mHealthy;
			}

			@Override
			public SSEMessage next() {
				return getNextMessage();
			}

			@Override
			public void remove() {
				return;
			}
		};
	}
	
	
	/**
	 * Literally ported directly from python
	 * Split the string at the last occurrence of sep, and return a 3-tuple containing the part before the separator, 
	 * the separator itself, and the part after the separator. If the separator is not found, return a 3-tuple containing two empty strings, 
	 * followed by the string itself.
	 * @param string The string to search.
	 * @param delim The delimiter pattern to find.
	 * @return The spec above.
	 */
	private static String[] rpartition(final String string, final String delim) {
		int index = string.lastIndexOf(delim);
		
		if (index == -1) {
			return new String[] {"", "", string};
		}
		
		return new String[] {string.substring(0, index), string.substring(index, index + delim.length()), string.substring(index + delim.length()) };
	}
	
	/**
	 * Literally ported directly from python
	 * Split the string at the first occurrence of sep, and return a 3-tuple containing the part before the separator, 
	 * the separator itself, and the part after the separator. 
	 * If the separator is not found, return a 3-tuple containing the string itself, followed by two empty strings.
	 * @param string
	 * @param delim
	 * @return
	 */
	private static String[] partition(final String string, final String delim) {
		int index = string.indexOf(delim);
		
		if (index == -1) {
			return new String[] {string, "", ""};
		}
		
		return new String[] {string.substring(0, index), string.substring(index, index + delim.length()), string.substring(index + delim.length()) };
	}
	
	/**
	 * Loads the next server sent event message.
	 * @return The next message.
	 */
	private SSEMessage getNextMessage() {
		/* Load in the response for the current thing. */
		mBuffer = new StringBuffer();
		
		while (!mBuffer.toString().contains("\n\n")) {
			try {
				InputStream stream = mResponse.getInputStream();
				int c = stream.read();
				if (c == -1) {
					break;
				}
				mBuffer.append((char)c);
			} catch (IOException e) {
				log.log(Level.WARNING, "IO Error", e);
				Util.sleep(mRetryPeriod);
				connect();
				
				final String[] headSepTail = rpartition(mBuffer.toString(), "\n\n");
				mBuffer = new StringBuffer(headSepTail[0]);
				mBuffer.append(headSepTail[1]);
				continue;
			}
		}
		
		final String[] headSepTail = partition(mBuffer.toString(), "\n\n");
		final String head = headSepTail[0];
		final String tail = headSepTail[2];
		
		mBuffer = new StringBuffer(tail);
		
		final SSEMessage message = SSEMessage.fromString(head);

		if (message.hasRetry()) {
			mRetryPeriod = message.retry;
		}
		
		if (message.hasLastId()) {
			mLastId = message.id;
		}
		
		return message;
	}
	
	public static void main(String[] args) throws MalformedURLException {
		final String firebaseSecret = "";
		final String appName = "";
		
		final URL url = new URL(String.format("https://%s.firebaseio.com/.json", appName));
		final HashMap<String, Object> queryParameters = new HashMap<>();
		
		/* Supply firebase auth parameters to test this out. */
		queryParameters.put("auth", firebaseSecret);
		
		final SSEClient client = new SSEClient(url, null, 3000, null, queryParameters);
		
		for (SSEMessage msg : client) {
			System.out.println("Got server event!: " + msg);
		}
		
		System.out.println("Done.");
	}

	@Override
	public void close() throws Exception {
		if (mResponse != null) {
			mResponse.disconnect();
		}
	}
}
