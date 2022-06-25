package nl.fw.util.jdbc.hikari;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * Logs a Hikari pool's status at regular intervals using all readily available counters and sizes
 * (there is no performance impact on the pool itself).
 * E.g. a log-statement could look like:
 * <br>{@code Pool stats PoolName (total=10, inUse=6, avail=4, waiting=1)}
 * <br>If values do not change, nothing is logged unless a connection is active.
 * If nothing happens with the pool, no log statement will appear.
 * @author FWiers
 * <br>Copied from Yapool at https://code.google.com/p/yapool/
 */
public class HikPoolUsageLogger implements Runnable {

	/** 
	 * Logger used to log report.
	 * Can be changed to a logger with category "{@code usage.report}" for example.
	 */
	public Logger log = LoggerFactory.getLogger(getClass());

	private HikariPoolMXBean pool;
	private String poolName;

	private ScheduledExecutorService executor;
	private boolean closeExecutor;
	private ScheduledFuture<?> scheduledTask;
	private long reportIntervalMs = 10000L;
	private volatile boolean stop;

	public HikPoolUsageLogger() {
		super();
	}
	public HikPoolUsageLogger(HikariPoolMXBean pool, String poolName) {
		super();
		setPool(pool, poolName);
	}
	
	public void setPool(HikariPoolMXBean pool, String poolName) {
		this.pool = pool;
		this.poolName = poolName;
	}
	
	public HikariPoolMXBean getPool() { 
		return pool; 
	}
	
	public long getReportIntervalMs() {
		return reportIntervalMs;
	}

	/** Default 10 seconds. */
	public void setReportInterval(long reportIntervalMs) {
		this.reportIntervalMs = reportIntervalMs;
	}

	public void start() {
		start(null);
	}

	public void start(ScheduledExecutorService executor) {
		
		if (pool == null) {
			log.error("No pool set, cannot start pool usage logger.");
			return;
		}
		if (executor == null) {
			closeExecutor = true;
			executor = Executors.newScheduledThreadPool(1);
			((ScheduledThreadPoolExecutor)executor).setRemoveOnCancelPolicy(true);
		}
		this.executor = executor;
		scheduleTask();
		if (log.isDebugEnabled()) {
			log.debug("Started pool usage logger for pool " + poolName);
		}
	}
	
	protected void scheduleTask() {
		scheduledTask = executor.schedule(this, getReportIntervalMs(), TimeUnit.MILLISECONDS);
	}
	
	public void stop() {
		
		stop = true;
		ScheduledFuture<?> t = scheduledTask;
		if (t != null) {
			t.cancel(false);
			scheduledTask = null;
		}
		if (closeExecutor) {
			executor.shutdown();
		}
		if (log.isDebugEnabled()) {
			log.debug("Stopped pool usage logger for pool " + poolName);
		}
	}
	
	@Override
	public void run() {
		
		if (stop) {
			scheduledTask = null;
		} else {
			if (report()) {
				log.info(getReport());
			}
			scheduleTask();
		}
	}
	
	protected int activeSize;
	protected int lastIdleSize;
	protected int lastTotalSize;
	protected int lastWaiting;
	
	/**
	 * Evaluates the "lastCount" values.
	 * @return true if a connection is active or any other values have changed since last call to {@link #getReport()},
	 */
	public boolean report() {
		
		activeSize = pool.getActiveConnections();
		boolean report = (activeSize > 0)
				|| (pool.getIdleConnections() != lastIdleSize)
				|| (pool.getTotalConnections() != lastTotalSize)
				|| (pool.getThreadsAwaitingConnection() != lastWaiting);
		return report;
	}
	
	
	/** Creates a report for the log and updates the "lastCount" values. */
	public String getReport() {

		StringBuilder sb = new StringBuilder(64);
		sb.append("Pool stats ").append(poolName).append(" (");
		lastTotalSize = pool.getTotalConnections();
		sb.append("total=").append(lastTotalSize);
		// activeSize is already updated in report()-method.
		sb.append(", used=").append(activeSize);
		lastIdleSize = pool.getIdleConnections();
		sb.append(", available=").append(lastIdleSize);
		lastWaiting = pool.getThreadsAwaitingConnection();
		sb.append(", waiting=").append(lastWaiting);
		sb.append(")");
		return sb.toString();
	}
}
