package distributed.systems.das;

import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

import distributed.systems.das.services.MessagingHandler;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;
import distributed.systems.das.units.Unit;
import distributed.systems.das.units.Unit.UnitType;

public class GameServer implements MessagingHandler {
	
	private Unit[][] map;
	private static GameServer battlefield;
	private static int numberOfReqServers = 3;
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;
	private ArrayList <Unit> units;  
	/* The last id that was assigned to an unit. This variable is used to
	 * enforce that each unit has its own unique id.
	 */
	private int lastUnitID = 0;
	private static HashMap<String, MessagingHandler> requestHandlingServers = new HashMap<String, MessagingHandler>();
	private static HashMap<String, ArrayList<Unit>> setup= new HashMap<String, ArrayList<Unit>>();
	
	private GameServer() {
		map = new Unit[MAP_WIDTH][MAP_HEIGHT];
		units = new ArrayList<>();
	}
	
	public static GameServer getBattleField() {
		if(battlefield == null) {
			new GameServer();
		}
		return battlefield;
	}
	
	/**
	 * Returns a new unique unit ID.
	 * @return int: a new unique unit ID.
	 */
	public synchronized int getNewUnitID() {
		return ++lastUnitID;
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
	private boolean spawnUnit(Unit unit, int x, int y)
	{
		synchronized (this) {
			if (map[x][y] != null)
				return false;
	
			map[x][y] = unit;
			unit.setPosition(x, y);
		}
		units.add(unit);

		return true;
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
	private synchronized boolean putUnit(Unit unit, int x, int y)
	{
		if (map[x][y] != null)
			return false;

		map[x][y] = unit;
		unit.setPosition(x, y);

		return true;
	}

	/**
	 * Get a unit from a position.
	 * 
	 * @param x position.
	 * @param y position.
	 * @return the unit at the specified position, or return
	 * null if there is no unit at that specific position.
	 */
	public Unit getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
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
	private synchronized boolean moveUnit(Unit unit, int newX, int newY)
	{
		int originalX = unit.getX();
		int originalY = unit.getY();

		if (unit.getHitPoints() <= 0)
			return false;

		if (newX >= 0 && newX < GameServer.MAP_WIDTH)
			if (newY >= 0 && newY < GameServer.MAP_HEIGHT)
				if (map[newX][newY] == null) {
					if (putUnit(unit, newX, newY)) {
						map[originalX][originalY] = null;
						return true;
					}
				}

		return false;
	}

	/**
	 * Remove a unit from a specific position and makes the unit disconnect from the server.
	 * 
	 * @param x position.
	 * @param y position.
	 */
	private synchronized void removeUnit(int x, int y)
	{
		Unit unitToRemove = this.getUnit(x, y);
		if (unitToRemove == null)
			return; // There was no unit here to remove
		map[x][y] = null;
		unitToRemove.disconnect();
		units.remove(unitToRemove);
	}

	@Override
	public void onMessageReceived(Message msg) throws RemoteException {
		Message reply = null;
		String origin = (String)msg.get("origin");
		MessageRequest request = (MessageRequest)msg.get("request");
		Unit unit;
		switch(request)
		{
			case spawnUnit:
				this.spawnUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				break;
			case putUnit:
				this.putUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				break;
			case getUnit:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", msg.get("id"));
				// Get the unit at the specific location
				reply.put("unit", getUnit(x, y));
				break;
			}
			case getType:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", msg.get("id"));
				if (getUnit(x, y) instanceof Player)
					reply.put("type", UnitType.player);
				else if (getUnit(x, y) instanceof Dragon)
					reply.put("type", UnitType.dragon);
				else reply.put("type", UnitType.undefined);
				break;
			}
			case dealDamage:
			{
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				unit = this.getUnit(x, y);
				if (unit != null)
					unit.adjustHitPoints( -(Integer)msg.get("damage") );
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				break;
			}
			case healDamage:
			{
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				unit = this.getUnit(x, y);
				if (unit != null)
					unit.adjustHitPoints( (Integer)msg.get("healed") );
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				break;
			}
			case moveUnit:
				reply = new Message();
				this.moveUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", msg.get("id"));
				break;
			case removeUnit:
				this.removeUnit((Integer)msg.get("x"), (Integer)msg.get("y"));
				return;
		}
		if (reply != null)
			sendMessage(reply, origin);
	}
	/**
	 * Close down the battlefield. Unregisters
	 * the serverSocket so the program can 
	 * actually end.
	 */ 
	public synchronized void shutdown() {
		// Remove all units from the battlefield and make them disconnect from the server
		for (Unit unit : units) {
			unit.disconnect();
			unit.stopRunnerThread();
		}
	}
	
	private void sendMessage(Message reply, String origin) {
		
	}
	
	public static void main(String args[]) throws NotBoundException {
		int port = 1099;
		int numberOfReqHandlers = 2;
		try {
			LocateRegistry.createRegistry(port);
			String name = "GameServer";
            MessagingHandler gameServer = new GameServer();
            MessagingHandler gameServerStub = (MessagingHandler) UnicastRemoteObject.exportObject(gameServer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, gameServerStub);
            
            for(int i=0;i<numberOfReqHandlers;i++) {
            	
            	String s = "ReqServer"+(i+1);
            	Registry remoteRegistry  = LocateRegistry.getRegistry(args[0], port);
    			MessagingHandler reqServerHandle = (MessagingHandler) remoteRegistry.lookup(s);
    			requestHandlingServers.put(s, reqServerHandle);
            }
            //initialize battlefield
            battlefield = GameServer.getBattleField();
            
            //initialize dragons
            initializeDragons();
            
            //initialize players
            initializePlayers(6);
            
            
            
            //send this info to respective servers
            
            
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void initializeDragons() {
		// TODO Auto-generated method stub
		
	}

	private static void initializePlayers(int playerCount) {
		/* Initialize a random number of players (between [MIN_PLAYER_COUNT..MAX_PLAYER_COUNT] */
		//int playerCount = (int)((MAX_PLAYER_COUNT - MIN_PLAYER_COUNT) * Math.random() + MIN_PLAYER_COUNT);
		for(int i = 0; i < playerCount; i++)
		{
			/* Once again, pick a random spot */
			int x, y, attempt = 0;
			do {
				x = (int)(Math.random() * MAP_WIDTH);
				y = (int)(Math.random() * MAP_HEIGHT);
				attempt++;
			} while (battlefield.getUnit(x, y) != null && attempt < 10);

			// We were successful
			if (attempt < 10)
			{
				int m = i % numberOfReqServers; 
				addUnitIds(i, "reqServer"+(m+1));					
			}		
		}
		for(int i=0;i<numberOfReqServers;i++) {
			Message setupMsg = new Message();
			setupMsg.put("id", 0);
			setupMsg.put("setup", setup.get("reqServer"+(i+1)));
			setupMsg.setMessageType(MessageRequest.setup);
			try {
				requestHandlingServers.get("reqServer"+(i+1)).onMessageReceived(setupMsg);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static void addUnitIds(int i, String serverName) {
		Unit u = new Unit()
		if(!setup.containsKey(serverName)) {
			setup.put(serverName, new ArrayList<>());
		}
		setup.get(serverName).add(i);
	}
}
