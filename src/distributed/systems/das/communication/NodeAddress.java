package distributed.systems.das.communication;

public class NodeAddress {
	public String ipAddress;
	public int port;
	
	public NodeAddress(String ipAddress, int port) {
		this.ipAddress = ipAddress;
		this.port = port;
	}
}
