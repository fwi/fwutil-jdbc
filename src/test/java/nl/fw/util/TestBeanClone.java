package nl.fw.util;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBeanClone {

	protected Logger log = LoggerFactory.getLogger(getClass());

	@Test
	public void testGzipSerialize() {
		
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("key", "value");
		m.put("int", 42);
		Map<String, Object> m2 = cloneViaGzip(m);
		assertTrue(m2 instanceof HashMap);
		assertTrue(m.equals(m2));
		assertEquals(42, m2.get("int"));
		assertEquals("value", m2.get("key"));
	}
	
	@Test
	public void testGzipSerializeBig() {
		
		Map<String, Object> m = new HashMap<String, Object>();
		for (int i = 0; i < 1000; i ++) {
			m.put("key" + i, "value" + i);
		}
		Map<String, Object> m2 = cloneViaGzip(m);
		assertTrue(m.equals(m2));
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, Object> cloneViaGzip(Map<String, Object> m) {

		byte[] ba = BeanClone.gzip(BeanClone.serialize(m));
		Object o = BeanClone.unserialize(BeanClone.ungzip(ba));
		return (Map<String, Object>) o;
	}
}
