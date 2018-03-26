package distributed.systems.das;

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
import distributed.systems.das.services.LoggingService;
import distributed.systems.das.services.MessagingHandler;

public class GameServer implements MessagingHandler {

	//updates to the following members must be synchronized. At any give time only one thread can update these values.
	private UnitState[][] map;
	private static int lastUnitId = 0;
	private static int syncMessageId = 0;
	
	private static GameServer battlefield;
	private static int numberOfReqServers;
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;  
	private static HashMap<String, MessagingHandler> requestHandlingServers = new HashMap<String, MessagingHandler>();
	private static HashMap<String, ArrayList<UnitState>> setupDragons = new HashMap<String, ArrayList<UnitState>>();
	private static HashMap<String, ArrayList<UnitState>> setupPlayers = new HashMap<String, ArrayList<UnitState>>();
	
	private GameServer() {
		map = new UnitState[MAP_WIDTH][MAP_HEIGHT];
	}
	
	public static GameServer getBattleField() {
		if(battlefield == null) {
			battlefield = new GameServer();
		}
		return battlefield;
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

	/**
	 * Get a unit from a position.
	 * 
	 * @param x position.
	 * @param y position.
	 * @return the unit at the specified position, or return
	 * null if there is no unit at that specific position.
	 */
	public UnitState getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
	}

	/**
	 * Put a unit at the specified position. First, it
	 * checks whether the position is empty, if not, it
	 * does nothing.
	 * 
	 * @param unit is the actual unit being put 
	 * on the specified position.
	 * @param x is the x position.
	 * @param y is the y position.
	 * @return true when the unit has been put on the 
	 * specified position.
	 */
	private synchronized boolean putUnit(UnitState unit, int x, int y)
	{
		if (map[x][y] != null)
			return false;

		map[x][y] = unit;
		//unit.setPosition(x, y);
		return true;
	}
	
	/**
	 * Move the specified unit a certain number of steps.
	 * 
	 * @param unit is the unit being moved.
	 * @param deltax is the delta in the x position.
	 * @param deltay is the delta in the y position.
	 * 
	 * @return true on success.
	 */
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

	/**
	 * Remove a unit from a specific position and makes the unit disconnect from the server.
	 * 
	 * @param x position.
	 * @param y position.
	 */
	private synchronized void removeUnit(int x, int y)
	{
		UnitState unitToRemove = this.getUnit(x, y);
		if (unitToRemove == null)
			return; // There was no unit here to remove
		map[x][y] = null;
	}
	
	
	@Override
	public Message onMessageReceived(Message msg) throws RemoteException {
		String text = "onMessageReceived: "+msg.get("id");
		LoggingService.log(msg.getMessageType(), text);
		Message reply = null;
		Message sync = null;
		String origin = (String)msg.get("origin");
		MessageType request = (MessageType)msg.get("request");
		UnitState unit;
		switch(request)
		{
			case spawnUnit:
				unit = this.spawnUnit((Integer)msg.get("x"), (Integer)msg.get("y"), (UnitType)msg.get("unitType"), origin);
				LoggingService.log(MessageType.spawnUnit, "Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
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
				unit = this.getUnit(x, y);
				LoggingService.log(MessageType.spawnUnit, "Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
				if (unit != null)
					unit.adjustHitPoints(-(Integer)msg.get("damagePoints") );
				
				//tell the player's request handling server (origin) that it's player's health has changed
				reply = new Message();
				reply.put("id", msg.get("id"));
				reply.put("unit", unit);
				
				
				//tell all other request handling servers (servers != origin) that a player's health has changed
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.dealDamage);
				sync.put("unit", unit);	
				break;
			}
			case healDamage:
			{
				int x = (Integer)msg.get("toX");
				int y = (Integer)msg.get("toY");
				unit = this.getUnit(x, y);
				LoggingService.log(MessageType.spawnUnit, "Game server received "+ request +" request for unitId " + unit.unitID + "from server: " + origin);
				if (unit != null)
					unit.adjustHitPoints((Integer)msg.get("healedPoints") );
				
				//tell the player's request handling server (origin) that it's player's health has changed
				reply = new Message();
				reply.put("id", msg.get("id"));
				reply.put("unit", unit);
				
				//tell all other request handling servers (servers != origin) that a player's health has changed
				sync = new Message();
				sync.put("id", getNewSyncMessageId());
				sync.put("request", MessageType.sync);
				sync.put("type", MessageType.healDamage);
				sync.put("unitHealer", unit);
				
				break;
			}
			case moveUnit:
				reply = new Message();
				boolean moveSuccess; 
				UnitState msgUnit = (UnitState)msg.get("unit");
				LoggingService.log(MessageType.moveUnit, "Game server received "+ request +" request for unitId " + msgUnit.unitID + "from server: " + origin);
				unit = this.moveUnit(msgUnit, (Integer)msg.get("toX"), (Integer)msg.get("toY"));
				moveSuccess = unit != null ? true : false;
				
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
					sync.put("oldUnit", msgUnit);
					sync.put("newUnit", unit);
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
				LoggingService.log(MessageType.changeServer, "Game server received change server from backup because server: "+ removeServerName + "failed.");
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
					LoggingService.log(MessageType.sync, "send sync to: "+key);
					requestHandlingServers.get(key).onSynchronizationMessageReceived(sync);
				}
			}
		}
		return reply;
	}
	
	public Message onHeartbeatReceived(Message msg) {
		return null;
	}
	
	public static void main(String args[]) throws NotBoundException {
		String name = args[0];
		String ip = "localhost";
		int port = 1099;
		numberOfReqServers = Integer.parseInt(args[1]);
		try {
			//LocateRegistry.createRegistry(port);
            MessagingHandler gameServer = new GameServer();
            MessagingHandler gameServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(gameServer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, gameServerStub);
            LoggingService.log(MessageType.setup, "game server registry created.");
            
            //req handlers
            for(int i=0;i<numberOfReqServers;i++) {	
            	String s = "reqServer"+(i+1);
            	Registry remoteRegistry  = LocateRegistry.getRegistry(ip, port);
    			MessagingHandler reqServerHandle = (MessagingHandler) remoteRegistry.lookup(s);
    			requestHandlingServers.put(s, reqServerHandle);
            }
            
            //backup req handler
            Registry remoteRegistry  = LocateRegistry.getRegistry(ip, port);
            requestHandlingServers.put("backupServerReq", (MessagingHandler) remoteRegistry.lookup("backupServerReq"));
            
            //initialize battlefield
            battlefield = GameServer.getBattleField();            
            
            //initialize dragons
            initializeDragons(2);
            
            //initialize players
            initializePlayers(6, 2);
            
            //send this info to respective servers
            requestHandlingServers.forEach((serverName, handler) -> {
				try {
					Message setupMessage = new Message();
					setupMessage.put("id", 0);
					setupMessage.put("players", setupPlayers);
					setupMessage.put("dragons", setupDragons);
					setupMessage.put("type", MessageType.setup);
					LoggingService.log(MessageType.setup, "Game server: send player and dragon setup message to "+serverName);
					handler.onMessageReceived(setupMessage);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
            
            
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void initializePlayers(int playerCount, int dragonCount) {
		LoggingService.log(MessageType.setup, "game server: Initialize players");
		for(int id = dragonCount; id < playerCount; id++)
		{
			int x, y, attempt = 0;
			do {
				x = (int)(Math.random() * MAP_WIDTH);
				y = (int)(Math.random() * MAP_HEIGHT);
				attempt++;
			} while (battlefield.getUnit(x, y) != null && attempt < 10);

			// We were successful
			if (attempt < 10)
			{
				int m = id % numberOfReqServers; 
				String serverName = "reqServer"+(m+1);
				UnitState u = new UnitState(x, y, getNewUnitId(), UnitType.Player, serverName);
				if(!setupPlayers.containsKey(serverName)) {
					setupPlayers.put(serverName, new ArrayList<UnitState>());
				}
				setupPlayers.get(serverName).add(u);
			}		
		}
	}
	
	private static void initializeDragons(int dragonCount) {
		LoggingService.log(MessageType.setup, "game server: Initialize dragons.");
		for(int id = 0; id < dragonCount; id++)
		{
			int x, y;
			do {
				x = (int)(Math.random() * MAP_WIDTH);
				y = (int)(Math.random() * MAP_HEIGHT);
			} while (battlefield.getUnit(x, y) != null);

			// We were successful in finding a spot at x, y
			
			int m = id % numberOfReqServers; 
			String serverName = "reqServer"+(m+1);
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
