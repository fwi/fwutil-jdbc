package nl.fw.util.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Copied from Yapool 0.9.3
public class SocketUtil {

	private static final Logger log = LoggerFactory.getLogger(SocketUtil.class);

	/**
	 * Closes socket if not null and not already closed.
	 * Logs a warning if closing fails.
	 * @param s
	 */
	public static void close(final Socket s) {
		
		if (s != null && !s.isClosed()) {
			try { s.close(); } catch (IOException ioerror) {
				log.warn("Could not properly close socket.", ioerror);
			}
		}
	}
	
	/**
	 * Returns remote-IP-address:remote-port, or, if the socket is not connected,
	 * "0.0.0.0:-1".
	 */
	public static String getRemoteLocation(Socket s) {
		final InetAddress ia = s.getInetAddress();
		return (ia == null ? "0.0.0.0:-1" : ia.getHostAddress() + ":" + s.getPort());
	}

	/**
	 * Checks if accepted socket is valid. This can be useful to detect port-scans etc.
	 * <br>Logs debug messages if socket is closed or not connected.
	 * @return false when the socket is closed or not connected, true otherwise. 
	 */
	public static boolean isValid(Socket s) {
		
		boolean valid = true;
		if (s.isClosed()) {
			log.debug("Socket is closed.");
			valid = false;
		} else if (!s.isConnected()) {
			log.debug("Socket is not connected.");
			valid = false;
		}
		return valid;
	}

}
