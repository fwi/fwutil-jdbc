package nl.fw.util;


import java.util.Map;
import java.util.Properties;

import nl.fw.util.BeanClone;
import nl.fw.util.BeanConfig;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class TestBeanConfig {

	protected Logger log = LoggerFactory.getLogger(getClass());
	
	@Test
	public void primitiveProps() {
		
		ConfigBean pool = new ConfigBean();
		assertFalse(pool.isFair());
		assertEquals(0, pool.getMaxAcquireTimeMs());
		assertFalse(pool.getMaxLeaseTimeMs() == 1000);
		Properties props = new Properties();
		props.setProperty("fair", "true");
		props.setProperty("maxacquiretimems", "10");
		props.setProperty("maxLeaseTimeS", "1");
		BeanConfig.configure(pool, props);
		assertTrue(pool.isFair());
		assertEquals(10, pool.getMaxAcquireTimeMs());
		assertEquals(1000, pool.getMaxLeaseTimeMs());
		
		Properties extracted = new Properties();
		BeanConfig.extract(pool, extracted);
		assertEquals("true", extracted.getProperty("fair"));
		assertEquals("1000", extracted.getProperty("maxLeaseTimeMs"));
		assertEquals("0", extracted.getProperty("minSize"));
		assertEquals("4", extracted.getProperty("maxSize"));
		assertFalse(extracted.containsKey("closed"));
	}

	@Test
	public void propsInProps() {
		
		ConfigBean factory = new ConfigBean();
		Properties props = new Properties();
		String jdbcDriver = factory.getJdbcDriverClass();
		props.setProperty("jdbcUrl", "jdbc:test:url");
		props.setProperty("connection.customProp", "customPropValue");
		props.setProperty("connection.AnotherProp", "AnotherPropValue");
		props.setProperty("connection.driver.special.class", "driver.special.class.value");
		BeanConfig.configure(factory, props);
		assertEquals("jdbc:test:url", factory.getJdbcUrl());
		assertEquals(jdbcDriver, factory.getJdbcDriverClass());
		assertEquals(3, factory.getConnectionProps().size());
		assertTrue(factory.getConnectionProps().containsKey("AnotherProp"));
		assertTrue(factory.getConnectionProps().contains("AnotherPropValue"));

		Properties extracted = new Properties();
		BeanConfig.extract(factory, extracted);
		assertEquals("false", extracted.getProperty("autoCommit"));
		assertEquals("AnotherPropValue", extracted.getProperty("connection.AnotherProp"));
		assertEquals("driver.special.class.value", extracted.getProperty("connection.driver.special.class"));
	}
	
	@Test
	public void filterProps() {
		
		Properties p = new Properties();
		p.setProperty("base.maxacquiretimems", "10");
		p.setProperty("small.maxsize", "2");
		p.setProperty("large.maxsize", "12");
		p.setProperty("large.connection.user", "LargeUser");
		p.setProperty("large.connection.password", "");
		
		Map<String, Object> base = BeanConfig.filterPrefix(p, "base.");
		Map<String, Object> small = BeanClone.clone(base);
		small.putAll(BeanConfig.filterPrefix(p, "small."));
		Map<String, Object> large = BeanClone.clone(base);
		large.putAll(BeanConfig.filterPrefix(p, "large."));
		
		ConfigBean poolSmall = new ConfigBean();
		BeanConfig.configure(poolSmall, small);
		ConfigBean poolLarge = new ConfigBean();
		BeanConfig.configure(poolLarge, large);
		ConfigBean factory = new ConfigBean();
		BeanConfig.configure(factory, large);
		
		assertEquals(10, poolSmall.getMaxAcquireTimeMs());
		assertEquals(10, poolLarge.getMaxAcquireTimeMs());
		assertEquals(2, poolSmall.getMaxSize());
		assertEquals(12, poolLarge.getMaxSize());
		assertEquals("LargeUser", factory.getConnectionProps().get("user"));
		assertEquals("", factory.getConnectionProps().get("password"));
	}
	
	@Test
	public void prioritizeProps() {

		Properties p = new Properties();
		p.setProperty("maxsize.test", "10");
		p.setProperty("maxsize.prod", "20");
		BeanConfig.prioritizeSuffix(p, ".test");
		assertEquals("10", p.getProperty("maxsize"));
		BeanConfig.prioritizeSuffix(p, ".prod");
		assertEquals("20", p.getProperty("maxsize"));
	}
	
	@Test
	public void filterAndPrioritizeProps() {

		Properties p = new Properties();
		p.setProperty("db.bean.maxsize.test", "10");
		p.setProperty("db.maxsize.test", "11");
		p.setProperty("d.maxsize.test", "12");
		p.setProperty("db.bean.maxsize.prod", "20");
		Properties f = BeanConfig.configure(null, p, "db.bean.", ".test");
		assertEquals("10", f.getProperty("maxsize"));
		f = BeanConfig.configure(null, p, "db.", ".test");
		assertEquals("11", f.getProperty("maxsize"));
		// Original properties must be unchanged.
		assertEquals(4, p.keySet().size());
		assertEquals("10", p.getProperty("db.bean.maxsize.test"));
	}
	
	@Test
	public void filterPwd() {
		
		ConfigBean factory = new SqlFactoryPwd();
		Properties props = new Properties();
		props.setProperty("jdbcUrl", "jdbc:test:url");
		props.setProperty("mypassword", "MyPwd");
		props.setProperty("connection.user", "customPropValue");
		props.setProperty("connection.password", "customPwdPropValue");
		props.setProperty("connection.AnotherProp", "AnotherPropValue");
		props.setProperty("connection.driver.special.class", "driver.special.class.value");
		String info = BeanConfig.configure(factory, props);
		log.trace(info);
		assertTrue(info.contains("user=customPropValue"));
		assertFalse(info.contains("customPwdPropValue"));
		assertTrue(info.contains("password=secret"));
		assertFalse(info.contains("MyPwd"));
		assertTrue(info.contains("setMyPassword to secret"));
		assertEquals("customPwdPropValue", factory.getConnectionProps().getProperty("password"));

		props.clear();
		info = BeanConfig.extract(factory, props);
		log.trace(info);
		assertTrue(info.contains("user=customPropValue"));
		assertFalse(info.contains("customPwdPropValue"));
		assertTrue(info.contains("password=secret"));
		assertFalse(info.contains("MyPwd"));
		assertTrue(info.contains("getMyPassword = secret"));
		assertEquals(BeanConfig.PASSWORD_VALUE, props.get("connection.password"));
		assertEquals(BeanConfig.PASSWORD_VALUE, props.get("myPassword"));
	}

	@Test
	public void testSuffixFilter() {

		ConfigBean b = new ConfigBean();
		Properties p = new Properties();
		p.setProperty("connection.user", "default-user");
		p.setProperty("connection.password", "default-pwd");
		p.setProperty("connection.password.test", "test-pwd");
		p.setProperty("connection.user.prod", "prod-user");
		p.setProperty("connection.password.prod", "prod-pwd");
		BeanConfig.configure(b, p, null, null, ".test", ".prod");
		assertEquals(b.getConnectionProps().get("user"), "default-user");
		assertEquals(b.getConnectionProps().get("password"), "default-pwd");
		assertFalse(b.getConnectionProps().contains("password.test"));
		assertFalse(b.getConnectionProps().contains("password.prod"));

		b = new ConfigBean();
		BeanConfig.configure(b, p, null, ".test", ".prod");
		assertEquals(b.getConnectionProps().get("user"), "default-user");
		assertEquals(b.getConnectionProps().get("password"), "test-pwd");
		assertFalse(b.getConnectionProps().contains("password.test"));
		assertFalse(b.getConnectionProps().contains("password.prod"));
		
		b = new ConfigBean();
		BeanConfig.configure(b, p, null, ".prod", ".test");
		assertEquals(b.getConnectionProps().get("user"), "prod-user");
		assertEquals(b.getConnectionProps().get("password"), "prod-pwd");
		assertFalse(b.getConnectionProps().contains("password.test"));
		assertFalse(b.getConnectionProps().contains("password.prod"));
	}
	
	final static class SqlFactoryPwd extends ConfigBean {
		
		private String pwd;
		public void setMyPassword(String pwd) {
			this.pwd = pwd;
		}
		public String getMyPassword() {
			return pwd;
		}
	}

}
