package distributed.systems.das;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

import distributed.systems.das.common.Message;
import distributed.systems.das.common.MessageType;
import distributed.systems.das.common.UnitState;
import distributed.systems.das.common.UnitType;
import distributed.systems.das.services.GameServerFailoverService;
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.services.RequestServerFailoverService;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;

public class BackupGameServer implements MessagingHandler {

	//updates to the following members must be synchronized. At any give time only one thread can update these values.
	private UnitState[][] map;
	private static int lastUnitId = 0;
	private static int syncMessageId = 0;
	
	private static int localMessageCounter;
	private static String myServerName;
	private static BackupGameServer battlefield;
	private static int numberOfReqServers;
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;  
	private static HashMap<String, MessagingHandler> requestHandlingServers = new HashMap<String, MessagingHandler>();
	private static HashMap<String, ArrayList<UnitState>> setupDragons = new HashMap<String, ArrayList<UnitState>>();
	private static HashMap<String, ArrayList<UnitState>> setupPlayers = new HashMap<String, ArrayList<UnitState>>();
	private static HashMap<String, String> serverIps;
	private static MessagingHandler gameServerHandle;
	private static GameServerFailoverService reqFailoverService;
	private static HashMap<String, Long> timeSinceHeartbeat;
	
	private BackupGameServer() {
		map = new UnitState[MAP_WIDTH][MAP_HEIGHT];
		createIpMap();
	}
	
	public static BackupGameServer getBattleField() {
		if(battlefield == null) {
			battlefield = new BackupGameServer();
		}
		return battlefield;
	}
	
	private void createIpMap() {
		serverIps = new HashMap<String, String>();
		//File ipFile = new File("ipAddresses.txt");
		File ipFile = new File("/home/ec2-user/ipAddresses.txt");
		try {
			BufferedReader in = new BufferedReader(new FileReader(ipFile));
			String line = null;
			while((line = in.readLine()) != null) {
				String[] s = line.split(" ");
				if(!s[0].equals(myServerName)) {
					serverIps.put(s[0], s[1]);
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * Returns a new unique unit ID.
	 * @return int: a new unique unit ID.
	 */
	public synchronized static int getNewUnitId() {
		return ++lastUnitId;
	}
	/**
	 * Returns a new unique unit ID.
	 * @return int: a new unique unit ID.
	 */
	public synchronized static int getNewSyncMessageId() {
		return ++syncMessageId;
	}
	
	private UnitState spawnUnit(int x, int y, UnitType unitType, String origin)
	{
		UnitState unit = null;
		//acquiring the intrinsic lock associated with 'this' instance of GameServer. 
		//As the GameServer is a singleton, all threads operate on the sole instance of the class. 
		synchronized (this) {
			if (map[x][y] == null) {
				unit = new UnitState(x, y, getNewUnitId(), unitType, origin);
				map[x][y] = unit;
				map[x][y].setPosition(x, y);
			}
		}
		return unit;
	}

	public UnitState getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
	}

	private synchronized boolean putUnit(UnitState unit, int x, int y)
	{
		synchronized(this) {
			if (map[x][y] != null)
				return false;
			map[x][y] = unit;
			unit.setPosition(x, y);
			return true;
		}
	}
	
	private synchronized UnitState moveUnit(UnitState unit, int newX, int newY)
	{
		synchronized(this) {
			if (newX >= 0 && newX < MAP_WIDTH)
				if (newY >= 0 && newY < MAP_HEIGHT)
					if (map[newX][newY] == null) {
						removeUnit(unit.x, unit.y);
						unit.x = newX;
						unit.y = newY;
						map[newX][newY] = unit;
						return map[newX][newY];
					}
			return null;
		}
	}

	private synchronized void removeUnit(int x, int y)
	{
		synchronized(this) {
			map[x][y] = null;
		}
	}
	
	private synchronized void placeUnitOnMap(UnitState u) {
		try {
			LoggingService.log(MessageType.setup, "placed "+u.x+" "+u.y+"on map.");
			battlefield.map[u.x][u.y] = u;
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return;
	}
	
	@Override
	public synchronized Message onMessageReceived(Message msg) throws RemoteException {
		if(msg.get("type").equals(MessageType.setup)) {
			LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup server received player and dragon setup message from gameServer.");
			try {
				Registry remoteRegistry  = LocateRegistry.getRegistry(serverIps.get("gameServer"), 1099);
				gameServerHandle= (MessagingHandler) remoteRegistry.lookup("gameServer");
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup server: place initial players and dragons on map.");
			setupDragons = (HashMap<String, ArrayList<UnitState>>) msg.get("dragons");
			//k: serverName v: arraylist of dragon units
			setupDragons.forEach((k,v)->{
				v.forEach(u -> placeUnitOnMap(u));
			});
			/*setupPlayers = (HashMap<String, ArrayList<UnitState>>) msg.get("players");
			setupPlayers.forEach((k,v)->{
				v.forEach(u -> placeUnitOnMap(u));
			});*/
			return null;
		}
		String text = "["+myServerName+"]"+"onMessageReceived: "+msg.get("id");
		//LoggingService.log(msg.getMessageType(), text);
		Message reply = null;
		Message sync = null;
		String origin = (String)msg.get("origin");
		MessageType request = (MessageType)msg.get("request");
		UnitState unit;
		Integer toX;
		Integer toY;
		switch(request)
		{
			case spawnUnit:
				unit = this.spawnUnit((Integer)msg.get("x"), (Integer)msg.get("y"), (UnitType)msg.get("unitType"), origin);
				LoggingService.log(MessageType.spawnUnit, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
				reply = new Message();
				reply.put("id", msg.get("id"));
				reply.put("unit", unit);
				
				//tell all other request handling servers that a new unit has spawned
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.spawnUnit);
				sync.put("unit", unit);	
				break;
			case getUnit:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				reply.put("id", msg.get("id"));
				reply.put("unit", getUnit(x, y));
				break;
			}
			case getType:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				reply.put("id", msg.get("id"));
				
				if(getUnit(x, y) != null) {
					UnitType unitType = getUnit(x, y).unitType;
					if (unitType == UnitType.Player)
						reply.put("type", UnitType.Player);
					else if (unitType == UnitType.Dragon)
						reply.put("type", UnitType.Dragon);
				}
				else reply.put("type", UnitType.Undefined);
				break;
			}
			case dealDamage:
			{
				int x = (Integer)msg.get("toX");
				int y = (Integer)msg.get("toY");
				Integer damagePoints = (Integer)msg.get("damagePoints");
				unit = this.getUnit(x, y);
				LoggingService.log(MessageType.dealDamage, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
				if (unit != null) {
					unit.adjustHitPoints(-damagePoints );
				}
				
				//tell the player's request handling server (origin) that it's player's health has changed
				reply = new Message();
				reply.put("id", msg.get("id"));
				reply.put("unit", unit);
				
				
				//tell all other request handling servers (servers != origin) that a player's health has changed
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.dealDamage);
				sync.put("x", unit.x);	
				sync.put("y", unit.y);
				sync.put("damagePoints", damagePoints);
				break;
			}
			case healDamage:
			{
				int x = (Integer)msg.get("toX");
				int y = (Integer)msg.get("toY");
				int healPoints = (Integer)msg.get("healedPoints"); 
				unit = this.getUnit(x, y);
				LoggingService.log(MessageType.healDamage, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
				if (unit != null)
					unit.adjustHitPoints(healPoints);
				
				//tell the player's request handling server (origin) that it's player's health has changed
				reply = new Message();
				reply.put("id", msg.get("id"));
				reply.put("unit", unit);
				
				//tell all other request handling servers (servers != origin) that a player's health has changed
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.healDamage);
				sync.put("x", unit.x);
				sync.put("y", unit.y);
				sync.put("healedPoints", healPoints);				
				break;
			}
			case moveUnit:
				toX = (Integer)msg.get("toX");
				toY = (Integer)msg.get("toY");
				reply = new Message();
				boolean moveSuccess; 
				UnitState msgUnit = (UnitState)msg.get("unit");
				int a = msgUnit.x;
				int b = msgUnit.y;
				int uid = msgUnit.unitID;
				LoggingService.log(MessageType.moveUnit, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + msgUnit.unitID + "from server: " + origin);
				unit = this.moveUnit(msgUnit, toX, toY);
				moveSuccess = unit != null ? true : false;
				
				if(map[a][b] == null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+uid
							+" move from ["+a +","+b+"] old map is updated to null.");
				}
				else {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "XXXXIt is ["+map[a][b].x +","+map[a][b].y+"]");
				}
				if(map[toX][toY] != null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+uid
							+" move to ["+toX +","+toY+"] new map is not null. It is ["+map[toX][toY].x +","+map[toX][toY].y+"]");
				}
				
				//tell the player's request handling server (origin) that it's player's move request has been processed 
				reply.put("id", msg.get("id"));
				reply.put("moveSuccess", moveSuccess);
				reply.put("unit", unit);
				
				//tell all other request handling servers (servers != origin) that a player has moved if the move was successful
				if(moveSuccess) {
					sync = new Message();
					sync.put("id", getNewSyncMessageId());
					sync.put("request", MessageType.sync);
					sync.put("type", MessageType.moveUnit);
					sync.put("fromX", a);
					sync.put("fromY", b);
					sync.put("toX", unit.x);
					sync.put("toY", unit.y);
				}
				break;
			case removeUnit:
				this.removeUnit((Integer)msg.get("x"), (Integer)msg.get("y"));
				reply = new Message();
				
				//tell the player's request handling server (origin) that it's player's has been removed 
				reply.put("id", msg.get("id"));
				
				//tell all other request handling servers (servers != origin) that a player has been removed
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.removeUnit);
				sync.put("removeX", (Integer)msg.get("x"));
				sync.put("removeY", (Integer)msg.get("y"));
				
				break;
			case changeServer:
				// get new req handling server name, ip addr from the message
				String removeServerName = msg.get("serverName").toString();
				LoggingService.log(MessageType.changeServer, "["+myServerName+"]"+"Game server received change server from backup because server: "+ removeServerName + "failed.");
				//chande rmi handler
				requestHandlingServers.remove(removeServerName);
				break;				
		default:
			break;
		}
		
		//TODO Send sync message to Game Server backup and req server backup also
		if(sync != null) {
			for(String key : requestHandlingServers.keySet()) {
				if(!key.equals(origin)) {
					LoggingService.log(MessageType.sync, "["+myServerName+"]"+"send sync to: "+key);
					new SendMessages(sync, requestHandlingServers.get(key), MessageType.sync);
				}
			}
		}
		return reply;
	}
	
	public synchronized Message onHeartbeatReceived(Message msg) {
		Message reply = null;	
		String name = msg.get("serverName").toString();
		String text = "["+ myServerName+"]"+"Received heartbeat from " +  name;
		LoggingService.log(MessageType.heartbeat, text);
		if(reqFailoverService == null) {
			LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Instantiate failover service.");
			reqFailoverService = new GameServerFailoverService(this);
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
		BackupGameServer.timeSinceHeartbeat = timeSinceHeartbeat;
	}
	public synchronized void removeTimeSinceHeartbeatServer(String name) {
		timeSinceHeartbeat.remove(name);
	}
	
	public synchronized void processServerFailure() {
		LoggingService.log(MessageType.changeServer, "["+ myServerName+"]"+"Backup game server is in process Server Failure method.");
	}	
	
	public static void main(String args[]) throws NotBoundException {
		myServerName = args[0];
		int port = 1099;
		numberOfReqServers = Integer.parseInt(args[1]);
		try {
			LocateRegistry.createRegistry(port);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
            MessagingHandler gameServer = new BackupGameServer();
            MessagingHandler gameServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(gameServer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(myServerName, gameServerStub);
            LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"backup game server registry created.");
            
            //req handlers
            for(int i=0;i<numberOfReqServers;i++) {	
            	String s = "reqServer_"+(i+1);
            	Registry remoteRegistry  = LocateRegistry.getRegistry(serverIps.get(s), port);
    			MessagingHandler reqServerHandle = (MessagingHandler) remoteRegistry.lookup(s);
    			requestHandlingServers.put(s, reqServerHandle);
            }         
            //backup req handler
            Registry remoteRegistry  = LocateRegistry.getRegistry(serverIps.get("backupServerReq"), port);
            requestHandlingServers.put("backupServerReq", (MessagingHandler) remoteRegistry.lookup("backupServerReq"));
            
            //initialize battlefield
            battlefield = BackupGameServer.getBattleField();            
            
            
            
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public synchronized void onSynchronizationMessageReceived(Message message) throws RemoteException {
		if(message.get("request").equals(MessageType.sync)) {
			UnitState u;
			int x = 0, y = 0;
			MessageType messageType = (MessageType)message.get("type");
			String text = "["+ myServerName+"]"+"Received sync message for action "+messageType;
			LoggingService.log(MessageType.sync, text);
			switch(messageType) {
			case spawnUnit:
				u = (UnitState) message.get("unit");
				synchronized(this) {
					putUnit(u, u.x, u.y);
					if(map[u.x][u.y] != null) {
						LoggingService.log(MessageType.spawnUnit, "["+ myServerName+"]"+ 
								"Spawned new unit at map ["+map[u.x]+","+u.y+"]");
					}
					else {
						LoggingService.log(MessageType.spawnUnit, "XXXXXXXXXXx");
					}
				}
				break;
			case dealDamage:
				x = (Integer)message.get("x");
				y = (Integer)message.get("y");
				Integer damagePoints = (Integer)message.get("damagePoints");
				u = (UnitState) message.get("unit");
				u = this.getUnit(x, y);
				if (u != null) {
					synchronized(this) {
						u.adjustHitPoints(-damagePoints );
					}
				}
				break;
			case healDamage:
				u = (UnitState) message.get("unit");
				x = (int)message.get("x");
				y = (int)message.get("y");
				u = this.getUnit(x, y);
				if (u != null)
					synchronized(this) {
						u.adjustHitPoints((Integer)message.get("healedPoints") );
					}
				break;
			case moveUnit:
				int fromX = Integer.parseInt(message.get("fromX").toString());;
				int fromY = Integer.parseInt(message.get("fromY").toString());;
				int toX = Integer.parseInt(message.get("toX").toString());
				int toY = Integer.parseInt(message.get("toY").toString());
				LoggingService.log(MessageType.moveUnit, "YYYYYYYYYYYYYYYY "+ fromX+" "+fromY+  " "+toX+ " "+toY);
				UnitState oldUnit = getUnit(fromX, fromY);
				if(oldUnit == null) {
					LoggingService.log(MessageType.moveUnit, "YYYYYYYYYYYYYYYY "+ fromX+" "+fromY+  " "+toX+ " "+toY);
				}
				synchronized(this) {
					moveUnit(oldUnit, toX, toY);
				}
				if(map[fromX][fromY] == null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+oldUnit.unitID
							+" move from ["+fromX +","+fromY+"] old map is updated to null.");
				}
				else {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "XXXXIt is ["+map[fromX][fromY].x +","+map[fromX][fromY].y+"]");
				}
				if(map[toX][toY] != null) {
					LoggingService.log(MessageType.moveUnit,"["+ myServerName+"]"+ "moveRequest UnitId: "+oldUnit.unitID
							+" move to ["+toX +","+toY+"] new map is not null. It is ["+map[toX][toY].x +","+map[toX][toY].y+"]");
				}
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
}
