package distributed.systems.das;

import java.net.Socket;
import java.util.HashMap;

public class ClientInformation {
	private static HashMap<Integer, Socket> clientSockets = new HashMap<Integer, Socket>();
	
	public static void addClient(int clientId, Socket socket) {
		if(!clientSockets.containsKey(clientId)) {
			clientSockets.put(clientId, socket);
		}
	}
}
