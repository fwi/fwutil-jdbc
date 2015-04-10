package nl.fw.util;

import java.util.concurrent.CountDownLatch;

public class WaitStart implements Runnable {
	
	private CountDownLatch latch;
	private Runnable toRun;
	
	public WaitStart(Runnable toRun) {
		this(toRun, new CountDownLatch(1));
	}
	
	public WaitStart(Runnable toRun, CountDownLatch latch) {
		super();
		this.latch = latch;
		this.toRun = toRun;
	}
	
	public CountDownLatch getLatch() { return latch; }
	
	public void run() {
		
		latch.countDown();
		TestUtil.await(latch);
		toRun.run();
	}

}
