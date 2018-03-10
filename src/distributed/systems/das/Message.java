package distributed.systems.das;

import java.io.Serializable;
import java.util.HashMap;

public class Message implements Serializable{
	private static final long serialVersionUID = -2501468001030794321L;
	private MessageRequest messageType;
	private HashMap<String, Object> messageAttributes = new HashMap<String, Object>();
			
	public MessageRequest getMessageType() {
		return messageType;
	}
	public void setMessageType(MessageRequest type) {
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
