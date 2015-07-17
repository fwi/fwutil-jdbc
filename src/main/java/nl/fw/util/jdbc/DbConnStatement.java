package nl.fw.util.jdbc;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds statement and prepared statement resource management to plain JDBC connections. 
 * See also {@link DbConnBase}.
 */
public class DbConnStatement<DBCONN extends DbConnStatement<DBCONN>> 
	extends DbConnBase<DBCONN> {
	
	private static final Logger log = LoggerFactory.getLogger(DbConnStatement.class);

	private INamedQuery namedQueries;
	private Statement statement;
	private String query;
	private int resultCount;
	private int[] resultCountBatch;
	private ResultSet resultSet;
	private boolean returnGeneratedKeys;
	private boolean callable;
	private boolean plain;
	private boolean prepared;

	/* *** getters and setters *** */
	public DBCONN setNamedQueries(INamedQuery namedQueries) { this.namedQueries = namedQueries; return me(); }
	public INamedQuery getNamedQueries() { return namedQueries; }
	public boolean isPlainStatement() { return plain; }
	protected void setPlain(boolean plain) { this.plain = plain; }
	public boolean isCallableStatement() { return callable; }
	protected void setCallable(boolean callable) { this.callable = callable; }
	public boolean isPreparedStatement() { return prepared; }
	protected void setPrepared(boolean prepared) { this.prepared = prepared; }
	public boolean isReturnGeneratedKeys() { return returnGeneratedKeys; }
	protected void setReturnGeneratedKeys(boolean gkey) { this.returnGeneratedKeys = gkey; }
	/** The last used sql-query. */
	public String getQuery() { return query; }
	protected void setQuery(String query) { this.query = query; }
	public int getResultCount() { return resultCount; }
	protected void setResultCount(int resultCount) { this.resultCount = resultCount; }
	public int[] getResultCountBatch() { return resultCountBatch; }
	protected void setResultCountBatch(int[] resultCountBatch) { this.resultCountBatch = resultCountBatch; }
	public ResultSet getResultSet() { return resultSet; }
	protected void setResultSet(ResultSet resultSet) { this.resultSet = resultSet; }

	public Statement getStatement() { return statement;	}
	protected void setStatement(Statement statement) { this.statement = statement; }
	
	public CallableStatement getCallableStatement() {
		return (isCallableStatement() ? (CallableStatement) statement : null);
	}

	public PreparedStatement getPreparedStatement() {
		return (isPreparedStatement() ? (PreparedStatement) statement : null);
	}

	/**
	 * Returns true if a statement is set/created.
	 */
	public boolean haveStatement() {
		return (isCallableStatement() || isPlainStatement() || isPreparedStatement());
	}
	
	/**
	 * If named queries is set (see {@link #setNamedQueries(INamedQuery)}),
	 * looks up the named query else returns the given sql statement.
	 * <br>Calls {@link DbConnStatement#setQuery(String)}. 
	 */
	public String getQuery(String sql) {

		String query = (getNamedQueries() == null ? sql : getNamedQueries().getQuery(sql));
		setQuery(query);
		return query;
	}

	/**
	 * Creates a plain statement after closing any open query resources.
	 */
	public DBCONN createStatement() throws SQLException {
		
		closeQueryResources();
		setPlain(true);
		setStatement(getConnection().createStatement());
		return me();
	}

	/**
	 * Creates a prepared statement after closing any open query resources.
	 */
	public DBCONN prepareCall(String sql) throws SQLException {
		
		closeQueryResources();
		setCallable(true);
		setStatement(getConnection().prepareCall(getQuery(sql)));
		return me();
	}

	/**
	 * Creates a prepared statement after closing any open query resources.
	 */
	public DBCONN prepareStatement(String sql) throws SQLException {
		return prepareStatement(sql, false);
	}

	/**
	 * Creates a prepared statement after closing any open query resources.
	 * @param returnGeneratedKeys If true, generated keys are fetched after query is executed.
	 */
	public DBCONN prepareStatement(String sql, boolean returnGeneratedKeys) throws SQLException {
		return prepareStatement(sql, (returnGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS));
	}

	/**
	 * Creates a prepared statement after closing any open query resources.
	 * @param returnGeneratedKeys either {@link Statement#RETURN_GENERATED_KEYS} or {@link Statement#NO_GENERATED_KEYS}. 
	 */
	public DBCONN prepareStatement(String sql, int returnGeneratedKeys) throws SQLException {
		
		closeQueryResources();
		setPrepared(true);
		setReturnGeneratedKeys(returnGeneratedKeys == Statement.RETURN_GENERATED_KEYS);
		setStatement(getConnection().prepareStatement(getQuery(sql), returnGeneratedKeys)); 
		return me();
	}

	/**
	 * Executes an update query and sets the {@link #getResultCount()}.
	 * If {@link #isReturnGeneratedKeys()} is true,
	 * the generated keys resultset is also fetched and available in {@link #getResultSet()}.
	 */
	public DBCONN executeUpdate() throws SQLException {
		
		return executeUpdate(null);
	}

	/**
	 * Executes an update query using the previously created statement.
	 * See also {@link #executeUpdate()}.
	 */
	public DBCONN executeUpdate(String sql) throws SQLException {
		
		if (isPlainStatement()) {
			if (sql == null) {
				throw new SQLException("Cannot execute an update for a statement without a sql-query.");
			} else {
				setResultCount(getStatement().executeUpdate(getQuery(sql)));
				if (isRegisterGeneratedKeys()) {
					setResultSet(getStatement().getGeneratedKeys());
				}
			}
		}
		if (isCallableStatement()) {
			if (sql == null) {
				setResultCount(getCallableStatement().executeUpdate());
				if (isRegisterGeneratedKeys()) {
					setResultSet(getCallableStatement().getGeneratedKeys());
				}
			} else {
				throw new SQLException("Cannot execute an update for a callable statement with another sql-query.");
			}
		}
		if (isPreparedStatement()) {
			if (sql == null) {
				setResultCount(getPreparedStatement().executeUpdate());
				if (isRegisterGeneratedKeys()) {
					setResultSet(getPreparedStatement().getGeneratedKeys());
				}
			} else {
				throw new SQLException("Cannot execute an update for a prepared statement with another sql-query.");
			}
		}
		if (!haveStatement()) {
			throw new SQLException("There is no statement set to perform a sql query with.");
		}
		return me();
	}
	
	/**
	 * True if result count > 0 and generated keys are returned.
	 */
	protected boolean isRegisterGeneratedKeys() {
		return (getResultCount() > 0 && isReturnGeneratedKeys());
	}
	
	/**
	 * Executes a select query and makes the results available in {@link #getResultSet()}.
	 */
	public DBCONN executeQuery() throws SQLException {

		return executeQuery(null);
	}
	
	/**
	 * Executes a select query using the previously created statement
	 * and makes the results available in {@link #getResultSet()}.
	 */
	public DBCONN executeQuery(String sql) throws SQLException {

		if (isPlainStatement()) {
			if (sql == null) {
				throw new SQLException("Cannot execute a query for a statement without a sql-query.");
			} else {
				setResultSet(getStatement().executeQuery(getQuery(sql)));
			}
		}
		if (isCallableStatement()) {
			if (sql == null) {
				setResultSet(getCallableStatement().executeQuery());
			} else {
				throw new SQLException("Cannot execute a query for a callable statement with another sql-query.");
			}
		}
		if (isPreparedStatement()) {
			if (sql == null) {
				setResultSet(getPreparedStatement().executeQuery());
			} else {
				throw new SQLException("Cannot execute a query for a prepared statement with another sql-query.");
			}
		}
		if (!haveStatement()) {
			throw new SQLException("There is no statement set to perform a sql query with.");
		}
		return me();
	}
	
	/**
	 * Add a set of parameters to the batch of commands. 
	 */
	public DBCONN addBatch() throws SQLException {
		
		return addBatch(null);
	}
	
	/**
	 * Add a set of parameters to the batch of commands, or, if sql is not null,
	 * add the sql command to the list of batch-commands of a plain statement.
	 */
	public DBCONN addBatch(String sql) throws SQLException {
		
		if (isPlainStatement()) {
			if (sql == null) {
				throw new SQLException("Cannot add a batch query for a statement without a sql-query.");
			} else {
				getStatement().addBatch(getQuery(sql));
			}
		}
		if (isCallableStatement()) {
			if (sql == null) {
				getCallableStatement().addBatch();
			} else {
				throw new SQLException("Cannot add a batch query for a callable statement with another sql-query.");
			}
		}
		if (isPreparedStatement()) {
			if (sql == null) {
				getPreparedStatement().addBatch();
			} else {
				throw new SQLException("Cannot add a batch query for a prepared statement with another sql-query.");
			}
		}
		if (!haveStatement()) {
			throw new SQLException("There is no statement set to add a batch to.");
		}
		return me();
	}
	
	/**
	 * Executes a batch and makes the results available in {@link #getResultCountBatch()}.
	 */
	public DBCONN executeBatch() throws SQLException {
		
		if (isPlainStatement()) {
			setResultCountBatch(getStatement().executeBatch());
		}
		if (isCallableStatement()) {
			setResultCountBatch(getCallableStatement().executeBatch());
		}
		if (isPreparedStatement()) {
			setResultCountBatch(getPreparedStatement().executeBatch());
		}
		if (!haveStatement()) {
			throw new SQLException("There is no statement set to execute a batch from.");
		}
		return me();
	}

	
	/**
	 * Closes all open query resources (if there are any) and resets variables to default values.
	 * Does not close the connection.
	 * <br>Does nothing when {@link #getActiveConnection()} returns null.
	 */
	public DBCONN closeQueryResources() {
		
		if (getActiveConnection() == null) {
			return me();
		}
		closeStatement();
		setResultCount(0);
		setResultCountBatch(null);
		setQuery(null);
		setReturnGeneratedKeys(false);
		setPrepared(false);
		setPlain(false);
		setCallable(false);
		return me();
	}
	
	/**
	 * Closes the result-set and the statement (if there are any).
	 */
	public DBCONN closeStatement() {

		closeResultSet();
		if (getStatement() != null) {
			try {
				getStatement().close();
			} catch (Exception e) {
				log.warn("Failed to close statement.", e);
			}
			setStatement(null);
		}
		return me();
	}		

	/**
	 * Closes the result-set (if there is any).
	 */
	public DBCONN closeResultSet() {
		
		if (getResultSet() != null) {
			try {
				getResultSet().close();
			} catch (Exception e) {
				log.warn("Failed to close result set.", e);
			}
			setResultSet(null);
		}
		return me();
	}

	/**
	 * Closes all open resources and commits the transaction (if any).
	 * Does not close the underlying connection.
	 * <br>To commit without closing open resources, call {@link #getActiveConnection()} 
	 * and call commit on the returned connection. 
	 */
	@Override
	public DBCONN commit() throws SQLException {
		
		closeQueryResources();
		return super.commit();
	}
	
	@Override
	public DBCONN rollback() throws SQLException {

		closeQueryResources();
		return super.rollback();
	}

	@Override
	protected void close(boolean afterCommitOrRollback, Throwable rethrow) {
		
		if (!afterCommitOrRollback) {
			closeQueryResources();
		}
		super.close(afterCommitOrRollback, rethrow);
	}
}
