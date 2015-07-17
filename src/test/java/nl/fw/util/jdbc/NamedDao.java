package nl.fw.util.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example of a Data Access Object using a base-class of the DbConn API (instead of an end-point like {@link DbConn}).
 * This DAO relies on named queries that must be set in the DbConn class.
 * @author fred
 */
public class NamedDao {

	private static final Logger log = LoggerFactory.getLogger(TestNamed.class);

	private final DbConnNamedStatement<?> dbc;
	
	public NamedDao(DbConnNamedStatement<?> dbc) {
		this.dbc = dbc;
	}

	public String getCodeValue(String code) {
		
		log.debug("Retrieving value for code " + code);
		String value = null;
		try {
			dbc.nameStatement(TestQueries.SELECT_CODE_VALUE).getNamedStatement().setString("code", code);
			if (dbc.executeQuery().getResultSet().next()) {
				value = dbc.getResultSet().getString("value");
			}
			dbc.commitAndClose();
			log.debug("Retrieved " + code + "=" + (value == null ? "<null>" : value));
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
		return value;
	}
	
	public void setCodeValue(String code, String value) {
		
		log.debug("Merging code " + code + "=" + value);
		try {
			dbc.nameStatement(TestQueries.MERGE_CODE_VALUE);
			dbc.getNamedStatement().setString("code", code);
			dbc.getNamedStatement().setString("value", value);
			if (dbc.executeUpdate().getResultCount() != 1) {
				throw new RuntimeException("Merging code/value did not result in database update. Result count: " + dbc.getResultCount());
			}
			dbc.commitAndClose();
			log.debug("Merged code " + code + "=" + value);
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
	}
	
	public List<String> loadUserNames() {
		
		log.debug("Loading all user names containing a 'n' with a custom, non pre-defined query.");
		final List<String> users = new ArrayList<String>();
		
		// This is an example on re-using a function to reduce boilerplate-code.
		// But is adds just as much boilerplate as it removes.
		// This is because HikDbConn already removes a lot of boilerplate-code.
		
		dbAction(new DbConnAction() {
			@Override
			public void toDb(DbConnNamedStatement<?> dbc) throws Exception {
				
				// The sql-string is not listed in the named quries loaded from "db-test-queries.sql".
				// Therefor, the sql-string is used "as is" to do a query.
				
				dbc.nameStatement("select name from users where name like @name");
				dbc.getNamedStatement().setString("name", "%n%");
				dbc.executeQuery();
				while (dbc.getResultSet().next()) {
					users.add(dbc.getResultSet().getString(1));
				}
			}
		});
		log.debug("Found " + users.size() + " user names: " + users);
		return users;
	}
	
	public long storeUser(final String name) {
		
		log.debug("Store new user, return auto-generated ID.");
		final long[] userId = new long[1];
		dbAction(new DbConnAction() {
			@Override
			public void toDb(DbConnNamedStatement<?> dbc) throws Exception {
				
				dbc.nameStatement("insert into users (name) values (@name)", true);
				dbc.getNamedStatement().setString("name", name);
				if (dbc.executeUpdate().getResultCount() != 1 || !dbc.getResultSet().next()) {
					throw new SQLException("Creating user did not result in an update.");
				}
				userId[0] = dbc.getResultSet().getLong(1);
			}
		});
		log.debug("New user got auto-generated ID {}", userId[0]);
		return userId[0];
	}

	public void useNonNamedQuery(String qname) {
		
		log.debug("Example using a query-name that does not exist.");
		try {
			dbc.nameStatement(qname);
			dbc.executeQuery();
			log.debug("Not expecting to get this far.");
			dbc.commitAndClose();
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
	}
	
	private void dbAction(DbConnAction dbAction) {
		
		try {
			dbAction.toDb(dbc);
			dbc.commitAndClose();
		} catch (Exception e) {
			dbc.rollbackAndClose(e);
		}
	}

}
