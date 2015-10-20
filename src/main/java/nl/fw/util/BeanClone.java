package nl.fw.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for (un)serializing and (un)gzipping objects. 
 * Any thrown checked exceptions are re-thrown as runtime exceptions.
 * @author Fred
 *
 */
//Copied from Yapool 0.9.3
public class BeanClone {

	protected static Logger log = LoggerFactory.getLogger(BeanClone.class);
	
	/** Default byte-buffer size for streams used in this class (512 bytes, also default for GZIP streams). */
	public static int BUFFER_SIZE = 512;
	
	// Utility class, do not instantiate
	private BeanClone() {}
	
	public static byte[] serialize(Object o) {
		return serialize(o, BUFFER_SIZE);
	}
	
	public static byte[] serialize(Object o, int bufSize) {
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream(bufSize);
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(o);
			oos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		byte[] ba = baos.toByteArray();
		log.trace("Serialized size: {}", ba.length);
		return ba;
	}
	
	/**
	 * GZips (deflates) the given bytes.
	 */
	public static byte[] gzip(byte[] ba) {
		
		int bufSize = (ba.length < BUFFER_SIZE ? BUFFER_SIZE : ba.length / 2);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(bufSize);
		try {
			GZIPBestOutputStream gzos = new GZIPBestOutputStream(baos);
			gzos.write(ba);
			gzos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		byte[] baZipped = baos.toByteArray();
		log.trace("Compressed size: {}", baZipped.length);
		return baZipped;
	}
	
	public static Object unserialize(byte[] ba) {
		
		Object o = null;
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(ba);
			ObjectInputStream oin = new ObjectInputStream(bais);
			o = oin.readObject();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return o;
	}
	
	/**
	 * Un-gzips (inflates) the given bytes.
	 */
	public static byte[] ungzip(byte[] ba) {
		
		int bufSize = (ba.length < BUFFER_SIZE / 2 ? BUFFER_SIZE : ba.length * 2);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(bufSize);
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(ba);
			GZIPInputStream gzin = new GZIPInputStream(bais);
			byte[] buf = new byte[bufSize];
			int l = -1;
			while ((l = gzin.read(buf, 0, bufSize)) > -1) {
				baos.write(buf, 0, l);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		byte[] baUnzipped = baos.toByteArray();
		log.trace("Uncompressed size: {}", baUnzipped.length);
		return baUnzipped;
	}
	
	/**
	 * Clones an object by serializing and then unserializing it.
	 */
	public static <T> T clone(T o) {
		return clone(o, BUFFER_SIZE);
	}

	@SuppressWarnings("unchecked")
	public static <T> T clone(T o, int bufSize) {
		return (T) unserialize(serialize(o, bufSize));
	}

	/** A {@link GZIPOutputStream} that uses {@link Deflater#BEST_COMPRESSION}. */
	public static class GZIPBestOutputStream extends GZIPOutputStream {

		public GZIPBestOutputStream(OutputStream out) throws IOException {
			this(out, BUFFER_SIZE);
		}
		
		public GZIPBestOutputStream(OutputStream out, int bufSize) throws IOException {
			super(out, bufSize);
			def.setLevel(Deflater.BEST_COMPRESSION);
		}
	}

}
