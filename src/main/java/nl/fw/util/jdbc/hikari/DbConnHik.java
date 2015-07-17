package nl.fw.util.jdbc.hikari;

import nl.fw.util.jdbc.DbConnNamedStatement;
import nl.fw.util.jdbc.INamedQuery;

/**
 * Database connection wrapper using a {@link HikPool}
 * <br>To add more utility methods, see the comments in {@link nl.fw.util.jdbc.DbConn}.
 * @author fred
 *
 */
public class DbConnHik extends DbConnNamedStatement<DbConnHik> {

	private HikPool dbPool;
	
	public DbConnHik(HikPool dbPool) {
		this(dbPool, null);
	}

	public DbConnHik(HikPool dbPool, INamedQuery namedQueries) {
		this.dbPool = dbPool;
		setDataSource(dbPool.getDataSource());
		setNamedQueries(namedQueries);
	}
	
	public HikPool getHikPool() {
		return dbPool;
	}

}
