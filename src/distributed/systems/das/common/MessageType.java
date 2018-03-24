package distributed.systems.das.common;

import java.io.Serializable;

/**
 * Different request types for the
 * nodes to send to the server.
 * 
 * @author Pieter Anemaet, Boaz Pat-El
 */
public enum MessageType {
	spawnUnit, getUnit, moveUnit, putUnit, removeUnit, getType, dealDamage, healDamage, setup, sync
}