package nl.fw.util.socket;

public class SocketSleepServer extends SocketAcceptor {
	
	public SocketSleepServer() {
		super();
		setCloseWaitTimeMs(1000L);
	}
	
	@Override
	public SocketTask getSocketTask() {
		return new SocketSleepTask();
	}
	
}
