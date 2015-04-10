package nl.fw.util.jdbc;

import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds named (prepared) statement resource management to plain JDBC connections. 
 * This class is extendable similar to how this class extends {@link DbConnStatement}.
 * For "ease of use" create an endpoint class like {@link nl.fw.util.jdbc.hikari.DbConnHik}.
 */
public class DbConnNamedStatement <DBCONN extends DbConnNamedStatement<DBCONN>> 
	extends DbConnStatement<DBCONN> {
	
	private static final Logger log = LoggerFactory.getLogger(DbConnNamedStatement.class);

	private NamedParameterStatement namedStatement;
	private boolean named;
	
	public boolean isNamedStatement() { return named; }
	protected void setNamed(boolean named) { this.named = named; }
	public NamedParameterStatement getNamedStatement() { return namedStatement; }
	protected void setNamedStatement(NamedParameterStatement namedStatement) { this.namedStatement = namedStatement; }

	@Override
	public boolean haveStatement() {
		return (super.haveStatement() || isNamedStatement());
	}

	/**
	 * Creates a named parameter statement after closing any open query resources.
	 */
	public DBCONN nameStatement(String sql) throws SQLException {
		return nameStatement(sql, false);
	}

	/**
	 * Creates a named parameter statement after closing any open query resources.
	 * @param returnGeneratedKeys If true, generated keys are fetched after query is executed.
	 */
	public DBCONN nameStatement(String sql, boolean returnGeneratedKeys) throws SQLException {
		return nameStatement(sql, (returnGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS));
	}

	/**
	 * Creates a named parameter statement after closing any open query resources.
	 * @param returnGeneratedKeys either {@link Statement#RETURN_GENERATED_KEYS} or {@link Statement#NO_GENERATED_KEYS}. 
	 */
	public DBCONN nameStatement(String sql, int returnGeneratedKeys) throws SQLException {
		
		closeQueryResources();
		setNamed(true);
		setReturnGeneratedKeys(returnGeneratedKeys == Statement.RETURN_GENERATED_KEYS);
		setNamedStatement(new NamedParameterStatement(getConnection(), getQuery(sql)));
		return me();
	}
	
	@Override
	public DBCONN executeUpdate(String sql) throws SQLException {
		
		if (isNamedStatement()) {
			if (sql == null) {
				setResultCount(getNamedStatement().executeUpdate());
			} else {
				throw new SQLException("Cannot execute an update for a named statement with another sql-query.");
			}
		}
		return super.executeUpdate(sql);
	}

	@Override
	public DBCONN executeQuery(String sql) throws SQLException {
		
		if (isNamedStatement()) {
			if (sql == null) {
				setResultSet(getNamedStatement().executeQuery());
			} else {
				throw new SQLException("Cannot execute a query for a named statement with another sql-query.");
			}
		}
		return super.executeQuery(sql);
	}
	
	@Override
	public DBCONN addBatch(String sql) throws SQLException {
		
		if (isNamedStatement()) {
			if (sql == null) {
				getNamedStatement().addBatch();
			} else {
				throw new SQLException("Cannot add a batch query for a named statement with another sql-query.");
			}
		}
		return super.addBatch(sql);
	}

	@Override
	public DBCONN executeBatch() throws SQLException {
		
		if (isNamedStatement()) {
			setResultCountBatch(getNamedStatement().executeBatch());
		}
		return super.executeBatch();
	}

	@Override
	public DBCONN closeQueryResources() {
		
		if (getActiveConnection() != null) {
			super.closeQueryResources();
			setNamed(false);
		}
		return me();
	}
	
	@Override
	public DBCONN closeStatement() {
		
		// Call super first to close the resulset.
		super.closeStatement();
		if (getNamedStatement() != null) {
			try {
				getNamedStatement().close();
			} catch (Exception e) {
				log.warn("Failed to close named statement.", e);
			}
			setNamedStatement(null);
		}
		return me();
	}

}
