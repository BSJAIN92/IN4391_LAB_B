package distributed.systems.das.units;

import java.io.Serializable;
import java.net.Socket;
import java.rmi.RemoteException;

import distributed.systems.das.GameServer;
import distributed.systems.das.GameState;
import distributed.systems.das.Message;
import distributed.systems.das.services.MessagingHandler;

public class Player extends Unit implements Runnable, Serializable, MessagingHandler {
	/* Reaction speed of the player
	 * This is the time needed for the player to take its next turn.
	 * Measured in half a seconds x GAME_SPEED.
	 */
	protected int timeBetweenTurns;
	public static final int MIN_TIME_BETWEEN_TURNS = 2;
	public static final int MAX_TIME_BETWEEN_TURNS = 7;
	public static final int MIN_HITPOINTS = 20;
	public static final int MAX_HITPOINTS = 10;
	public static final int MIN_ATTACKPOINTS = 1;
	public static final int MAX_ATTACKPOINTS = 10;
	
	private static final long serialVersionUID = -3507657523648823999L;
	
	public Player(int x, int y) {
		
		/* Initialize the hitpoints and attackpoints */
		super((int)(Math.random() * (MAX_HITPOINTS - MIN_HITPOINTS) + MIN_HITPOINTS), (int)(Math.random() * (MAX_ATTACKPOINTS - MIN_ATTACKPOINTS) + MIN_ATTACKPOINTS));
		/* Create a random delay */
		timeBetweenTurns = (int)(Math.random() * (MAX_TIME_BETWEEN_TURNS - MIN_TIME_BETWEEN_TURNS)) + MIN_TIME_BETWEEN_TURNS;
		if (!spawn(x, y))
			return; // We could not spawn on the battlefield
		runnerThread = new Thread(this);
		runnerThread.start();
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		Direction direction;
		UnitType adjacentUnitType;
		int targetX = 0, targetY = 0;
		
		this.running = true;

		while(GameState.getRunningState() && this.running) {
			try {			
				/* Sleep while the player is considering its next move */
				Thread.currentThread().sleep((int)(timeBetweenTurns * 500 * GameState.GAME_SPEED));

				/* Stop if the player runs out of hitpoints */
				if (getHitPoints() <= 0)
					break;

				// Randomly choose one of the four wind directions to move to if there are no units present
				direction = Direction.values()[ (int)(Direction.values().length * Math.random()) ];
				adjacentUnitType = UnitType.undefined;

				switch (direction) {
					case up:
						if (this.getY() <= 0)
							// The player was at the edge of the map, so he can't move north and there are no units there
							continue;
						
						targetX = this.getX();
						targetY = this.getY() - 1;
						break;
					case down:
						if (this.getY() >= GameServer.MAP_HEIGHT - 1)
							// The player was at the edge of the map, so he can't move south and there are no units there
							continue;

						targetX = this.getX();
						targetY = this.getY() + 1;
						break;
					case left:
						if (this.getX() <= 0)
							// The player was at the edge of the map, so he can't move west and there are no units there
							continue;

						targetX = this.getX() - 1;
						targetY = this.getY();
						break;
					case right:
						if (this.getX() >= GameServer.MAP_WIDTH - 1)
							// The player was at the edge of the map, so he can't move east and there are no units there
							continue;

						targetX = this.getX() + 1;
						targetY = this.getY();
						break;
				}

				// Get what unit lies in the target square
				adjacentUnitType = this.getType(targetX, targetY);
				
				switch (adjacentUnitType) {
					case undefined:
						// There is no unit in the square. Move the player to this square
						this.moveUnit(targetX, targetY);
						break;
					case player:
						// There is a player in the square, attempt a healing
						this.healDamage(targetX, targetY, getAttackPoints());
						break;
					case dragon:
						// There is a dragon in the square, attempt a dragon slaying
						this.dealDamage(targetX, targetY, getAttackPoints());
						break;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	public void onMessageReceived(Message message) throws RemoteException {
		//messageList.put((Integer)message.get("id"), message);	
	}
}
