package distributed.systems.das;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;

public class RequestHandlingServer implements MessagingHandler {
	public int MAP_WIDTH;
	public int MAP_HEIGHT;
	public UnitState[][] map;
	private int localMessageCounter = 0;
	
	private static RequestHandlingServer battlefield;
	private static String GameServerIp = "localhost";
	private static int port = 1099; //we use default RMI Registry port
	private static MessagingHandler gameServerHandle;
	
	
	private RequestHandlingServer(int width, int height){
		MAP_WIDTH = width;
		MAP_HEIGHT = height;
	}
	public static RequestHandlingServer getRequestHandlingServer(){
		if(battlefield == null) {
			battlefield = new RequestHandlingServer(25, 25);
			battlefield.map = new UnitState[25][25];
		}
		return battlefield;
	}
	
	
	public UnitState getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
	}
	
	public synchronized void moveUnit(UnitState unit, int toX, int toY) {
		Message moveMessage = new Message();
		int id = ++localMessageCounter;
		moveMessage.put("request", MessageType.moveUnit);
		moveMessage.put("toX", toX);
		moveMessage.put("toY", toY);
		moveMessage.put("id", id);
		moveMessage.put("unit", unit);
		moveMessage.put("origin", unit.helperServerAddress);
		try {
			Message reply = gameServerHandle.onMessageReceived(moveMessage);
			if((boolean)reply.get("moveSuccess")) {
				map[toX][toY] =  (UnitState)reply.get("unit");
				map[unit.x][unit.y] = null;
			}
		} catch (RemoteException e) { 
			e.printStackTrace();
		}
	}
	
	public synchronized void healDamage(UnitState unit, int toX, int toY) {
		int id;
		Message healMessage;
		synchronized (this) {
			id = ++localMessageCounter;
			healMessage = new Message();
			healMessage.put("request", MessageType.healDamage);
			healMessage.put("toX", toX);
			healMessage.put("toY", toY);
			healMessage.put("healedPoints", unit.attackPoints);
			healMessage.put("id", id);
			healMessage.put("origin", unit.helperServerAddress);
			try {
				Message reply = gameServerHandle.onMessageReceived(healMessage);
				UnitState healedUnit = (UnitState) reply.get("unit");
				map[toX][toY].hitPoints = healedUnit.hitPoints;
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}	
	}
	
	public synchronized void dealDamage(UnitState unit, int toX, int toY) {
		int id;
		Message dealMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			dealMessage = new Message();
			dealMessage.put("request", MessageType.dealDamage);
			dealMessage.put("toX", toX);
			dealMessage.put("toY", toY);
			dealMessage.put("damagePoints", unit.attackPoints);
			dealMessage.put("id", id);
			dealMessage.put("origin", unit.helperServerAddress);
			try {
				Message reply = gameServerHandle.onMessageReceived(dealMessage);
				UnitState damagedUnit = (UnitState) reply.get("unit");
				map[toX][toY].hitPoints = damagedUnit.hitPoints;
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void removeUnit(int x, int y)
	{
		int id;
		Message removeMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			removeMessage = new Message();
			removeMessage.put("request", MessageType.removeUnit);
			removeMessage.put("x", x);
			removeMessage.put("y", y);
			removeMessage.put("id", id);
			Message reply;
			try {
				reply = gameServerHandle.onMessageReceived(removeMessage);
				if(reply != null) {
					map[x][y] = null;
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void placeUnitOnMap(UnitState u) {
		try {
			battlefield.map[u.x][u.y] = u;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return;
	}
	public static void main(String args[]) throws NotBoundException {	
		try {
			//String name = args[0];
			String name = "reqServer1";
			LocateRegistry.createRegistry(1099);
			MessagingHandler reqHandlingServer = getRequestHandlingServer();
            MessagingHandler reqHandlingServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(reqHandlingServer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, reqHandlingServerStub);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public synchronized Message onMessageReceived(Message message) throws RemoteException {
		if(message.get("type").equals(MessageType.setup)) {
			try {
				Registry remoteRegistry  = LocateRegistry.getRegistry(GameServerIp, port);
				gameServerHandle= (MessagingHandler) remoteRegistry.lookup("gameServer");
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
			/* Create the new player in a separate
			 * thread, making sure it does not 
			 * block the system.
			 */
			for(UnitState u : (ArrayList<UnitState>) message.get("dragons")) {
				placeUnitOnMap(u);
				Dragon d = new Dragon(u);
			}
			for(UnitState u : (ArrayList<UnitState>) message.get("players")) {
				placeUnitOnMap(u);
				Player p = new Player(u);
			}			
		}
		return null;
	}
	
	@Override
	public void onSynchronizationMessageReceived(Message message) throws RemoteException {
		if(message.get("request").equals(MessageType.sync)) {
			UnitState u;
			MessageType messageType = (MessageType)message.get("type");
			switch(messageType) {
			case spawnUnit:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y] = u;
				}
				break;
			case dealDamage:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y].hitPoints = u.hitPoints;
				}
				break;
			case healDamage:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y].hitPoints = u.hitPoints;
				}
				break;
			case moveUnit:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y] = u;
				}
				break;
			case putUnit:
				break;
			case removeUnit:
				int x = (Integer)message.get("removeX");
				int y = (Integer)message.get("removeY");
				synchronized(this) {
					map[x][y] = null;
				}
				break;
			case getType:
				break;
			case getUnit:
				break;
			case setup:
				break;
			case sync:
				break;
			default:
				break;			
			}
		}
		
	}

}
