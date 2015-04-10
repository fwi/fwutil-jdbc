package nl.fw.util;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class with lots of properties (formerly in Yapool 0.9.3).
 * Used for testing {@link BeanConfig}
 * @author fred
 *
 */
public class ConfigBean {

	private String jdbcDriverClass;
	
	private String jdbcUrl;
	private String poolName;
	private int transactionIsolation;
	private Properties connectionProps;
	private boolean autoCommit;
	private int validateTimeOutS;

	public String getJdbcDriverClass() {
		return jdbcDriverClass;
	}
	public void setJdbcDriverClass(String jdbcDriverClass) {
		this.jdbcDriverClass = jdbcDriverClass;
	}

	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	public String getPoolName() {
		return poolName;
	}
	public void setPoolName(String name) {
		this.poolName = name;
	}

	public int getTransactionIsolation() {
		return transactionIsolation;
	}
	public void setTransactionIsolation(int transactionIsolation) {
		this.transactionIsolation = transactionIsolation;
	}
	
	/**
	 * User or username is usually stored in the connection properties.
	 * @return the user or an empty string.
	 */
	public String getUser() {
		
		if (connectionProps == null) return "";
		String user = connectionProps.getProperty("user");
		if (user == null) {
			user = connectionProps.getProperty("username");
		}
		return (user == null ? "" : user);
	}

	public Properties getConnectionProps() {
		return connectionProps;
	}
	public void setConnectionProps(Properties connectionProps) {
		this.connectionProps = connectionProps;
	}

	public boolean isAutoCommit() {
		return autoCommit;
	}
	public void setAutoCommit(boolean autoCommit) {
		this.autoCommit = autoCommit;
	}

	public int getValidateTimeOutS() {
		return validateTimeOutS;
	}
	public void setValidateTimeOutS(int validateTimeOutS) {
		this.validateTimeOutS = validateTimeOutS;
	}

	/** 1000 milliseconds (1 second) */
	public static final long DEFAULT_PRUNE_INTERVAL = 1000L;
	/** 60 000 milliseconds (1 minute) */
	public static final long DEFAULT_MAX_IDLE_TIME = 60000L;
	/** 120 000 milliseconds (2 minutes) */
	public static final long DEFAULT_MAX_LEASE_TIME = 120000L;
	/** 1 800 000 milliseconds (30 minutes) */
	public static final long DEFAULT_MAX_LIFE_TIME = 1800000L;

	private AtomicLong pruneIntervalMs = new AtomicLong(DEFAULT_PRUNE_INTERVAL);
	private AtomicLong maxIdleTimeMs = new AtomicLong(DEFAULT_MAX_IDLE_TIME);
	private AtomicLong maxLeaseTimeMs = new AtomicLong(DEFAULT_MAX_LEASE_TIME);
	private AtomicLong maxLifeTimeMs = new AtomicLong(DEFAULT_MAX_LIFE_TIME);

	private volatile boolean logLeaseExpiredTrace;
	private volatile boolean logLeaseExpiredTraceAsWarn;
	private volatile boolean logLeaseExpiredTraceAsError;
	private volatile boolean interruptLeaser;
	private volatile boolean destroyOnExpiredLease;

	public long getPruneIntervalMs() {
		return pruneIntervalMs.get();
	}

	/**
	 * Prune interval in milliseconds at witch prune tasks are scheduled.
	 * @param pruneIntervalMs if less than zero, the pool will NOT be registered with the PoolPruner.
	 */
	public void setPruneIntervalMs(long pruneIntervalMs) {
		this.pruneIntervalMs.set(pruneIntervalMs);
	}
	
	public long getMaxIdleTimeMs() {
		return maxIdleTimeMs.get();
	}

	/**
	 * A resource that has been idle for too long, is removed from the pool.
	 * @param maxIdleTimeMs if 0, idle time never expires.
	 */
	public void setMaxIdleTimeMs(long maxIdleTimeMs) {
		if (maxIdleTimeMs >= 0L) {
			this.maxIdleTimeMs.set(maxIdleTimeMs);
		}
	}

	public long getMaxLeaseTimeMs() {
		return maxLeaseTimeMs.get();
	}

	/**
	 * A resource that has been leased for too long, is removed from the pool.
	 * @param maxLeaseTimeMs if 0, lease time never expires.
	 */
	public void setMaxLeaseTimeMs(long maxLeaseTimeMs) {
		if (maxLeaseTimeMs >= 0L) {
			this.maxLeaseTimeMs.set(maxLeaseTimeMs);
		}
	}
	
	public long getMaxLifeTimeMs() {
		return maxLifeTimeMs.get();
	}

	/**
	 * A resource that has been in use for too long, is removed from the pool.
	 * <br>The main reason for regurarly refreshing all resources is to prevent (subtle) memory leaks. 
	 * @param maxLifeTimeMs if 0, life time never expires.
	 */
	public void setMaxLifeTimeMs(long maxLifeTimeMs) {
		if (maxLifeTimeMs >= 0L) {
			this.maxLifeTimeMs.set(maxLifeTimeMs);
		}
	}

	
	public boolean isLogLeaseExpiredTrace() {
		return logLeaseExpiredTrace;
	}

	public void setLogLeaseExpiredTrace(boolean logLeaseExpiredTrace) {
		this.logLeaseExpiredTrace = logLeaseExpiredTrace;
	}

	public boolean isLogLeaseExpiredTraceAsWarn() {
		return logLeaseExpiredTraceAsWarn;
	}

	public void setLogLeaseExpiredTraceAsWarn(boolean logLeaseExpiredTraceAsWarn) {
		this.logLeaseExpiredTraceAsWarn = logLeaseExpiredTraceAsWarn;
		if (logLeaseExpiredTraceAsWarn) setLogLeaseExpiredTrace(true);
	}

	public boolean isLogLeaseExpiredTraceAsError() {
		return logLeaseExpiredTraceAsError;
	}

	public void setLogLeaseExpiredTraceAsError(boolean logLeaseExpiredTraceAsError) {
		this.logLeaseExpiredTraceAsError = logLeaseExpiredTraceAsError;
		if (logLeaseExpiredTraceAsError) setLogLeaseExpiredTrace(true);
	}

	public boolean isInterruptLeaser() {
		return interruptLeaser;
	}

	/**
	 * If set to true, the thread that leased a resource for too long is interrupted.
	 */
	public void setInterruptLeaser(boolean interruptLeaser) {
		this.interruptLeaser = interruptLeaser;
		if (interruptLeaser) {
			setLogLeaseExpiredTraceAsError(true);
		}
	}

	public boolean isDestroyOnExpiredLease() {
		return destroyOnExpiredLease;
	}

	/**
	 * If set to true, a resource that is removed from the pool after it was leased for too long,
	 * will be destroyed by the resource-factory.
	 * <br>Default set to false because this can also destroy a a resource that is still in use
	 * (an expired lease indicates something is wrong, but the resource could still be in use).
	 * And also because expired resources that are eventually released to the pool,
	 * are always closed by the pool factory. 
	 * <br>Set to true if all resources really need to be destroyed/closed by the factory
	 * in a timely manner, and expired leases are frequent.
	 */
	public void setDestroyOnExpiredLease(boolean destroyOnExpiredLease) {
		this.destroyOnExpiredLease = destroyOnExpiredLease;
	}

	private volatile boolean syncCreation;
	private volatile int minSize = 0;
	private volatile int maxSize = 4;

	public int getMinSize() { 
		return minSize; 
	}

	public void setMinSize(int minSize) {
		if (minSize >= 0) {
			this.minSize = minSize;
			if (maxSize < minSize) maxSize = minSize;
		}
	}
	
	public int getMaxSize() { 
		return maxSize; 
	}
	
	public void setMaxSize(int maxSize) {
		if (maxSize > 0) {
			this.maxSize = maxSize;
			if (minSize > maxSize) minSize = maxSize;
		}
	}

	public boolean isSyncCreation() {
		return syncCreation;
	}

	public void setSyncCreation(boolean syncCreation) {
		this.syncCreation = syncCreation;
	}

	private AtomicLong maxAcquireTimeMs = new AtomicLong();
	private volatile boolean closed;
	private boolean fair;
	
	public long getMaxAcquireTimeMs() {
		return maxAcquireTimeMs.get();
	}

	public void setMaxAcquireTimeMs(long maxAcquireTimeMs) {
		if (maxAcquireTimeMs >= 0L) {
			this.maxAcquireTimeMs.set(maxAcquireTimeMs);
		}
	}
	
	/**
	 * If true, the thread waiting the longest for a resource will get the first available resource.
	 * If false, whichever thread is fastest to give the first avialable resource to will get the resource.
	 */
	public boolean isFair() {
		return fair;
	}

	public void setFair(boolean fair) {
		
		if (fair != isFair()) {
			this.fair = fair;
		}
	}

	public boolean isClosed() { 
		return closed;
	}

}
