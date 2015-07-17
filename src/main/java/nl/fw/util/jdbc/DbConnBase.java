package nl.fw.util.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for a fluent API for using plain JDBC database connections.
 * This class is extendable, see the extending class {@link DbConnStatement} for example.
 * <br>This class and extending classes try to track and close resources as best as possible,
 * but a certain way of using this class is required. See {@link DbConn} and the test-classes for examples.
 * <br>This class is <b>not thread-safe</b>. It should be created and disposed of 
 * in the context of a "business transaction" (a group of database actions performed to support a task).  
 * <p>
 * The idea behind the extendable fluent API is that each class can take responsibility 
 * for a part of the implementation and hide the details. This prevents creating one
 * big class that is difficult to extend and at the same time allows for extending
 * the class in any direction that may be required by the application using this API. 
 * 
 * @author fred
 *
 * @param <DBCONN> the class itself, always returned by {@link #me()}.
 */
public class DbConnBase<DBCONN extends DbConnBase<DBCONN>> implements AutoCloseable {
	
	private static final Logger log = LoggerFactory.getLogger(DbConnBase.class);
	
	private DataSource dataSource;
	private Connection connection;
	
	/**
	 * Instead of returning {@code this}, always return a call to this method {@code me()}
	 * so that the top-level implementation is always returned to the user. 
	 * @return Top-level implementation of DBCONN.
	 */
	@SuppressWarnings("unchecked")
	protected final DBCONN me() { return (DBCONN) this; } 
	
	/**
	 * The datasource to get a connection from.
	 * If a connection is already used, {@link #rollbackAndClose()} is called first.
	 * <br>Typically, an application sets a datasource when a connection pool is used
	 * and sets a connection when no pool is used.
	 */
	public DBCONN setDataSource(DataSource ds) {
		
		if (getActiveConnection() != null) {
			rollbackAndClose();
		}
		this.dataSource = ds;
		return me();
	}
	
	public DataSource getDataSource() {
		return dataSource;
	}
	
	/**
	 * The connection to use for queries.
	 * If a connection is already used, {@link #rollbackAndClose()} is called first.
	 * <br>Typically, an application sets a connection when no connection pool is used
	 * and sets a datasource when a connection pool is used.
	 * See also {@link #getConnection()}.
	 */
	public DBCONN setConnection(Connection conn) {
		
		if (getActiveConnection() != null) {
			rollbackAndClose();
		}
		setActiveConnection(conn);
		return me();
	}
	
	/**
	 * If a connection is not already in use or set, 
	 * this method will fetch a connection from the datasource if a datasource is set.
	 */
	public Connection getConnection() throws SQLException {
		
		if (getActiveConnection() == null && getDataSource() != null) {
			setActiveConnection(getDataSource().getConnection());
		}
		return getActiveConnection();
	}

	/**
	 * Returns the connection currently being used (can be null).
	 * <br>See also {@link #getConnection()}.
	 */
	protected Connection getActiveConnection() {
		return connection;
	}
	
	/**
	 * Sets the current active connection directly, no close/cleanup methods are called.
	 * <br>See also {@link #setConnection(Connection)}.
	 */
	protected void setActiveConnection(Connection conn) {
		this.connection = conn;
	}

	/**
	 * Commits the transaction (if any) but does not close the underlying connection.
	 */
	public DBCONN commit() throws SQLException {
		
		if (getActiveConnection() == null) {
			// Use debug-level: commit for nothing can happen (e.g. 0 records inserted),
			// but there is also a small chance of programming error.
			log.debug("Nothing to commit, no connection open.");
		} else if (getActiveConnection().getAutoCommit()) {
			log.trace("Nothing to commit in auto-commit mode.");
		} else {
			getActiveConnection().commit();
		}
		return me();
	}

	/**
	 * Commits the transaction (if any) and always closes the underlying connection.
	 * <br>If the commit fails, the transaction is rolled back and a {@code SQLException} is thrown.
	 */
	public DBCONN commitAndClose() throws SQLException {
		
		SQLException commitError = null;
		try {
			commit();
		} catch (SQLException e) {
			commitError = e;
		} finally {
			if (commitError == null) {
				close(true, null);
			} else {
				// after a commit fails, always roll back else connection might keep transaction open
				// and retry the transaction with next commit.
				// HikariCP does the rollback on connection close if a transaction is open,
				// but it is better not to rely on the pool implementation.
				rollbackAndClose();
				throw commitError;
			}
		}
		return me();
	}
	
	/**
	 * Rolls back the transaction (if any) but does not close the underlying connection.
	 * Never throws an exception.
	 */
	public DBCONN rollbackSilent() {
		
		try {
			rollback();
		} catch (Exception e) {
			log.warn("Failed to rollback transaction.", e);
		}
		return me();
	}

	/**
	 * Rolls back the transaction (if any) but does not close the underlying connection.
	 */
	public DBCONN rollback() throws SQLException {
		
		if (getActiveConnection() == null) {
			log.trace("Nothing to rollback, no connection open.");
		} else if (getActiveConnection().getAutoCommit()) {
			log.trace("Nothing to rollback in auto-commit mode.");
		} else {
			getActiveConnection().rollback();
		}
		return me();
	}

	/**
	 * Rolls back the transaction and always closes any used connection.
	 * Never throws an exception.
	 */
	public DBCONN rollbackAndClose() {
		return rollbackAndClose(null);
	}
	
	/**
	 * Rolls back the transaction and always closes any used connection.
	 * Only throws a runtime-exception if parameter "rethrow" is not null.
	 */
	public DBCONN rollbackAndClose(Throwable rethrow) {
		
		rollbackSilent();
		close(true, rethrow);
		return me();
	}

	/**
	 * Closes any used connection after calling rollback.
	 * To skip the rollback, use {@link #commitAndClose()}. 
	 * Never throws an exception.
	 */
	@Override
	public void close() {
		close(null);
	}

	/**
	 * Closes any used connection after calling rollback.
	 * To skip the rollback, use {@link #commitAndClose()}. 
	 * Only throws a runtime-exception if parameter "rethrow" is not null.
	 */
	public void close(Throwable rethrow) {
		close(false, rethrow);
	}

	/**
	 * For use in extending classes. This method is always called when any of the close-methods in this class is called.
	 * Uses {@link DbConnUtil#rethrowRuntime(Throwable)}.
	 * @param afterCommitOrRollback If false, {@link #rollbackSilent()} is called. 
	 * If true, classes extending this method may skip some cleanup as it is already done in the {@link #rollback()} or {@link #commit()} methods.
	 * @param rethrow Rethrown when not null using {@link DbConnUtil#rethrowRuntime(Throwable)}. 
	 */
	protected void close(boolean afterCommitOrRollback, Throwable rethrow) {
		
		if (getActiveConnection() != null) {
			if (!afterCommitOrRollback) {
				rollbackSilent();
			}
			try {
				getActiveConnection().close();
			} catch (Exception se) {
				log.warn("Failed to close connection.", se);
			} finally {
				setActiveConnection(null);
			}
		}
		DbConnUtil.rethrowRuntime(rethrow);
	}
	
}
