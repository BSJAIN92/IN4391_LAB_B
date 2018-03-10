package distributed.systems.das;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import distributed.systems.das.BattleField;

public class Server {
	static ServerSocket serverSocket = null;
	private static int clientIdCounter;
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			serverSocket = new ServerSocket(9000);
			while(true) {
				
				Socket clientSocket = serverSocket.accept();
				clientIdCounter++;
				ClientInformation.addClient(clientIdCounter, clientSocket);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
}
