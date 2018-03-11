package distributed.systems.das;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.units.Player;
import distributed.systems.das.units.Unit;

public class RequestHandlingServer implements MessagingHandler{

	private static int size = 25;
	public static int localMessageCounter = 0;
	private static String GameServerIp = "localhost";
	private static int port = 1099; //we use default RMI Registry port
	private static HashMap<Integer, Message> messageQueue = new HashMap<Integer, Message>();
	private static MessagingHandler gameServerHandle;
	@Override
	public void onMessageReceived(Message message) throws RemoteException {		
		messageQueue.put((Integer) message.get("id"), message);
	}
	
	public static void main(String args[]) throws NotBoundException {
		
		try {
			LocateRegistry.createRegistry(1099);
			String name = args[0];
			MessagingHandler reqHandlingServer = new RequestHandlingServer();
            MessagingHandler reqHandlingServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(reqHandlingServer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, reqHandlingServerStub);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Registry remoteRegistry  = LocateRegistry.getRegistry(GameServerIp, port);
			gameServerHandle= (MessagingHandler) remoteRegistry.lookup("GameServer");
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		spawnPlayers();
	}
	
	private static void spawnPlayers() {
		while(!messageQueue.containsKey(0)) {
			
		}
		Message reply = messageQueue.get(0);
		ArrayList<Integer> unitIds =  (ArrayList<Integer>) reply.get("setup");
		
		/* Create the new player in a separate
		 * thread, making sure it does not 
		 * block the system.
		 */
		new Thread(new Runnable() {
			public void run() {
				new Player(finalX, finalY);
			}
		}).start();
	}
}
