package nl.fw.util.jdbc.hikari;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import nl.fw.util.jdbc.DbConnUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Utility class for opening and closing a Hikari-pool 
 * and providing related pool functions such as 
 * {@link #setLogPoolUsage(boolean)}
 * and {@link #getDataSource()}.
 * @author fred
 *
 */
public class HikPool implements Closeable {
	
	private static final Logger log = LoggerFactory.getLogger(HikPool.class);
	private static final AtomicInteger poolNumber = new AtomicInteger();

	/**
	 * The property name specifying the pool name.
	 */
	public static final String POOL_NAME_PROP = "poolName";
	
	/**
	 * The properties file-name containing the default values for all pools. 
	 * Set to null to skip loading default properties.
	 */
	public static String DB_DEFAULTS_FILE = "db-defaults.properties";

	private HikariDataSource hds;
	private String dbName;
	private boolean logPoolUsage;
	private HikPoolUsageLogger poolUsageLogger;
	private long reportIntervalMs = 10000L;

	/**
	 * Calls {@link #loadDbProps(String, String)} with no prefix filtering.
	 */
	public Properties loadDbProps(String propsFileName) throws IOException {
		return loadDbProps(propsFileName, null);
	}

	/**
	 * Loads database connection properties by combining the {@link #DB_DEFAULTS_FILE} properties
	 * with the properties found in given {@code propsFileName} (UTF-8 encoding is assumed). 
	 * If the {@code DB_DEFAULTS_FILE} has a value and does not exist or properties could not be loaded, 
	 * only a warning is logged.
	 * <br>If the {@code prefix} has a value, only the properties starting with the given prefix
	 * are loaded from {@code propsFileName}. All other properties are ignored.
	 * <br>If {@code propsFileName} does not exist, an error is thrown (see also {@link #loadProps(String)}).
	 * <br>The {@link #getPoolName()} is set to the value of the {@link #POOL_NAME_PROP}.
	 * If no such property value exists, a pool-name is generated in the format {@code dbpool-<number>} and set. 
	 * @param propsFileName The resource name containing the properties for the database pool.
	 * @param prefix The required prefix for properties to load from {@code propsFileName}. 
	 * A {@code null} value indicates no prefix-filtering.
	 * @return The combined and possibly filtered properties loaded from resources {@code DB_DEFAULTS_FILE} and {@code propsFileName}.
	 * @throws IOException When properties could not be loaded from {@code propsFileName}.
	 */
	public Properties loadDbProps(String propsFileName, String prefix) throws IOException {
		
		Properties dbProps = null;
		if (DB_DEFAULTS_FILE != null && DB_DEFAULTS_FILE.length() > 0) {
			try {
				dbProps = loadProps(DB_DEFAULTS_FILE);
			} catch (Exception e) {
				log.warn("Unable to load default database properties from file [" + DB_DEFAULTS_FILE + "]", e);
			}
		}
		Properties dbEnvProps = loadProps(propsFileName);
		if (prefix != null && prefix.length() > 0) {
			Properties tmpProps = new Properties();
			Enumeration<?> pnames = dbEnvProps.propertyNames();
			while (pnames.hasMoreElements()) {
				String pname = pnames.nextElement().toString();
				if (pname.startsWith(prefix)) {
					tmpProps.setProperty(pname.substring(prefix.length()), dbEnvProps.getProperty(pname));
				}
			}
			dbEnvProps = tmpProps;
		}
		dbProps.putAll(dbEnvProps);
		setDbName(dbProps);
		return dbProps;
	}
	
	/**
	 * Sets {@code dbName} to the value of the {@link #POOL_NAME_PROP}.
	 * If no such property value exists, a pool-name is generated in the format {@code dbpool-<number>} and set. 
	 * @param dbProps The database connection pool configuration properties, 
	 * updated with a {@link #POOL_NAME_PROP} if no such value existed. 
	 */
	protected void setDbName(Properties dbProps) {
		
		if (dbProps.get(POOL_NAME_PROP) == null) {
			dbProps.put(POOL_NAME_PROP, "dbpool-" + poolNumber.incrementAndGet());
		}
		dbName = dbProps.getProperty(POOL_NAME_PROP);
	}
	
	/**
	 * Opens a Hikari database connection pool configured using the given properties.
	 * To ensure a database connection can be created set <tt>initializationFailFast=true</tt> in the properties.
	 * To enable pool usage logging, call {@link #setLogPoolUsage(boolean)} before calling this method.
	 * @param dbProps The properties used to configure the connection pool (see also {@link HikariConfig#HikariConfig(String)}. 
	 */
	public void open(Properties dbProps) {
		
		setDbName(dbProps);
		HikariConfig hc = new HikariConfig(dbProps);
		// open the pool
		hds = new HikariDataSource(hc);
		// if initializationFailFast is true, a runtime-exception was thrown if the pool could not be opened. 
		if (isLogPoolUsage()) {
			// More about JMX and MBeans is explained at 
			// https://github.com/brettwooldridge/HikariCP/wiki/MBean-(JMX)-Monitoring-and-Management
			poolUsageLogger = new HikPoolUsageLogger(hds.getHikariPoolMXBean(), hc.getPoolName());
			poolUsageLogger.setReportInterval(reportIntervalMs);
			poolUsageLogger.start();
		}
	}
	
	/**
	 * Returns the (Hikari) datasource that hands out pool-connections (in a thread-safe manner).
	 */
	public HikariDataSource getDataSource() {
		return hds;
	}

	/**
	 * Stop the {@link HikPoolUsageLogger} if it is running and closes the pool.
	 * Any thrown exceptions are catched and logged as warning.
	 */
	@Override
	public void close() {
		
		if (poolUsageLogger != null) {
			poolUsageLogger.stop();
			poolUsageLogger = null;
		}
		if (hds != null) {
			try { hds.close(); } catch (Exception e) {
				log.warn("Closing database pool " + dbName + " failed.", e);
			}
			hds = null;
		}
	}
	
	/* *** Convenience methods *** */
	
	public int getActiveConnections() {
		return hds.getHikariPoolMXBean().getActiveConnections();
	}

	/* *** Bean methods *** */
	
	/**
	 * The pool name (set via property {@link #POOL_NAME_PROP}) or the generated name for the pool.
	 * See also {@link #setDbName(Properties)}.
	 */
	public String getPoolName() {
		return dbName;
	}

	public boolean isLogPoolUsage() {
		return logPoolUsage;
	}

	/**
	 * If true, pool usage is logged every 10 seconds if the pool is used.
	 * See also {@link #setReportIntervalMs(long)}.
	 */
	public void setLogPoolUsage(boolean logPoolUsage) {
		this.logPoolUsage = logPoolUsage;
	}

	public long getReportIntervalMs() {
		return reportIntervalMs;
	}

	/**
	 * The interval in milliseconds at which pool usage is logged.
	 * See also {@link #setLogPoolUsage(boolean)}.
	 */
	public void setReportIntervalMs(long reportIntervalMs) {
		this.reportIntervalMs = reportIntervalMs;
	}
	
	/* *** Static methods *** */
	
	/**
	 * Reads properties from a resource using UTF-8 encoding.
	 * Uses {@link DbConnUtil#getResourceAsStream(String)} to find and open the properties file/resource.
	 * @param propsFileName The (file-) name of the resource containing the properties to load.
	 * @return The properties loaded from resource.
	 * @throws IOException When properties failed to load.
	 * @throws FileNotFoundException When resource could not be found.
	 */
	public static Properties loadProps(String propsFileName) throws IOException {
		
		Properties props = new Properties();
		InputStream in = DbConnUtil.getResourceAsStream(propsFileName);
		if (in == null) {
			throw new FileNotFoundException("Unable to find resource [" + propsFileName + "]");
		}
		try {
			// no need to use a buffered-stream, the properties-class uses an internal buffered "linereader"
			props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} finally {
			try { in.close(); } catch (Exception e) {
				log.warn("Failed to close properties resource [" + propsFileName + "] - " + e);
			}
		}
		return props;
	}
	
}
