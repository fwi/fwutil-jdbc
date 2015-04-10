package nl.fw.util.socket;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

import nl.fw.util.TestUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketClient implements Runnable {
	
	private static Logger log = LoggerFactory.getLogger(SocketClient.class);

	private int portNumber;

	public static volatile boolean stop;
	public static AtomicLong count = new AtomicLong();
	public static AtomicLong done = new AtomicLong();
	
	public SocketClient(int portNumber) {
		this.portNumber = portNumber;
	}
	
	@Override
	public void run() {

		while (!stop) {
			Socket s = null;
			try {
				s = TestUtilSocket.openSocket(portNumber);
				s.setReuseAddress(true);
				String msg = TestUtilSocket.readMsg(s);
				log.debug("Message received: " + msg);
				count.getAndIncrement();
				if (msg.startsWith("Too busy")) {
					TestUtil.sleep(SocketSleepTask.minSleepTime );
				}
			} catch (Exception e) {
				log.error(e.toString());
			} finally {
				SocketUtil.close(s);
			}
		}
		done.incrementAndGet();
	}

}
