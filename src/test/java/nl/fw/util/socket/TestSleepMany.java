package nl.fw.util.socket;

import static org.junit.Assert.*;

import nl.fw.util.TestUtil;

import org.junit.Test;

public class TestSleepMany {

	@Test
	public void sleepMany() {
	
		int socketClients = 10;
		long runtime = 1000L;
	
		SocketSleepTask.msgsSend.set(0);
		SocketSleepServer sss = new SocketSleepServer();
		sss.setThreadName("sss-1");
		SocketUsageLogger reporter = new SocketUsageLogger();
		reporter.setReportInterval(100L);
		reporter.setReportId("SleepManyReporter");
		reporter.setThreadName("Rep-1");
		sss.setSocketUsageLogger(reporter);
		assertTrue(sss.start());
		
		// Setting a low max-acquire will show "Too busy to sleep" messages.
		//sss.getSocketTaskPool().setMaxAcquireTimeMs(10L);
		// Setting a low max-size will show "Too busy to sleep" messages. 
		sss.setMaxOpenSocketTasks(socketClients / 2);
		
		SocketClient.stop = false;
		SocketClient.count.set(0);
		SocketClient.done.set(0);
		for (int i = 0; i < socketClients; i++) {
			sss.getExecutor().execute(new SocketClient(sss.getOpenPort()));
		}
		TestUtil.sleep(runtime);
		SocketClient.stop = true;
		while (SocketClient.done.get() < socketClients) {
			TestUtil.sleep(10L);
		}
		sss.awaitTasks();
		assertTrue(sss.getAcceptedCount() > 10);
		assertTrue(SocketClient.count.get() > 10);
		assertTrue(sss.getAcceptedCount() >= SocketClient.count.get());
		TestUtil.sleep(100);
		assertEquals(sss.getAcceptedCount(), sss.getTasksFinished());
		assertEquals(sss.getTasksFinished(), reporter.lastProcessed);
	}
}
