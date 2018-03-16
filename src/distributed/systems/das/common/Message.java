package distributed.systems.das.common;

import java.io.Serializable;
import java.util.HashMap;

public class Message implements Serializable{
	private static final long serialVersionUID = -2501468001030794321L;
	private MessageType messageType;
	private HashMap<String, Object> messageAttributes = new HashMap<String, Object>();
			
	public MessageType getMessageType() {
		return messageType;
	}
	public void setMessageType(MessageType type) {
		messageType = type;
	}
	public HashMap<String, Object> get() {
		return messageAttributes;
	}
	public Object get(String attributeName) {
		return messageAttributes.get(attributeName);
	}
	public void put(String key, Object value) {
		messageAttributes.put(key, value);
	}
}
