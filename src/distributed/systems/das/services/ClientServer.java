package distributed.systems.das.services;

import distributed.systems.das.common.UnitState;

public interface ClientServer {
	public void moveUnitRequest(UnitState unit, int toX, int toY);
	public void healDamageRequest(UnitState unit, int toX, int toY);
	public void dealDamageRequest(UnitState unit, int toX, int toY);
	public void removeUnitRequest(int x, int y);
	public UnitState getUnit(int targetX, int targetY);
}
