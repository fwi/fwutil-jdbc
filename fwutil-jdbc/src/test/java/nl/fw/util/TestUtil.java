package nl.fw.util;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {
	
	private static final Logger log = LoggerFactory.getLogger(TestUtil.class);
	
	public static void sleep(long timeMs) { 
		try { Thread.sleep(timeMs); } catch (Exception ignored) {
			log.debug("Sleep of " + timeMs + " interrupted.");
		}
	}
	
	public static Thread start(Runnable r, CountDownLatch latch) {
		
		WaitStart w = new WaitStart(r, latch);
		Thread t = new Thread(w);
		t.start();
		return t;
	}
	
	public static void await(CountDownLatch latch) {

		long tstart = System.currentTimeMillis();
		try {
			latch.await();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		log.debug("Await time: " + (System.currentTimeMillis() - tstart));
	}
	
	public static void close(Closeable c) {
	
		try {
			if (c != null) c.close();
		} catch (Exception ignored) {}
	}

	public static InputStream getResourceStream(String rname) {
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(rname);
	}
	
}
