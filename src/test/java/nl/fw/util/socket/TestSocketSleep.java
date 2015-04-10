package nl.fw.util.socket;

import static org.junit.Assert.*;

import java.net.Socket;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSocketSleep {

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	SocketSleepServer sss;
	Socket s, s2;
	
	@Before
	public void resetCounters() {
		
		SocketSleepTask.msgsSend.set(0);
		sss = new SocketSleepServer();
		sss.setCloseWaitTimeMs(100L);
		assertTrue(sss.start());
		s = null;
		s2 = null;
	}

	@Test
	public void portScan() {
		
		// TODO: Test does not work, need to open sockets async to mimic a port-scan.
		sss.setMaxOpenSocketTasks(1);
		try {
			s = TestUtilSocket.openSocket(sss.getOpenPort());
			s2 = TestUtilSocket.openSocket(sss.getOpenPort());
			SocketUtil.close(s2);
			log.debug("Closed socket 2");
			TestUtilSocket.readMsg(s);
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		} finally {
			SocketUtil.close(s);
			SocketUtil.close(s2);
			sss.awaitTasks();
		}
		assertEquals(2, sss.getAcceptedCount());
		// Eventhough s2 is closed immediatly, the socket can still be written by SocketTask
		//assertEquals(1, SocketSleepTask.msgsSend.get());
	}
	
	@Test
	public void sleepOne() {
		
		try {
			s = TestUtilSocket.openSocket(sss.getOpenPort());
			String msg = TestUtilSocket.readMsg(s);
			assertTrue(msg.contains("Slept for"));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		} finally {
			SocketUtil.close(s);
			sss.awaitTasks();
		}
		assertEquals(1, sss.getAcceptedCount());
		assertEquals(1, SocketSleepTask.msgsSend.get());
	}

	@Test
	public void tooBusy() {
		
		sss.setMaxOpenSocketTasks(1);
		Socket s2 = null;
		try {
			s = TestUtilSocket.openSocket(sss.getOpenPort());
			s2 = TestUtilSocket.openSocket(sss.getOpenPort());
			String msg = TestUtilSocket.readMsg(s);
			String msg2 = TestUtilSocket.readMsg(s2);
			assertTrue(msg.contains("Slept for"));
			assertTrue(msg2.contains("Too busy"));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		} finally {
			SocketUtil.close(s);
			SocketUtil.close(s2);
			sss.stopNow();
		}
		assertEquals(2, sss.getAcceptedCount());
		assertEquals(2, SocketSleepTask.msgsSend.get());
	}
}
