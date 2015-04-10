package nl.fw.util.jdbc;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import nl.fw.util.jdbc.DbConn;
import nl.fw.util.jdbc.NamedQuery;
import nl.fw.util.jdbc.hikari.DbConnHik;
import nl.fw.util.jdbc.hikari.HikPool;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens an in-memory database and uses (named) queries loaded from sql-files 
 * to set the initial structure and data of the database and query and update the database. 
 * @author fred
 *
 */
public class TestNamed {

	private static final Logger log = LoggerFactory.getLogger(TestNamed.class);
	private static HikPool dbPool = null;

	@BeforeClass
	public static void openDb() throws Exception {

		dbPool = new HikPool();
		// test the pool usage logger as well.
		dbPool.setLogPoolUsage(true);
		dbPool.setReportIntervalMs(10L);
		dbPool.open(dbPool.loadDbProps("db-test.properties", "db.test."));
		log.debug("Database opened.");
	}

	@AfterClass
	public static void closeDb() {

		if (dbPool != null) {
			dbPool.close();
		}
		log.debug("Database closed.");
	}

	@Test
	public void named() throws Exception {

		// Create database structure and load initial data.
		DbConnHik dbc = new DbConnHik(dbPool);
		assertNoConnectionsInUse();
		loadAndUpdate(dbc, "db-test-hsql-struct.sql");
		loadAndUpdateBatch(dbc, "db-test-data.sql");
		assertNoConnectionsInUse();
		NamedQuery namedQueries = new NamedQuery(NamedQuery.loadQueries("db-test-queries.sql"));
		assertTrue(namedQueries.getSize() > 0);
		dbc.setNamedQueries(namedQueries);
		assertNoConnectionsInUse();
		
		// Use a DAO to perform database operations.
		NamedDao dao = new NamedDao(dbc);
		assertEquals("Code value util-jdbc", "1.0.0-SNAPSHOT", dao.getCodeValue("nl.fw.util.jdbc.version"));
		assertNoConnectionsInUse();
		dao.setCodeValue("testing", "123");
		assertEquals("New code value testing", "123", dao.getCodeValue("testing"));
		dao.setCodeValue("testing", "1234");
		assertEquals("Updated code value testing", "1234", dao.getCodeValue("testing"));
		assertEquals("User names", 2, dao.loadUserNames().size());
		
		checkNonNamedQuery(dao);
		checkAutoCloseable();
		assertNoConnectionsInUse();
	}
	
	private void loadAndUpdate(DbConnHik dbc, String rname) {
		
		log.debug("Loading statements from " + rname);
		try {
			LinkedHashMap<String, String> qmap = NamedQuery.loadQueries(rname);
			for (Map.Entry<String, String> sqlEntry : qmap.entrySet()) {
				log.debug("Executing sql query " + sqlEntry.getKey());
				int updateCount = dbc.createStatement().executeUpdate(sqlEntry.getValue()).getResultCount();
				log.debug("Result count: " + updateCount);
			}
			dbc.commitAndClose();
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
	}
	
	private void loadAndUpdateBatch(DbConnHik dbc, String rname) {
		
		log.debug("Loading statements from " + rname);
		try {
			LinkedHashMap<String, String> qmap = NamedQuery.loadQueries(rname);
			dbc.createStatement();
			for (Map.Entry<String, String> sqlEntry : qmap.entrySet()) {
				log.debug("Adding sql query " + sqlEntry.getKey());
				dbc.addBatch(sqlEntry.getValue());
			}
			int[] batchUpdateCount = dbc.executeBatch().getResultCountBatch();
			log.debug("Result count batch: " + Arrays.toString(batchUpdateCount));
			dbc.commitAndClose();
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
	}
	
	private void checkNonNamedQuery(NamedDao dao) {
		
		final String bogusQName = "QUERY_DOES_NOT_EXIST";
		try {
			dao.useNonNamedQuery(bogusQName);
			fail("Should have gotton a runtime exception for bogus query.");
		} catch (Exception e) {
			log.debug("Query failed: " + e);
			assertTrue(e.toString().contains(bogusQName));
		}
	}
	
	private void checkAutoCloseable() {
		
		log.debug("Testing with auto-closeable");
		assertNoConnectionsInUse();
		try {
			try (DbConn dbc = new DbConn(dbPool.getDataSource())) {
				dbc.createStatement().executeQuery("select * from settings");
				dbc.commitAndClose();
			}
			// Should show only COMMIT in the test-output
		} catch (Exception e) {
			fail("Auto-cloaseable test query failed: " + e);
		}
		assertNoConnectionsInUse();
		log.debug("Testing auto-closeable with non-existing table name");
		final String bogusTableName = "DOES_NOT_EXIST";
		try {
			try (DbConn dbc = new DbConn(dbPool.getDataSource())) {
				dbc.createStatement().executeQuery("select * from " + bogusTableName);
				fail("Expecting a SQL error");
			}
			// Should show only ROLLBACK in the test-output
		} catch (Exception e) {
			assertTrue(e.toString().contains(bogusTableName));
		}
		assertNoConnectionsInUse();
	}
	
	private void assertNoConnectionsInUse() {
		
		assertEquals("No connections in use", 0, dbPool.getJmx().getActiveConnections());
	}

}
