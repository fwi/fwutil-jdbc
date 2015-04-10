package nl.fw.util.jdbc;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queries identified by a name, optionally loaded from file using {@link #loadQueries(String)}.
 * @author fred
 *
 */
public class NamedQuery implements INamedQuery {

	private static final Logger log = LoggerFactory.getLogger(NamedQuery.class);
	
	private Map<String, String> queries;
	
	/**
	 * Given query-map may not change while this instance is in use.
	 */
	public NamedQuery(Map<String, String> queries) {
		this.queries = queries;
	}

	@Override
	public String getQuery(String qname) {
		
		String sql = queries.get(qname);
		return (sql == null ? qname : sql);
	}
	
	/**
	 * Returns the amount of named queries.
	 */
	public int getSize() {
		return queries.size();
	}

	/** Beginning or end marker for a query in a query-file "{@code --[}". */
	public static final String QUERY_NAME_MARKER = "--[";

	/**
	 * Opens a resource text file in UTF-8 encoding and loads the queries.
	 * See {@link #loadQueries(Reader, Map)}.
	 */
	public static LinkedHashMap<String, String> loadQueries(String resourceName) throws IOException {
		return loadQueries(resourceName, StandardCharsets.UTF_8);
	}
	
	/**
	 * Opens a resource text file and loads the queries.
	 * See {@link #loadQueries(Reader, Map)}.
	 */
	public static LinkedHashMap<String, String> loadQueries(String resourceName, Charset charset) throws IOException {
		
		LinkedHashMap<String, String> qmap = null;
		try (Reader in = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName), charset)) {
			qmap = loadQueries(in);
		}
		return qmap;
	}

	/**
	 * Calls {@link #loadQueries(Reader, Map)} with an empty query map 
	 * and returns the query map after loading the queries.
	 */
	public static LinkedHashMap<String, String> loadQueries(Reader in) throws IOException {
		
		LinkedHashMap<String, String> qmap = new LinkedHashMap<String, String>();
		loadQueries(in, qmap);
		return qmap;
	}
	
	/**
	 * Loads queries form a sql-file/inputstream.
	 * Queries must be formatted using the {@link #QUERY_NAME_MARKER} in the following manner:
<pre>{@literal 
-- Just a comment
// Also a comment
-- Empty start tag, query will get query-count as ID (in this case "1").
--[]
select something from somehwere
-- End-tag for query (optional).
--[/]
-- Query with name INSERT_ITEM
--[INSERT_ITEM]
insert into items (item_key, item) values (@itemKey, @item)
-- If end-tag is used the name in end-tag must match start-tag, e.g. --[/INSERT_ITEM].
-- End-tag is optional, start-tag below marks end for INSERT_ITEM query
--[BIG_QUERY]
select a, b,c 
from x, y, z
where this=that and other=stuff
--[/BIG_QUERY]
-- Last end-tag is also optional (end of document marks end for BIG_QUERY)
}</pre>
	 * All text between begin and end tag is not formatted in any way and taken literally as the query,
	 * but a line is skipped if it is empty, starts with {@code --} (but not "{@code --[}" which is the tag-marker)
	 * or starts with {@code //}.
	 * @param in The characters to parse (stream is NOT closed by this method).
	 * @param qmap The map that will contain the query names/IDs and queries.
	 * @throws IOException if formatting is incorrect.
	 */
	public static void loadQueries(Reader in, Map<String, String> qmap) throws IOException {
		
		LineNumberReader reader = new LineNumberReader(in);
		String line = null;
		String q[] = null;
		int qCount = 0;
		while ((line = reader.readLine()) != null) {
			if (line.trim().isEmpty()) continue;
			if (line.startsWith(QUERY_NAME_MARKER)) {
				line = line.substring(QUERY_NAME_MARKER.length()-1);
			}
			if (line.startsWith("--") || line.startsWith("//")) {
				continue;
			}
			if (line.charAt(0) == '[' && line.charAt(line.length()-1) == ']') {
				if (line.charAt(1) == '/') { // query end tag
					if (q == null) {
						throw new IOException("Unexpected closing query-key at line " + reader.getLineNumber() + ": " + line);
					} else if (q[0] == null) {
						throw new IOException("Closing query-key found without opening query-key at line " + reader.getLineNumber() + ": " + line);
					} else if (q[1] == null) {
						throw new IOException("Closing query-key found without query at line " + reader.getLineNumber() + ": " + line);
					}
					if (q[1] == null) {
						log.warn("Ignoring empty query " + q[0] + " at line " + reader.getLineNumber());
					} else {
						String k = line.substring(2, line.length()-1);
						if (k.isEmpty()) {
							k = Integer.toString(qCount + 1);
						}
						if (!k.equals(q[0])) {
							throw new IOException("Closing query-key does not match opening query key [" + q[0] + "] at line " + reader.getLineNumber() + ": " + line);
						}
						addQuery(q, qmap);
						qCount++;
					}
					q = null;
				} else { // query start tag
					if (q != null) {
						if (q[1] == null) {
							log.warn("Ignoring empty query " + q[0] + " at line " + reader.getLineNumber());
						} else {
							addQuery(q, qmap);
							qCount++;
						}
					}
					String k = line.substring(1, line.length()-1);
					if (k.isEmpty()) {
						k = Integer.toString(qCount + 1);
					}
					q = new String[2];
					q[0] = k;
				}
			} else { // no query tag
				if (q == null) {
					throw new IOException("Missing opening query key at line " + reader.getLineNumber() + ": " + line);
				}
				if (q[1] == null) {
					q[1] = line;
				} else {
					q[1] = q[1] + "\n" + line;
				}
			}
		}
		if (q != null) {
			if (q[1] == null) {
				log.warn("Ignoring empty query " + q[0] + " at line " + reader.getLineNumber());
			} else {
				addQuery(q, qmap);
				qCount++;
			}
		}
		log.debug("Loaded " + qCount + " queries.");
	}
	
	private static void addQuery(String[] q, Map<String, String> qmap) {
		
		if (qmap.containsKey(q[0])) {
			if (log.isDebugEnabled()) {
				log.debug("Replacing old query for " + q[0]);
			}
		}
		qmap.put(q[0], q[1]);
		if (log.isTraceEnabled()) {
			log.trace("Added query " + q[0] + ":\n" + q[1]);
		}
	}

}
