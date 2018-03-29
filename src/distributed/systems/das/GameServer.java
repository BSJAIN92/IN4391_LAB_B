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
import distributed.systems.das.services.HeartbeatService;
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;

public class GameServer implements MessagingHandler {

	//updates to the following members must be synchronized. At any give time only one thread can update these values.
	private UnitState[][] map;
	private static int lastUnitId = 0;
	private static int syncMessageId = 0;
	
	private static String myServerName;
	private static GameServer battlefield;
	private static int numberOfReqServers;
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;  
	private static HashMap<String, MessagingHandler> requestHandlingServers = new HashMap<String, MessagingHandler>();
	private static HashMap<String, ArrayList<UnitState>> setupDragons = new HashMap<String, ArrayList<UnitState>>();
	private static HashMap<String, ArrayList<UnitState>> setupPlayers = new HashMap<String, ArrayList<UnitState>>();
	private static HashMap<String, String> serverIps;
	private static int numberOfDragons;
	
	private static HeartbeatService heartbeat;
	
	private GameServer() {
		map = new UnitState[MAP_WIDTH][MAP_HEIGHT];
		createIpMap();
	}
	
	public static GameServer getBattleField() {
		if(battlefield == null) {
			battlefield = new GameServer();
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
	
	
	/**
	 * Puts a new unit at the specified position. First, it
	 * checks whether the position is empty, if not, it
	 * does nothing.
	 * In addition, the unit is also put in the list of known units.
	 * 
	 * @param unit is the actual unit being spawned 
	 * on the specified position.
	 * @param x is the x position.
	 * @param y is the y position.
	 * @return true when the unit has been put on the 
	 * specified position.
	 */
	private synchronized UnitState spawnUnit(int x, int y, int unitId, UnitType unitType, String origin)
	{
		UnitState unit = null;
		//acquiring the intrinsic lock associated with 'this' instance of GameServer. 
		//As the GameServer is a singleton, all threads operate on the sole instance of the class. 
		synchronized (this) {
			if (map[x][y] == null) {
				unit = new UnitState(x, y, unitId, unitType, origin);
				unit.setPosition(x, y);
				map[x][y] = unit;
			}
		}
		return unit;
	}

	/**
	 * Get a unit from a position.
	 * 
	 * @param x position.
	 * @param y position.
	 * @return the unit at the specified position, or return
	 * null if there is no unit at that specific position.
	 */
	public synchronized UnitState getUnit(int x, int y)
	{
		//assert x >= 0 && x < map.length;
		//assert y >= 0 && x < map[0].length;

		return map[x][y];
	}

	private synchronized boolean putUnit(UnitState unit, int x, int y)
	{
		if (map[x][y] != null)
			return false;

		synchronized(this) {
			unit.setPosition(x, y);
			map[x][y] = unit;
		}
		return true;
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
	
	
	@Override
	public synchronized  Message onMessageReceived(Message msg) throws RemoteException {
		String text = "["+myServerName+"]"+"onMessageReceived: "+msg.get("id");
		LoggingService.log(msg.getMessageType(), text);
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
				
				int rx, ry, attempt = 0;
				do {
					rx = (int)(Math.random() * MAP_WIDTH);
					ry = (int)(Math.random() * MAP_HEIGHT);
					attempt++;
				} while (battlefield.getUnit(rx, ry) != null && attempt < 100);
				
				int newUid = Integer.parseInt(msg.get("unitId").toString());
				unit = this.spawnUnit(rx, ry, newUid, UnitType.Player, origin);
				if(unit != null) {
					LoggingService.log(MessageType.spawnUnit, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
					reply = new Message();
					reply.put("id", msg.get("id"));
					reply.put("unit", unit);
					
					//tell all other request handling servers that a new unit has spawned
					sync = new Message();
					sync.put("id", getNewSyncMessageId());
					sync.put("request", MessageType.sync);
					sync.put("type", MessageType.spawnUnit);
					sync.put("x", rx);	
					sync.put("y", ry);
					sync.put("unit", unit);
				}	
				break;
			case getUnit:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				reply.put("id", msg.get("id"));
				reply.put("unit", battlefield.getUnit(x, y));
				break;
			}
			case getType:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				reply.put("id", msg.get("id"));
				
				if(battlefield.getUnit(x, y) != null) {
					UnitType unitType = battlefield.getUnit(x, y).unitType;
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
				unit = battlefield.getUnit(x, y);
				if (unit != null) {
					LoggingService.log(MessageType.dealDamage, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
					unit.adjustHitPoints(-damagePoints );
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
				}
				break;
			}
			case healDamage:
			{
				int x = (Integer)msg.get("toX");
				int y = (Integer)msg.get("toY");
				int healPoints = (Integer)msg.get("healedPoints"); 
				unit = battlefield.getUnit(x, y);
				if (unit != null) {
					LoggingService.log(MessageType.healDamage, "["+myServerName+"]"+"Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
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
				}				
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
	
	public Message onHeartbeatReceived(Message msg) {
		return null;
	}
	
	public static void main(String args[]) throws NotBoundException {
		myServerName = args[0];
		int port = 1099;
		numberOfReqServers = Integer.parseInt(args[1]);
		numberOfDragons = Integer.parseInt(args[2]);
		String bkname = "backupServerGame";
		try {
			LocateRegistry.createRegistry(port);
		}
		catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
		}
			
         MessagingHandler gameServer = new GameServer();
         MessagingHandler gameServerStub;
		try {
			gameServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(gameServer, 0);
			Registry registry = LocateRegistry.getRegistry();
	         registry.rebind(myServerName, gameServerStub);
	         LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"game server registry created.");
	          
	         Registry bkRegistry  = LocateRegistry.getRegistry(serverIps.get(bkname), port);
	         MessagingHandler backupGameServerHandle = (MessagingHandler) bkRegistry.lookup(bkname);
		     heartbeat = new HeartbeatService(backupGameServerHandle, myServerName);
		     LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Backup request server handle obtained.");
	            
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
	            requestHandlingServers.put("backupServerGame", (MessagingHandler) bkRegistry.lookup("backupServerGame"));
	            
	            //initialize battlefield
	            battlefield = GameServer.getBattleField();            
	            
	            //initialize dragons
	            initializeDragons(numberOfDragons);

	            //send this info to respective servers
	            requestHandlingServers.forEach((serverName, handler) -> {
	            	try {
						Message setupMessage = new Message();
						setupMessage.put("id", 0);
						setupMessage.put("dragons", setupDragons);
						setupMessage.put("type", MessageType.setup);
						LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"Game server: send player and dragon setup message to "+serverName);
						handler.onMessageReceived(setupMessage);
					}catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});  

		} catch (RemoteException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
  		} 
	
	private static void initializeDragons(int dragonCount) {
		LoggingService.log(MessageType.setup, "["+ myServerName+"]"+"game server: Initialize dragons.");
		for(int id = 0; id < dragonCount; id++)
		{
			int x, y;
			do {
				x = (int)(Math.random() * MAP_WIDTH);
				y = (int)(Math.random() * MAP_HEIGHT);
			} while (battlefield.getUnit(x, y) != null);

			// We were successful in finding a spot at x, y
			
			int m = id % numberOfReqServers; 
			String serverName = "reqServer_"+(m+1);
			UnitState u = new UnitState(x, y, getNewUnitId(), UnitType.Dragon, serverName);
			if(!setupDragons.containsKey(serverName)) {
				setupDragons.put(serverName, new ArrayList<UnitState>());
			}
			setupDragons.get(serverName).add(u);	
		}
	}
	
	@Override
	public void onSynchronizationMessageReceived(Message message) throws RemoteException {
		// TODO Auto-generated method stub
		
	}
}
