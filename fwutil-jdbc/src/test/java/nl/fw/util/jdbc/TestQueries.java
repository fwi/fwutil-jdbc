package nl.fw.util.jdbc;

/**
 * Names for queries specified in the resource file "db-test-queries.sql".
 * It is good practice to make these names constants so that a name-change
 * can be done centrally (in this interface) without affecting other code.
 * A name-change does require a recompile as Java stores constant-values in classes that use them.
 * @author fred
 *
 */
public interface TestQueries {

	String SELECT_CODE_VALUE = "SELECT_CODE_VALUE";
	String INSERT_CODE_VALUE = "INSERT_CODE_VALUE";
	String UPDATE_CODE_VALUE = "UPDATE_CODE_VALUE";
	String MERGE_CODE_VALUE = "MERGE_CODE_VALUE";
}
