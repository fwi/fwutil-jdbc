package nl.fw.util.socket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TestUtilSocket {

	public static int readTimeOutMs = 2000;
	
	public static Socket openSocket(int portNumber) throws Exception {
		
		Socket s = new Socket();
		s.setReuseAddress(true);
		s.connect(new InetSocketAddress(InetAddress.getLocalHost(), portNumber), 1000);
		s.setSoTimeout(readTimeOutMs);
		return s;
	}
	
	public static String readMsg(Socket s) throws Exception {
		
		byte[] msgBytes = new byte[1024]; 
		int read = s.getInputStream().read(msgBytes);
		return (read > 0 ? new String(msgBytes, 0, read) : "");
	}

}
