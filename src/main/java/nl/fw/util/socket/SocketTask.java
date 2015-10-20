package nl.fw.util.socket;

import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Socket task that just logs an info- or warn-statement.
 * Overload method {@link #communicate()} to actually do something.
 * The {@link #run()}-method takes care of socket-validation and error-catching.
 * <br>Also, the run-method always signals the socket server/acceptor when the task is done
 * (if this is not done, all socket tasks will eventuelly be marked with "too busy").
 * @author Fred
 *
 */
//Copied from Yapool 0.9.3
public class SocketTask implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(SocketTask.class);
	
	protected Socket socket;
	private boolean tooBusy;
	private SocketAcceptor socketAcceptor;
	
	/** 
	 * If isTooBusy is true and closeTooBusy is true (the default), 
	 * the socket will be closed after calling {@link #communicate()} 
	 */
	protected boolean closeTooBusy = true;
	
	/**
	 * If isTooBusy is false and closeSocket is true (the default), 
	 * the socket will be closed after calling {@link #communicate()} 
	 */
	protected boolean closeSocket = true;
	
	/**
	 * Remote location of socket connection.
	 * Set by run-method after socket is validated using {@link SocketUtil#getRemoteLocation(Socket)}.
	 */
	protected String remoteLocation;

	/**
	 * Validates a socket using {@link SocketUtil#isValid(Socket)}, sets {@link #remoteLocation}
	 * and calls {@link #communicate()}.
	 * If the socket is not valid, the socket is closed using {@link SocketUtil#close(Socket)}
	 * and run-method exits without doing anything further.
	 */
	@Override
	public void run() {

		if (!SocketUtil.isValid(socket)) {
			SocketUtil.close(socket);
			return;
		}
		remoteLocation = SocketUtil.getRemoteLocation(socket);
		try {
			communicate();
		} catch (Exception e) {
			log.error("Failed to communicate " + (isTooBusy() ? "too-busy response " : "") + "over socket at " + remoteLocation, e);
		} finally {
			if ((isTooBusy() && closeTooBusy)
					|| (!isTooBusy() && closeSocket)) {
				SocketUtil.close(socket);
			}
			if (socketAcceptor != null) {
				socketAcceptor.taskDone();
			}
		}
	}
	
	/**
	 * Called by run-method when socket is valid.
	 * <br>Note that by default the socket is closed when this method returns 
	 * (see also {@link #closeSocket} and {@link #closeTooBusy}). 
	 * @throws Exception if thrown, it is catched by run-method and logged as error.
	 */
	public void communicate() throws Exception {
		
		if (isTooBusy()) {
			log.warn("Too busy to handle socket task, closing socket from " + remoteLocation);
		} else {
			log.info("Received socket from " + remoteLocation);
		}
	}
	
	/* *** bean methods *** */
	
	public void setSocket(Socket s) {
		this.socket = s;
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public void setTooBusy(boolean tooBusy) {
		this.tooBusy = tooBusy;
	}
	
	public boolean isTooBusy() {
		return tooBusy;
	}

	public SocketAcceptor getSocketAcceptor() {
		return socketAcceptor;
	}

	/** This method should only be called by the socket-acceptor. */
	protected void setSocketAcceptor(SocketAcceptor socketAcceptor) {
		this.socketAcceptor = socketAcceptor;
	}

}
