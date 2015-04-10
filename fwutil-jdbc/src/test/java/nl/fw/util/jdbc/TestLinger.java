package nl.fw.util.jdbc;

import nl.fw.util.jdbc.hikari.HikPool;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests using Hikari/Javassist keep lingering in Eclipse/Maven after completion.
 * Trying to find the cause ... 
 * <br>Work around is to set "forkCount=0" for the surefire-plugin.
 * @author fred
 *
 */
public class TestLinger {

	private static final Logger log = LoggerFactory.getLogger(TestLinger.class);

	private static HikPool dbPool = null;

	@BeforeClass
	public static void beforeTest() throws Exception {

		log.debug("Before test.");
		dbPool = new HikPool();
		// this start the linger.
		// dbPool.open("db-test.properties", "db.test.");
		log.debug("Before test done.");
	}

	@AfterClass
	public static void afterTest() {

		log.debug("After test.");
		if (dbPool != null) {
			dbPool.close();
		}
		log.debug("After test done.");
	}
	
	@Test
	public void linger() {
		
		try {
			log.debug("Sleeping.");
			Thread.sleep(10L);
			// Does not help.
			// dbPool.getDs().getConnection().createStatement().executeQuery("SHUTDOWN");
			log.debug("Sleep complete.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
