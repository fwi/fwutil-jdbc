package nl.fw.util.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General static JDBC utility methods.
 * @author fred
 *
 */
public class DbConnUtil {

	/** Utility class, should not be instantiated. */
	private DbConnUtil() {}
	
	/** Logger used to log a warning when a close method encounters an error. */ 
	public static Logger log = LoggerFactory.getLogger(DbConnUtil.class);

	/**
	 * Throws a runtime-exception if given exception is not null.
	 * @param t If an instance of an {@link Error}, throws the Error.
	 * If an instance of a {@link RuntimeException}, throws the RuntimeException.
	 * Else throws a RuntimeException with the description, cause and stack-trace copied from the checked exception.
	 */
	public static void rethrowRuntime(Throwable t) {
		
		if (t == null) {
			return;
		}
		if (t instanceof Error) {
			throw ((Error)t);
		}
		if (t instanceof RuntimeException) {
			throw ((RuntimeException)t);
		}
		RuntimeException re = new RuntimeException(t.toString(), t.getCause());
		re.setStackTrace(t.getStackTrace());
		throw re;
	}

	/** Closes a resultset (checks for null-value), logs any error as warning. */
	public static void closeSilent(ResultSet rs) {
		
		try {
			if (rs != null) rs.close();
		} catch (Exception se) {
			log.warn("Failed to close result set " + rs, se);
		}
	}

	/** Closes a statement (checks for null-value), logs any error as warning. */
	public static void closeSilent(Statement s) {
		
		try {
			if (s != null) s.close();
		} catch (Exception se) {
			log.warn("Failed to close statement " + s, se);
		}
	}

	/** Closes a statement (checks for null-value), logs any error as warning. */
	public static void closeSilent(NamedParameterStatement s) {
		
		try {
			if (s != null) s.close();
		} catch (Exception se) {
			log.warn("Failed to close named statement " + s, se);
		}
	}
	
	/** Closes a database Connection (checks for null-value), logs any error as warning. */
	public static void closeSilent(Connection c) {
		
		try {
			if (c != null) c.close();
		} catch (Exception se) {
			log.warn("Failed to close database connection " + c, se);
		}
	}

	/**
	 * Utility method for constructing a prepared statement using the 'in' keyword.
	 * <br>Usage:<pre>
	 * String SQL_FIND = "SELECT id, name, value FROM data WHERE id IN (%s)" 
	 * String sql = String.format(SQL_FIND, preparePlaceHolders(ids.size()));
	 * statement = connection.prepareStatement(sql);
	 * setValues(statement, ids.toArray());
	 * resultSet = statement.executeQuery();
	 * </pre>
	 * See also {@link #setValues(PreparedStatement, Object...)}.
	 * <br>Copied from http://stackoverflow.com/questions/178479/preparedstatement-in-clause-alternatives.
	 */
	public static String preparePlaceHolders(int length) {
	    
		StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < length;) {
	        sb.append("?");
	        if (++i < length) {
	            sb.append(",");
	        }
	    }
	    return sb.toString();
	}

	/** 
	 * See comments on {@link #preparePlaceHolders(int)}
	 */
	public static void setValues(PreparedStatement preparedStatement, Object... values) throws SQLException {
	    
		for (int i = 0; i < values.length; i++) {
	        preparedStatement.setObject(i + 1, values[i]);
	    }
	}
	
	/** 
	 * See comments on {@link #preparePlaceHolders(int)}
	 */
	public static void setValues(PreparedStatement preparedStatement, Collection<?> values) throws SQLException {
	    
		int i = 0;
		for (Object o : values) {
	        preparedStatement.setObject(++i, o);
		}
	}

}
