package simulateGame;

import distributed.systems.das.GameServer;

public class Core {

	public static GameServer battlefield;
	public static void main(String[] args) {
		battlefield = GameServer.getBattleField(); 
		int x=0, y=0;
		do {
			x = (int)(Math.random() * GameServer.MAP_WIDTH);
			y = (int)(Math.random() * GameServer.MAP_HEIGHT);
		} while (battlefield.getUnit(x, y)!= null);
		new Thread(new Runnable() {
			public void run() {
				new Player(x, y);
			}
		}).start();
	}

}
