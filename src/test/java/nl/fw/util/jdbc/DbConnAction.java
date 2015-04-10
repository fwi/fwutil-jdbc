package nl.fw.util.jdbc;

/**
 * Used in {@link NamedDao} as an example to reduce boilerplate-code.
 * But it does not help much since {@link DbConnHik} already removes most of the boilerplate-code.
 * @author fred
 *
 */
public interface DbConnAction {

	void toDb(DbConnNamedStatement<?> dbc) throws Exception;
}
