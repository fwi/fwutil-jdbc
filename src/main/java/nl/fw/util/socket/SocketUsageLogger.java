package nl.fw.util.socket;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Reports the acitivity of a {@link SocketAcceptor} as a log-statement that looks like:
 * <br><code>SleepManyReporter - accepted: 22, processing: 5, processed: 15, workers: 5</code>
 * <br>
 * Usage is only reported when values have changed (i.e. if there is no activity, nothing is logged).
 * The amount of workers is only logged when the amount of workers has changed.
 * @author FWiers
 *
 */
//Copied from Yapool 0.9.3
public class SocketUsageLogger implements Runnable {

	/** 
	 * Logger used to log report.
	 * Can be changed to a logger with category "{@code usage.report}" for example.
	 */
	public Logger log = LoggerFactory.getLogger(getClass());

	private String reportId = "";
	private long reportIntervalMs = 10000L;
	private SocketAcceptor acceptor;
	private String threadName;
	private boolean runAsTask;
	private ScheduledExecutorService scheduledExecutor;
	private ScheduledFuture<?> scheduledTask;

	private volatile boolean stop;
	private Semaphore sleepLock = new Semaphore(0);

	public SocketUsageLogger() {
		super();
	}
	public SocketUsageLogger(SocketAcceptor acceptor) {
		super();
		setSocketAcceptor(acceptor);
	}
	
	public void setSocketAcceptor(SocketAcceptor acceptor) {
		this.acceptor = acceptor;
		if (isEmpty(getReportId())) {
			setReportId(getDefaultReportId());
		}
	}
	
	protected String getDefaultReportId() {
		return "server@" + (getSocketAcceptor() == null ? "unknown" : getSocketAcceptor().getOpenPort());
	}
	
	public SocketAcceptor getSocketAcceptor() {
		return acceptor;
	}
	
	public long getReportIntervalMs() {
		return reportIntervalMs;
	}

	/** Default 10 seconds. */
	public void setReportInterval(long reportIntervalMs) {
		this.reportIntervalMs = reportIntervalMs;
	}

	public String getReportId() {
		return reportId;
	}
	
	public String getThreadName() {
		return threadName;
	}
	/** If not empty, sets the name thread running this acceptor to the given name. */ 
	public void setThreadName(String threadName) {
		this.threadName = threadName;
	}


	/** 
	 * An optional socket-reporter ID shown in the report-log statement.
	 * Useful in case there are multiple socket-reporters running. 
	 */
	public void setReportId(String reportId) {
		if (reportId != null) {
			this.reportId = reportId;
		}
	}

	/**
	 * Starts as a run-loop in a thread from the {@link #getSocketAcceptor()}'s executor.
	 * To run as a scheduled task, use {@link #start(ScheduledExecutorService)}.
	 */
	public void start() {
		
		if (acceptor == null) {
			log.error("No socket acceptor set, cannot log usage.");
		} else {
			if (isEmpty(getReportId())) {
				setReportId(getDefaultReportId());
			}
			acceptor.getExecutor().execute(this);
		}
	}

	/**
	 * Schedules a task for reporting, this task will repeat until {@link #stop()} is called.
	 * @param scheduler The scheduled executor service.
	 */
	public void start(ScheduledExecutorService scheduler) {
		
		if (acceptor == null) {
			log.error("No socket acceptor set, cannot log usage.");
		} else {
			runAsTask = true;
			this.scheduledExecutor = scheduler;
			scheduleTask();
			debug("socket usage logger started.");
		}
	}
	
	protected void scheduleTask() {
		scheduledTask = scheduledExecutor.schedule(this, getReportIntervalMs(), TimeUnit.MILLISECONDS);
	}

	public void stop() {
		
		stop = true;
		if (runAsTask) {
			if (scheduledTask != null) {
				scheduledTask.cancel(false);
				scheduledTask = null;
			}
			debug("socket usage logger stopped.");
		} else {
			sleepLock.release();
		}
	}
	
	protected void debug(String msg) {
		
		if (log.isDebugEnabled()) {
			String prefix = (isEmpty(getReportId()) ? "" : getReportId() + " - ");
			log.debug(prefix + msg);
		}
	}

	/**
	 * Reports in a loop or as a repeating scheduled task.
	 * Only reports if there are changes in the previously reported numbers.
	 */
	@Override
	public void run() {

		if (runAsTask) {
			runAsTask();
		} else {
			runInThread();
		}
	}
	
	protected void runAsTask() {
		
		if (stop) {
			scheduledTask = null;
		} else {
			if (report()) {
				log.info(getReport());
			}
			scheduleTask();
		}
	}
	
	protected void runInThread() {
		
		String orgThreadName = null;
		if (threadName != null) {
			orgThreadName = Thread.currentThread().getName();
			Thread.currentThread().setName(threadName);
		}
		try {
			while (!stop) {
				if (report()) {
					log.info(getReport());
				}
				try {
					if (sleepLock.tryAcquire(reportIntervalMs, TimeUnit.MILLISECONDS)) {
						stop = true;
					}
				} catch (Exception e) {
					log.debug(reportId + " socket reporter stopping after interruption: " + e);
					stop = true;
				}
			}
		} finally {
			log.info(getReport());
			debug("Socket connections reporter stopping.");
			if (orgThreadName != null) {
				Thread.currentThread().setName(orgThreadName);
			}
		}
	}
	
	protected long lastAccepted;
	protected long lastProcessing;
	protected long lastProcessed;
	
	/**
	 * Evaluates the "lastCount" values.
	 * @return true if any values have changed since last call to {@link #getReport()}.
	 */
	public boolean report() {
		
		boolean report = (acceptor.getAcceptedCount() != lastAccepted)
				|| (acceptor.getTasksFinished() != lastProcessed)
				|| (acceptor.getOpenTasks() != lastProcessing);
		return report;
	}

	/** Creates a report for the log and updates the "lastCount" values. */
	public String getReport() {

		StringBuilder sb = new StringBuilder(128);

		if (reportId != null && !reportId.isEmpty()) {
			sb.append(reportId).append(" - ");
		}

		long count = acceptor.getAcceptedCount();
		sb.append("accepted: ").append(count - lastAccepted).append(", ");
		lastAccepted = count;

		count = acceptor.getOpenTasks();
		sb.append("processing: ").append(count).append(", ");
		lastProcessing = count;

		count = acceptor.getTasksFinished();
		sb.append("processed: ").append(count - lastProcessed);
		lastProcessed = count;

		return sb.toString();
	}
	
	/**
	 * 
	 * @param s
	 * @return true if s is null or empty.
	 */
	public static boolean isEmpty(String s) {
		return (s == null || s.isEmpty());
	}
}
