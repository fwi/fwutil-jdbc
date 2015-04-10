package nl.fw.util.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ServerSocket-thread that accepts incoming sockets and starts connection-tasks for them.
 * Depending on the amount of open connections, a "too busy" flag is set which provides
 * control over the limit of open connections and thus the work-load.
 * Each socket is handled with 1 thread, which does not work for servers 
 * with many open connections like http-servers (use Jetty, Apache, Tomcat, etc.).
 * It does however work when the amount of open connections is limited (up to about 500 on one machine).
 * @author frederikw
 *
 */
//Copied from Yapool 0.9.3
public class SocketAcceptor implements Runnable {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private int portNumber;
	private int backLog = 50;
	private int maxOpenSocketTasks = 50;
	private AtomicInteger openTasks = new AtomicInteger();
	private Semaphore awaitLock;
	private boolean exitOnError;
	private boolean opened;
	private ServerSocket serverSocket;
	private int openPort;
	private volatile boolean stop;
	private AtomicLong acceptedSockets = new AtomicLong();
	private AtomicLong tasksFinished = new AtomicLong();
	private long closeWaitTimeMs = 30000L;
	private SocketUsageLogger reporter;
	private Throwable runError;

	private Executor executor;
	private boolean shutdownExecutorOnClose;
	private String threadName;

	/** 
	 * This will open the server-socket (no sockets are accepted yet).
	 * @return true if the server-socket was opened and ready to accept incoming socket connections.
	 */
	public boolean openSocketServer() {
		
		opened = false;
		try {
			if (serverSocket == null) {
				serverSocket = new ServerSocket();
			}
			// See http://meteatamel.wordpress.com/2010/12/01/socket-reuseaddress-property-and-linux/
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(portNumber), backLog);
			serverSocket.setSoTimeout(0);
			openPort = serverSocket.getLocalPort();
			log.info("Listening for socket connections on port " + openPort);
			opened = true;
		} catch (Throwable t) {
			log.error("Could not open the server socket.", t);
			closeServerSocket();
			serverSocket = null;
			openPort = 0;
		}
		return opened;
	}
	
	/** 
	 * Starts accepting incoming socket connections (uses {@link #getExecutor()} to run this instance,
	 * if that returns null, a cached thread pool is created and set as executor). 
	 * <br>Before starting this method, you should call openSocketServer() and check the returned value.
	 * If openSocketServer() was not called previously, it is called by this method.
	 * @return true if this SocketAcceptor started, false otherwise. 
	 */
	public boolean start() {
		return start(false);
	}

	/**
	 * Same as {@link #start()} but runs this socket server in the thread calling this method.
	 * After socket server is closed and this method returns, {@link #awaitTasks()} should be called before closing the application.
	 * @return true if this SocketAcceptor started, false otherwise. 
	 */
	public boolean startBlocking() {
		return start(true);
	}

	protected boolean start(boolean blocking) {
		
		if (!opened && !openSocketServer()) {
			return false;
		}
		stop = false;
		if (executor == null) {
			setExecutor(Executors.newCachedThreadPool());
			setShutdownExecutorOnClose(true);
		}
		if (reporter != null) {
			reporter.start();
		}
		if (blocking) {
			run();
		} else {
			executor.execute(this);
		}
		return true;
	}

	
	/**
	 * The socket-task to execute for a new socket. 
	 * This method returns a {@link SocketTask} by default.
	 * Overload this method to return a task that does something
	 * (you can extend {@link SocketTask} if you like).
	 * <br>Creation of a socket-task object should be cheap as this method is called within
	 * the main server-socket thread. Also, it may not throw a runtime-exception else the
	 * main server-socket thread will exit.
	 * <br>Socket tasks objects can be re-used with the help of an {@code nl.fw.yapool.object.ObjectPool}.
	 * @return the socket task to execute, never null.
	 */
	public SocketTask getSocketTask() {
		return new SocketTask();
	}

	/**
	 * Method called from {@link SocketTask} to signal a task is done.
	 */
	public void taskDone() {
		
		tasksFinished.incrementAndGet();
		openTasks.decrementAndGet();
		if (awaitLock != null && openTasks.get() < 1) {
			awaitLock.release();
		}
	}

	/** The run-loop, should not be called directly (use start() method). */
	@Override
	public void run() {

		Socket s = null;
		runError = null;
		openTasks.set(0);
		acceptedSockets.set(0L);
		tasksFinished.set(0L);
		opened = true;
		String orgThreadName = null;
		if (threadName != null) {
			orgThreadName = Thread.currentThread().getName();
			Thread.currentThread().setName(threadName);
		}
		try {
			while (!stop) {
				try {
					s = serverSocket.accept();
				} catch (SocketException se) {
					log.info("Received a shutdown request: " + se);
					stop = true;
				} catch (IOException ioe) {
					log.error("IO-error while waiting for new socket connection.", ioe); 
					// Check if server-socket should be restarted, see also
					// http://stackoverflow.com/questions/3028543/what-to-do-when-serversocket-throws-ioexception-and-keeping-server-running
				}
				if (s == null) {
					continue;
				}
				acceptedSockets.incrementAndGet();
				SocketTask task = getSocketTask();
				task.setSocketAcceptor(this);
				task.setSocket(s);
				task.setTooBusy(openTasks.incrementAndGet() > maxOpenSocketTasks);
				executor.execute(task);
				s = null;
			}
		} catch (Throwable t) {
			runError = t;
			log.error("Unexpected error encountered, socket server stopping!", t);
		} finally {
			closeServerSocket();
			// if an InterruptedException occurred a new socket might have been left dangling.
			SocketUtil.close(s);
			if (orgThreadName != null) {
				Thread.currentThread().setName(orgThreadName);
			}
			if (exitOnError && runError != null) {
				awaitTasks();
				System.exit(1);
			}
		}
	}
	
	/** 
	 * Stops the server socket and all related workers. 
	 * After first call the subsequent calls have no effect.
	 * This call is non-blocking, socket tasks may still be running.
	 * <br>If {@link #awaitTasks()} is not called, 
	 * stop the {@link #getExecutor()} manually if needed.
	 */
	public void stop() { 
		stop(false);
	}
	
	/** 
	 * Stops the server socket and all related workers immediatly, does not wait for tasks to finish. 
	 */
	public void stopNow() { 
		stop(true);
	}
	
	protected void stop(boolean now) {
		
		/*
		 * The execute-loop may or may not have stopped (depending if a socket
		 * is being handled or serverSocket is waiting for a socket).
		 * If it has stopped, closeServerSocket() is already called,
		 * if it did not stop, closeServerSocket() will surely stop the loop.   
		 */
		closeServerSocket();
		if (now) {
			shutdownExecutor();
		}
	}

	/** 
	 * Closes the server-socket and stops the {@link #getSocketUsageLogger()} (if it was set). 
	 * This method is synchronized, after the first call subsequent calls have no effect.
	 */
	protected synchronized void closeServerSocket() {

		stop = true;
		if (serverSocket == null || !opened) {
			return;
		}
		if (reporter != null) {
			reporter.stop();
		}
		if (serverSocket != null && !serverSocket.isClosed()) {
			if (log.isDebugEnabled()) {
				log.debug("Closing socket server on port " + openPort);
			}
			try {
				serverSocket.close();
				// serverSocket.accept() will now throw a SocketException
			} catch (Throwable t) { 
				log.warn("Could not properly close server socket.", t); 
			}
			log.info("Server socket on port " + openPort + " closed. Number of accepted sockets: " + acceptedSockets);
		}
		serverSocket = null;
		openPort = 0;
		opened = false;
	}
	
	/**
	 * @return null or an unexpected exception that stopped the ServerSocket from working.
	 */
	public Throwable getRunError() {
		return runError;
	}
	
	/**
	 * Calls {@link #awaitTasks(long)} with {@link #getCloseWaitTimeMs()}.
	 */
	public int awaitTasks() {
		return awaitTasks(getCloseWaitTimeMs());
	}
	
	/**
	 * Closes the server socket if it was not already closed and 
	 * waits for tasks to stop running within the given maximum wait time in miliseconds.
	 * <br>Logs info-messages at regular intervals which show the amount of open tasks.
	 * Closes the executor if {@link #isShutdownExecutorOnClose()} is true.
	 * @return Amount of tasks that were still running after the maximum wait time was exceeded.
	 */
	public int awaitTasks(long maxWaitTimeMs) {
		
		if (!stop) {
			closeServerSocket();
		}
		if (openTasks.get() > 0) {
			awaitRunningTasks(maxWaitTimeMs);
		}
		int tasksStillOpen = openTasks.get();
		if (tasksStillOpen <= 0) {
			log.info("All socket tasks finished.");
		} else {
			log.warn("Closing with " + tasksStillOpen + " socket task(s) still running!");
		}
		shutdownExecutor();
		return tasksStillOpen;
	}
	
	protected void awaitRunningTasks(long maxWaitTimeMs) {
		
		if (maxWaitTimeMs < 1L) {
			return;
		}
		log.info("Waiting for " + openTasks.get() + " socket task(s) to finish.");
		awaitLock = new Semaphore(0);
		final long maxTime = System.currentTimeMillis() + maxWaitTimeMs;
		final long sleepTime = (maxWaitTimeMs < 5000L ? maxWaitTimeMs < 1000L ? maxWaitTimeMs < 100L ? 50L : 500L : 1000L : 5000L);
		while (System.currentTimeMillis() < maxTime && openTasks.get() > 0) {
			try {
				awaitLock.tryAcquire(sleepTime, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				log.warn("Waiting for open socket tasks interrupted: " + e);
				break;
			}
			if (openTasks.get() > 0) {
				log.info("Waiting for " + openTasks.get() + " socket task(s) to finish.");
			}
		}
	}
	
	/**
	 * Closes the {@link #getExecutor()} but only if it {@link #isShutdownExecutorOnClose()} is true 
	 * and executor is of type {@link ThreadPoolExecutor}.
	 */
	protected void shutdownExecutor() {
		
		if (!isShutdownExecutorOnClose()) {
			return;
		}
		if (getExecutor() instanceof ThreadPoolExecutor) {
			((ThreadPoolExecutor)executor).shutdownNow();
		}
	}
	
	/* *** bean methods *** */
	
	/**
	 * Returns the configured port-number, the actual open port is returned by {@link #getOpenPort()}.
	 */
    public int getPortNumber() {
		return portNumber;
	}
	/** The port-number to listen on for incoming sockets. Default 0 (which means "any available port"). */  
	public void setPortNumber(int portNumber) {
		this.portNumber = portNumber;
	}
	
	/** The port on which the server socket is listening. */
	public int getOpenPort() { 
		return openPort; 
	} 

	public int getBackLog() {
		return backLog;
	}
	/** 
	 * The amount of sockets that can be waiting to be accepted. Default 50, 
	 * set higher to prevent "connection refused" errors.
	 */
	public void setBackLog(int backLog) {
		this.backLog = backLog;
	}
	
	/** The amount of sockets accepted. */
	public long getAcceptedCount() { 
		return acceptedSockets.get();
	}

	/**
	 * If set, the reporter is stopped and started with this acceptor.
	 * @param reporter
	 */
	public void setSocketUsageLogger(SocketUsageLogger reporter) {
		this.reporter = reporter;
		reporter.setSocketAcceptor(this);
	}
	public SocketUsageLogger getSocketUsageLogger() {
		return reporter;
	}
	
	public boolean isExitOnError() {
		return exitOnError;
	}
	/** 
	 * When an unexpected error occurs and this variable is true, System.exit(1) will be called
	 * (which triggers any registered shutdown-hooks to be executed). 
	 */
	public void setExitOnError(boolean exitOnError) {
		this.exitOnError = exitOnError;
	}
	
	public ServerSocket getServerSocket() {
		return serverSocket;
	}
	public void setServerSocket(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

    public Executor getExecutor() { 
    	return executor; 
    }
	/** Executor used to start the {@link #run()}-loop when {@link #start()} is called. */
	public void setExecutor(Executor executor) { 
    	this.executor = executor; 
    }

	public long getCloseWaitTimeMs() {
		return closeWaitTimeMs;
	}
	/** The maximum time to wait for socket-tasks to finish. */
	public void setCloseWaitTimeMs(long closeWaitTimeMs) {
		this.closeWaitTimeMs = closeWaitTimeMs;
	}

	public String getThreadName() {
		return threadName;
	}
	/** If not empty, sets the name thread running this acceptor to the given name. */ 
	public void setThreadName(String threadName) {
		this.threadName = threadName;
	}

	public boolean isShutdownExecutorOnClose() {
		return shutdownExecutorOnClose;
	}

	public void setShutdownExecutorOnClose(boolean shutdownExecutorOnClose) {
		this.shutdownExecutorOnClose = shutdownExecutorOnClose;
	}

	public int getMaxOpenSocketTasks() {
		return maxOpenSocketTasks;
	}

	public void setMaxOpenSocketTasks(int maxOpenSocketTasks) {
		this.maxOpenSocketTasks = maxOpenSocketTasks;
	}
	
	public long getTasksFinished() {
		return tasksFinished.get();
	}
	
	public int getOpenTasks() {
		return openTasks.get();
	}
	
}
