package nl.fw.util.jdbc.hikari;

import java.lang.management.ManagementFactory;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.pool.HikariPoolMXBean;

/**
 * Little Helper class to get access to the various HikariPool methods via JMX.
 * <br>Note: the Hikari interfaces changed name from HikariCP version 2.3.8. to 2.3.9, 
 * i.e. version 2.3.9 or higher is required, but not 2.4.x since interfaces changed name again.
 * <br>Usage of this class requires the HikariCP configuration option <code>hikari.registerMbeans=true</code>
 * <p>
 * Copied and adjusted from 
 * https://gist.githubusercontent.com/brettwooldridge/405d7c54784fc3a99813/raw/77559b05e6adc1b7a9b55ad327ad652a8bdb8767/HikariJmxElf
 * 
 * @author brettwooldridge, FWiers
 *
 */
public class HikariPoolJmx implements HikariPoolMXBean, HikariConfigMXBean {

	private final ObjectName poolAccessor;
	private final ObjectName poolConfigAccessor;
	private final MBeanServer mBeanServer;
	private final String poolName;

	public HikariPoolJmx(final String poolName) {
		this.poolName = poolName;
		try {
			mBeanServer = ManagementFactory.getPlatformMBeanServer();
			poolAccessor = new ObjectName("com.zaxxer.hikari:type=Pool (" + poolName + ")");
			poolConfigAccessor = new ObjectName("com.zaxxer.hikari:type=PoolConfig (" + poolName + ")");
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException("Pool " + poolName + " could not be found", e);
		}
	}

	@Override
	public String getPoolName() {
		return poolName;
	}

	/* *** HikariPoolMBean methods *** */

	@Override
	public int getIdleConnections() {
		return getCount("IdleConnections");
	}

	@Override
	public int getActiveConnections() {
		return getCount("ActiveConnections");
	}

	@Override
	public int getTotalConnections() {
		return getCount("TotalConnections");
	}

	@Override
	public int getThreadsAwaitingConnection() {
		return getCount("ThreadsAwaitingConnection");
	}

	protected int getCount(String attributeName) {

		try {
			return (Integer) mBeanServer.getAttribute(poolAccessor, attributeName);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void softEvictConnections() {
		callMethod("softEvictConnections");
	}

	@Override
	public void resumePool() {
		callMethod("resumePool");
	}

	@Override
	public void suspendPool() {
		callMethod("suspendPool");
	}

	protected void callMethod(String methodName) {

		try {
			mBeanServer.invoke(poolAccessor, methodName, null, null);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/* *** HikariConfigMBean methods *** */
	
	@Override
	public long getConnectionTimeout() {
		return getConfigNumber("ConnectionTimeout").longValue();
	}
	
	@Override
	public void setConnectionTimeout(long connectionTimeoutMs) {
		setConfigNumber("ConnectionTimeout", connectionTimeoutMs);
	}
	
	@Override
	public long getIdleTimeout() {
		return getConfigNumber("IdleTimeout").longValue();
	}
	
	@Override
	public void setIdleTimeout(long idleTimeoutMs) {
		setConfigNumber("IdleTimeout", idleTimeoutMs);
	}
	
	@Override
	public long getLeakDetectionThreshold() {
		return getConfigNumber("LeakDetectionThreshold").longValue();
	}
	
	@Override
	public void setLeakDetectionThreshold(long leakDetectionThresholdMs) {
		setConfigNumber("LeakDetectionThreshold", leakDetectionThresholdMs);
	}
	
	@Override
	public long getMaxLifetime() {
		return getConfigNumber("MaxLifetime").longValue();
	}
	
	@Override
	public void setMaxLifetime(long maxLifetimeMs) {
		setConfigNumber("MaxLifetime", maxLifetimeMs);
	}
	
	@Override
	public int getMinimumIdle() {
		return getConfigNumber("MinimumIdle").intValue();
	}
	
	@Override
	public void setMinimumIdle(int minIdle) {
		setConfigNumber("MinimumIdle", minIdle);
	}
	
	@Override
	public int getMaximumPoolSize() {
		return getConfigNumber("MaximumPoolSize").intValue();
	}
	
	@Override
	public void setMaximumPoolSize(int maxPoolSize) {
		setConfigNumber("MaximumPoolSize", maxPoolSize);
	}
	
	@Override
	public long getValidationTimeout() {
		return getConfigNumber("ValidationTimeout").longValue();
	}
	
	@Override
	public void setValidationTimeout(long validationTimeoutMs) {
		setConfigNumber("ValidationTimeout", validationTimeoutMs);
	}
	
	protected Number getConfigNumber(String attributeName) {
		try {
			return (Number) mBeanServer.getAttribute(poolConfigAccessor, attributeName);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	protected void setConfigNumber(String attributeName, Number value) {
		try {
			mBeanServer.setAttribute(poolConfigAccessor, new Attribute(attributeName, value));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
