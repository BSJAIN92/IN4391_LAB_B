package distributed.systems.das;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.common.UnitType;
import distributed.systems.das.services.HeartbeatService;
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.services.RequestServerFailoverService;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;

public class BackupRequestHandlingServer implements MessagingHandler {
	public int MAP_WIDTH;
	public int MAP_HEIGHT;
	public UnitState[][] map;
	private int localMessageCounter = 0;

	private static BackupRequestHandlingServer battlefield;
	private static String gameServerIp = "localhost";
	private static String backupServerIp = "localhost";
	private static String myServerName = "localhost";
	private static int port = 1099; //we use default RMI Registry port
	private static MessagingHandler gameServerHandle;
	private static MessagingHandler backupReqServerHandle;
	private static HeartbeatService heartbeat;
	private static RequestServerFailoverService reqFailoverService;
	private HashMap<String, String> serverIps;
	private static HashMap<String, Long> timeSinceHeartbeat;
	
	private static HashMap<String, ArrayList<UnitState>> setupDragons;
	private static HashMap<String, ArrayList<UnitState>> setupPlayers;
	
	private BackupRequestHandlingServer(int width, int height){
		MAP_WIDTH = width;
		MAP_HEIGHT = height;
		//read all IP addresses from config file and add them to hashmap
		//createIpMap();	
	}
	private void createIpMap() {
		File ipFile = new File("C:\\Users\\Apourva\\Documents\\DAS\\IN4391_LAB_B\\src\\distributed\\systems\\das\\config\\ipAddresses.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(ipFile));
			String line = null;
			while((line = in.readLine()) != null) {
				String[] s = line.split(" ");
				serverIps.put(s[0], s[1]);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static BackupRequestHandlingServer getRequestHandlingServer(){
		if(battlefield == null) {
			battlefield = new BackupRequestHandlingServer(25, 25);
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
	
	private synchronized UnitState moveUnit(UnitState unit, int newX, int newY)
	{
		if (newX >= 0 && newX < MAP_WIDTH)
			if (newY >= 0 && newY < MAP_HEIGHT)
				if (map[newX][newY] == null) {
					UnitState newUnit = new UnitState(newX, newY, unit.unitID, unit.unitType, unit.helperServerAddress);
					map[newX][newY] =  newUnit;
					removeUnit(unit.x, unit.y);
					return map[newX][newY];
				}
		return null;
	}
	
	private synchronized void removeUnit(int x, int y)
	{
		UnitState unitToRemove = this.getUnit(x, y);
		if (unitToRemove == null)
			return; // There was no unit here to remove
		map[x][y] = null;
	}
	
	public synchronized void moveUnitRequest(UnitState unit, int toX, int toY) {
		Message moveMessage = new Message();
		int id = ++localMessageCounter;
		moveMessage.setMessageType(MessageType.moveUnit);
		moveMessage.put("request", MessageType.moveUnit);
		moveMessage.put("toX", toX);
		moveMessage.put("toY", toY);
		moveMessage.put("id", id);
		moveMessage.put("unit", unit);
		moveMessage.put("origin", unit.helperServerAddress);
		String text = "["+ myServerName+"]"+"Move unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ "to X:"+toX+" Y: "+toY;
		LoggingService.log(MessageType.moveUnit, text);
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
	
	public synchronized void healDamageRequest(UnitState unit, int toX, int toY) {
		int id;
		Message healMessage;
		synchronized (this) {
			id = ++localMessageCounter;
			healMessage = new Message();
			healMessage.setMessageType(MessageType.healDamage);
			healMessage.put("request", MessageType.healDamage);
			healMessage.put("toX", toX);
			healMessage.put("toY", toY);
			healMessage.put("healedPoints", unit.attackPoints);
			healMessage.put("id", id);
			healMessage.put("origin", unit.helperServerAddress);
			String text = "["+ myServerName+"]"+"Heal from unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ "to X:"+toX+" Y: "+toY;
			LoggingService.log(MessageType.healDamage, text);
			try {
				Message reply = gameServerHandle.onMessageReceived(healMessage);
				UnitState healedUnit = (UnitState) reply.get("unit");
				map[toX][toY].hitPoints = healedUnit.hitPoints;
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}	
	}
	
	public synchronized void dealDamageRequest(UnitState unit, int toX, int toY) {
		int id;
		Message dealMessage;
		synchronized(this) {
			id = ++localMessageCounter;
			dealMessage = new Message();
			dealMessage.setMessageType(MessageType.dealDamage);
			dealMessage.put("request", MessageType.dealDamage);
			dealMessage.put("toX", toX);
			dealMessage.put("toY", toY);
			dealMessage.put("damagePoints", unit.attackPoints);
			dealMessage.put("id", id);
			dealMessage.put("origin", unit.helperServerAddress);
			String text = "["+ myServerName+"]"+"Deal unit X: "+ unit.x + " Y: "+ unit.y + " unitID: "+unit.unitID+ "to X:"+toX+" Y: "+toY;
			LoggingService.log(MessageType.dealDamage, text);
			try {
				Message reply = gameServerHandle.onMessageReceived(dealMessage);
				UnitState damagedUnit = (UnitState) reply.get("unit");
				map[toX][toY].hitPoints = damagedUnit.hitPoints;
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void removeUnitRequest(int x, int y)
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
	
	public synchronized void processServerFailure(String serverName) {
		LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Backup server is in process Server Failure method.");
		//spawn players and dragons in backup server
		ArrayList<Integer> ids = new ArrayList<Integer>();
		ArrayList<UnitState> units = null;
		for(UnitState u : setupDragons.get(serverName)) {
			ids.add(u.unitID);
		}
		for(UnitState u : setupPlayers.get(serverName)) {
			ids.add(u.unitID);
		}
		LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Process Server Failure. Start Ids: "+ ids.size());
		units = getUnitsFromUnitIds(ids);
		LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Process Server Failure. Start Units: "+ units.size());
		for(UnitState u: units) {
			if(u.unitType == UnitType.Dragon){
				LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Process Server Failure. Start Dragon: "+ u.unitID);
				Dragon d = new Dragon(u);
			}
			if(u.unitType == UnitType.Player) {
				LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Process Server Failure. Start Player: "+ u.unitID);
				Player p = new Player(u);
			}			
		}			
		//send information of failure to main game server and backup game server
		Message msg = new Message();
		int id = ++localMessageCounter;
		msg.put("id", id);
		msg.put("serverName", serverName);
		msg.put("origin", myServerName);
		msg.put("request", MessageType.changeServer);
		msg.setMessageType(MessageType.changeServer);		
		try {
			gameServerHandle.onMessageReceived(msg);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private synchronized ArrayList<UnitState> getUnitsFromUnitIds(ArrayList<Integer> ids) {
		ArrayList<UnitState> unitsToSpawn = new ArrayList<UnitState>();
		for(int i=0;i<MAP_WIDTH;i++) {
			for(int j=0;j<MAP_HEIGHT;j++) {
				if(map[i][j] != null && ids.contains(map[i][j].unitID) && !unitsToSpawn.contains(map[i][j])) {
					LoggingService.log(MessageType.changeServer, "Map ["+i+"]"+"["+j+"] = "+map[i][j].unitID );
					unitsToSpawn.add(map[i][j]);
				}
			}
		}
		return unitsToSpawn;
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
			myServerName = args[0]; 
			LocateRegistry.createRegistry(1099);
			
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		MessagingHandler reqHandlingServer = getRequestHandlingServer();
        MessagingHandler reqHandlingServerStub;
		try {
			reqHandlingServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(reqHandlingServer, 0);
			Registry registry = LocateRegistry.getRegistry();
	        registry.rebind(myServerName, reqHandlingServerStub);
	        LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Registered remote object for backup server.");
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public synchronized Message onMessageReceived(Message message) throws RemoteException {
		if(message.get("type").equals(MessageType.setup)) {
			LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup server received player and dragon setup message from gameServer.");
			try {
				Registry remoteRegistry  = LocateRegistry.getRegistry(gameServerIp, port);
				gameServerHandle= (MessagingHandler) remoteRegistry.lookup("gameServer");
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup server: place initial players and dragons on map.");
			setupDragons = (HashMap<String, ArrayList<UnitState>>) message.get("dragons");
			//k: serverName v: arraylist of dragon units
			setupDragons.forEach((k,v)->{
				v.forEach(u -> placeUnitOnMap(u));
			});
			setupPlayers = (HashMap<String, ArrayList<UnitState>>) message.get("players");
			setupPlayers.forEach((k,v)->{
				v.forEach(u -> placeUnitOnMap(u));
			});
		}
		if(message.get("type").equals(MessageType.changeServer)) {
			//get the name of the backup server
			String backupServerName = message.get("serverName").toString();
			String backupServerIpAddress = message.get("ipAddress").toString();
			
			//change RMI to call backup server method
			try {
				MessagingHandler backupGameServerHandle = 
				(MessagingHandler) LocateRegistry.getRegistry(backupServerIpAddress).lookup(backupServerName);
				synchronized(this) {
					gameServerHandle = backupGameServerHandle;
				}
			} catch (NotBoundException e) {
				e.printStackTrace();
			} 
		}
		return null;
	}
	
	@Override
	public synchronized void onSynchronizationMessageReceived(Message message) throws RemoteException {
		if(message.get("request").equals(MessageType.sync)) {
			UnitState u;
			int x = 0, y = 0;
			MessageType messageType = (MessageType)message.get("type");
			String text = "["+ myServerName+"]"+"Received sync message for action "+messageType;
			LoggingService.log(MessageType.sync, "");
			switch(messageType) {
			case spawnUnit:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					map[u.x][u.y] = u;
				}
				break;
			case dealDamage:
				x = (Integer)message.get("x");
				y = (Integer)message.get("y");
				Integer damagePoints = (Integer)message.get("damagePoints");
				u = (UnitState) message.get("unit");
				u = this.getUnit(x, y);
				if (u != null) {
					u.adjustHitPoints(-damagePoints );
				}
				break;
			case healDamage:
				u = (UnitState) message.get("unit");
				x = (int)message.get("x");
				y = (int)message.get("y");
				u = this.getUnit(x, y);
				if (u != null)
					u.adjustHitPoints((Integer)message.get("healedPoints") );
				break;
			case moveUnit:
				UnitState oldUnit = (UnitState) message.get("oldUnit");
				int toX = Integer.parseInt(message.get("toX").toString());
				int toY = Integer.parseInt(message.get("toY").toString());
				moveUnit(oldUnit, toX, toY);
				break;
			case putUnit:
				break;
			case removeUnit:
				x = (Integer)message.get("removeX");
				y = (Integer)message.get("removeY");
				removeUnit(x,y);
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
	
	public synchronized Message onHeartbeatReceived(Message msg) {		
		Message reply = null;	
		String name = msg.get("serverName").toString();
		String text = "["+ myServerName+"]"+"Received heartbeat from " +  name;
		LoggingService.log(MessageType.heartbeat, text);
		if(reqFailoverService == null) {
			LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Instantiate failover service.");
			reqFailoverService = new RequestServerFailoverService(this);
			setTimeSinceHeartbeat(new HashMap<String, Long>());
		}		
		if(!getTimeSinceHeartbeat().containsKey(name)) {
			getTimeSinceHeartbeat().put(name, System.currentTimeMillis());
		}
		else {
			getTimeSinceHeartbeat().put(name, System.currentTimeMillis());
		}
		reply = new Message();
		reply.put("serverName", myServerName);
		return reply;
	}
	public synchronized HashMap<String, Long> getTimeSinceHeartbeat() {
		return timeSinceHeartbeat;
	}
	public synchronized void setTimeSinceHeartbeat(HashMap<String, Long> timeSinceHeartbeat) {
		BackupRequestHandlingServer.timeSinceHeartbeat = timeSinceHeartbeat;
	}
	public synchronized void removeTimeSinceHeartbeatServer(String name) {
		timeSinceHeartbeat.remove(name);
	}
}
