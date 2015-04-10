package nl.fw.util.jdbc;

/**
 * Interface for looking up a query given a query name.
 * <br>As long as the underlying map does not change, the method {@link #getQuery(String)} should be thread-safe.
 * @author fred
 *
 */
public interface INamedQuery {

	/**
	 * Returns the given query or the query mapped to the query name.
	 */
	String getQuery(String qname);
}
