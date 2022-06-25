package nl.fw.util.socket;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import nl.fw.util.TestUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketSleepTask extends SocketTask {

	private static Logger log = LoggerFactory.getLogger(SocketSleepTask.class);

	/** Minimum time to sleep in milliseconds. */
	public static int minSleepTime = 1;
	/** Maximum random time to sleep in milliseconds (added to minSleepTime). */
	public static int varSleepTime = 50;
	
	public static String busyMsg = "Too busy to sleep.";
	
	public static AtomicLong msgsSend = new AtomicLong();

	@Override
	public void communicate() throws Exception {
		
		if (isTooBusy()) {
			log.debug(remoteLocation + " - " + busyMsg);
			sendMsg(busyMsg);
		} else {
			final long sleepTime = (long)(Math.random()*varSleepTime) + minSleepTime;
			log.debug(remoteLocation + " - socket received, sleeping " + sleepTime + " ms.");
			TestUtil.sleep(sleepTime);
			log.debug(remoteLocation + " - done sleeping " + sleepTime + " ms.");
			sendMsg("Slept for " + sleepTime + " ms.");
		}
	}
	
	protected void sendMsg(String msg) throws Exception {
		
		OutputStream out = getSocket().getOutputStream();
		out.write(msg.getBytes());
		out.flush();
		msgsSend.getAndIncrement();
	}

}
