package simulateGame;

import distributed.systems.das.BattleField;

public class Core {

	public static BattleField battlefield;
	public static void main(String[] args) {
		battlefield = BattleField.getBattleField(); 
		int x=0, y=0;
		do {
			x = (int)(Math.random() * BattleField.MAP_WIDTH);
			y = (int)(Math.random() * BattleField.MAP_HEIGHT);
		} while (battlefield.getUnit(x, y)!= null);
		new Thread(new Runnable() {
			public void run() {
				new Player(x, y);
			}
		}).start();
	}

}
