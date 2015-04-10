package nl.fw.util.jdbc;

import java.sql.Connection;

import javax.sql.DataSource;

/**
 * Keeps track of resources and closes them as needed.
 * Example usage with "try with resources":<pre><code>
 * try (DbConn dbc = new DbConn(myDataSource)) {
 * 	// DbConn will fetch a connection from the datasource.
 *	ResultSet rs = dbc.createStatement().executeQuery("select * from myTable).getResulSet();
 *	// do work 
 *	rs = dbc.createStatement().executeQuery("select * from anotherTable).getResulSet();
 *	// DbConn will have closed all previously used resources (resultset and statement)
 *	// do some more work
 *	dbc.commitAndClose();
 * }
 * // If "commitAndClose" was not reached, DbConn will close all resources and rollback.
 * // The connection from the datasource is always closed. 
 * </code></pre>
 * Example usage when re-using a DbConn instance:<pre><code>
 * try {
 * 	ResultSet rs = dbc.createStatement().executeQuery("select * from myTable).getResulSet();
 * 	dbc.commitAndClose();
 * } catch (Exception e) {
 * 	dbc.rollbackAndClose(e);
 * }
 * </code></pre>
 * <p>
 * This class is an end-point for the fluent class {@link DbConnNamedStatement}.
 * Try not to extend this class with new JDBC utility methods 
 * (that will break the fluent API hierarchy), 
 * instead extend {@link DbConnStatement} or {@link DbConnNamedStatement}
 * in the same manner that {@link DbConnNamedStatement} extends {@link DbConnStatement}.
 * <br>Then create a 'concrete' class similar to this class.
 * <br>This class is <b>not thread-safe</b>, see also comments in {@link DbConnBase}.
 * @author fred
 *
 */
public class DbConn extends DbConnNamedStatement<DbConn> {
	
	public DbConn() {}
	
	public DbConn(DataSource ds) {
		setDataSource(ds);
	}
	
	public DbConn(Connection conn) {
		setConnection(conn);
	}

}
