package com.jbrower.sseclient;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Represents a server-sent message.
 * @author Justin
 */
public class SSEMessage {

	public static Logger log = Logger.getLogger(SSEMessage.class.getName());
	
	public final String event;
	public final String data;
	public final int retry;
	public final String id;
	
	/**
	 * Creates a server-sent event. (SSE).
	 * @param e The event 
	 * @param d The data associated with the event
	 * @param _retry Whether or not this message was issued with a retry, if available.
	 * @param _lastId The last id sent with this message, if available.
	 */
	public SSEMessage(final String e, final String d, final int _retry, final String _lastId) {
		event = e;
		data = d;
		retry = _retry;
		id = _lastId;
	}
	
	/**
	 * A regex pattern to match something like 'event: put' or 'data: {{stuff :12}}' etc.
	 */
	private static final Pattern LINE_PATTERN = Pattern.compile("(?<key>[^:]*):(?<value>.*)");
	
	/**
	 * Creates an SSE message from a string sent from the server.
	 * @param response The string sent from the server.
	 * @return An SSE message.
	 */
	public static SSEMessage fromString(final String response) {
		
		String event = null;
		StringBuffer data = new StringBuffer();
		int retry = -1;
		String id = null;
		
		for (final String line : response.split("\n")) {
			final Matcher matcher = LINE_PATTERN.matcher(line);
			if (!matcher.matches()) {
				log.log(Level.WARNING, "Couldn't parse line: " + line);
				continue;
			}
			//System.out.println("Matches?: " + matcher.matches());
			final String name = matcher.group("key").trim();
			final String value = matcher.group("value").trim();
			
			switch (name) {
			case "":
				// this line is a comment
				continue;
			case "data":
				// this is a piece of the data.
				if (data.toString().length() == 0) {
					data.append(value);
				} else {
					data.append("\n");
					data.append(value);
				}
				break;
			case "event":
				// the event type
				event = value;
				break;
			case "id":
				// the id of the message
				id = value;
				break;
			case "retry":
				// a retry time frame
				retry = Integer.parseInt(value);
				break;
			}
		}
		
		return new SSEMessage(event, data.toString(), retry, id);
	}
	
	@Override
	public String toString() {
		return String.format("SSE: %s: %s", event, data);
	}
	
	/**
	 * Returns true if this sse message was sent with information on when to retry the connection.
	 * @return
	 */
	public boolean hasRetry() {
		return retry >= 0;
	}
	
	/**
	 * Returns true if this sse message was sent with an id.
	 * @return
	 */
	public boolean hasLastId() {
		return id != null;
	}
	
}
