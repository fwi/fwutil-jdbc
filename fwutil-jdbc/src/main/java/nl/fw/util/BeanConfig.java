package nl.fw.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods for configuring pools and (SQL) pool-factories using key-value maps or properties.
 * For example, consider the following properties:
 * <code>
 * <br> base.maxAcquireTimeMs = 10
 * <br> base.jdbcDriverClass = org.hsqldb.jdbc.JDBCDriver
 * <br> small.poolname = SmallPool
 * <br> small.maxsize = 3
 * <br> small.connection.user = SA
 * <br> small.connection.password = 
 * <br> large.poolname = LargePool
 * <br> large.maxsize = 10
 * <br> large.connection.user = MyUser
 * <br> large.connection.password = secret
 * </code>
 * <p>
 * Configuring pools could be done using: <pre>
 * base = BeanConfig.filterPrefix(p, "base.");
 * small = BeanClone.clone(base);
 * small.putAll(BeanConfig.filterPrefix(p, "small."));
 * large = BeanClone.clone(base);
 * large.putAll(BeanConfig.filterPrefix(p, "large."));
 * 
 * BeanConfig.configure(poolSmall, small);
 * BeanConfig.configure(sqlSmallFactory, small);
 * BeanConfig.configure(poolLarge, large);
 * BeanConfig.configure(sqlLargeFactory, large); </pre>
 * 
 * Further configuration per environment can be arranged
 * by using the method {@link #prioritizeSuffix(Map, String)}.
 * @author fwiers
 *
 */
// Copied from Yapool 0.9.3
public class BeanConfig {

	protected static Logger log = LoggerFactory.getLogger(BeanConfig.class);
	
	/** Properties with uppercase-names that contain this value (default "PASSWORD") are always shown with value {@link #PASSWORD_VALUE} (default "secret"). */ 
	public static String PASSWORD_KEY = "PASSWORD";
	/** The replacement value for {@link #PASSWORD_KEY} values, default "secret". */
	public static String PASSWORD_VALUE = "secret";

	// Utility class, do not instantiate
	private BeanConfig() {}
	
	/**
	 * Calls set-methods in the bean that have names that correspond to the property-keys.
	 * All names and keys are compared NOT case sensitive. 
	 * Two special cases are handled:
	 * <br> - if the property-key ends with an "S" (for seconds) and there is no such method,
	 * a method ending with "Ms" (for milliseconds) will be used if it exists. The property-value
	 * is multiplied by 1000 thousand. Only works for int- and long-values.
	 * <br> - if there is a set-method that takes a map or properties, a map is created using 
	 * the first Camel-case name of the method. E.g. a method <code>setConnectionProps(Properties p)</code>
	 * will receive a map with all values for properties that start with "connection."
	 * The keys in this map have "connection." removed. 
	 * <br>See also {@link #filterPrefix(Map, String)} to prepare the props.   
	 * @param bean The bean to apply the properties to.
	 * @param props The properties to apply to the bean.
	 * @return A description of methods for which values were set.
	 */
	@SuppressWarnings("unchecked")
	public static String configure(Object bean, Map<?,?> props) {

		if (bean == null) return "";

		Map<String, Method> setMethods = new HashMap<String, Method>();
		List<Method> mapMethods = new LinkedList<Method>();
		Method[] methoda = bean.getClass().getMethods();
		for(Method m : methoda) {
			if (m.getName().length() < 4) continue;
			if (!m.getName().startsWith("set")) continue;
			Class<?>[] par = m.getParameterTypes();
			if (par.length != 1) continue;
			if (Map.class.isAssignableFrom(par[0])) {
				mapMethods.add(m);
			} else {
				setMethods.put(m.getName().toUpperCase().substring(3), m);
			}
		}
		StringBuilder sb = new StringBuilder("Configuring properties for " + bean);
		Map<String, Object> filtered = filterPrefix(props, "");
		for(String key : filtered.keySet()) {
			String mkey = key.toUpperCase();
			Object value = filtered.get(key);
			if (setMethods.containsKey(mkey)) {
				setMethodValue(bean, setMethods.get(mkey), value, sb);
			}
			if (mkey.endsWith("S")) {
				String msKey = mkey.substring(0, key.length() -1) + "MS";
				if (setMethods.containsKey(msKey)) {
					setMethodValue(bean, setMethods.get(msKey), value, true, sb);
				}
			}
		}
		for (Method method : mapMethods) {
			String mapKey = method.getName().substring(3);
			int upperIndex = 1; // skip first character which is already uppercase
			boolean found = false;
			while (upperIndex < mapKey.length() && !found) {
				found = Character.isUpperCase(mapKey.charAt(upperIndex));
				if (!found) upperIndex++;
			}
			mapKey = mapKey.substring(0, upperIndex).toLowerCase();
			//log.debug("Found map-name " + mapKey);
			String mapPrefix = mapKey + ".";
			filtered = filterPrefix(props, mapPrefix);
			if (filtered.isEmpty()) continue;
			Map<String, Object> confProps;
			if (Properties.class.isAssignableFrom(method.getParameterTypes()[0])) {
				confProps = (Map<String, Object>)((Map<?,?>)new Properties());
			} else {
				confProps = new HashMap<String, Object>();
			}
			for(String key : filtered.keySet()) {
				Object value = filtered.get(key);
				confProps.put(key, value);
			}
			if (!confProps.isEmpty()) {
				setMethodValue(bean, method, confProps, sb);
			}
		}
		return sb.toString();
	}
	
	/**
	 * Applies prefix-filtered and prioritized properties to a bean.
	 * @param bean The bean to configure with the properties (set to null to configure no bean).
	 * @param props Original properties (remain unchanged).
	 * @param prefix The prefix to filter on (set to null for none).
	 * @param suffix The suffix to prioritize (set to null for none).
	 * @return The filtered and prioritized properties. 
	 */
	public static Properties configure(Object bean, Map<?, ?> props, String prefix, String suffix) {
		
		return configure(bean, props, prefix, suffix, (String[]) null);
	}

	/**
	 * Same as {@link #configure(Object, Map, String, String)} but also removes properties 
	 * that are listed in the filterSuffix.
	 */
	public static Properties configure(Object bean, Map<?, ?> props, String prefix, String suffix, String... filterSuffix) {

		Properties p = new Properties();
		p.putAll(prefix == null || prefix.isEmpty() ? props : filterPrefix(props, prefix));
		if (!(suffix == null || suffix.isEmpty())) {
			prioritizeSuffix(p, suffix, filterSuffix);
		}
		configure(bean, p);
		return p;
	}

	/**
	 * Opposite function of {@link #configure(Object, Map)}: extracts bean values and puts them in the properties map.
	 * @return a description of bean-methods and values
	 */
	public static String extract(Object bean, Map<Object, Object> props) {
	
		if (bean == null) return "";

		Map<String, Method> getMethods = new HashMap<String, Method>();
		List<Method> mapMethods = new LinkedList<Method>();
		Method[] methoda = bean.getClass().getMethods();
		for(Method mset : methoda) {
			if (mset.getName().length() < 4) continue;
			if (!mset.getName().startsWith("set")) continue;
			Class<?>[] par = mset.getParameterTypes();
			if (par.length != 1) continue;
			String propKey = mset.getName().substring(3);
			Method mget = null;
			if (Map.class.isAssignableFrom(par[0])) {
				try {
					mget = bean.getClass().getMethod("get" + propKey, (Class<?>[])null);
				} catch (NoSuchMethodException e) {
					continue;
				}
				if (!par[0].equals(mget.getReturnType())) {
					continue;
				}
				mapMethods.add(mget);
			} else {
				boolean boolGet = (par[0].equals(Boolean.class) || par[0].equals(boolean.class));
				String getMethodName = (boolGet ? "is" : "get") + propKey;
				try {
					mget = bean.getClass().getMethod(getMethodName, (Class<?>[])null);
				} catch (NoSuchMethodException e) {
					continue;
				}
				if (!par[0].equals(mget.getReturnType())) {
					continue;
				}
				getMethods.put(propKey, mget);
			}
		}
		StringBuilder sb = new StringBuilder("Reading configuration properties from " + bean);
		for (String key : getMethods.keySet()) {
			Object v = getMethodValue(bean, getMethods.get(key), sb);
			if (v != null) {
				boolean pwd = (key.toUpperCase().contains(PASSWORD_KEY));
				props.put(firstCharLowerCase(key), (pwd ? PASSWORD_VALUE : v.toString()));
			}
		}
		for (Method mapGet : mapMethods) {
			Object v = getMethodValue(bean, mapGet, sb);
			if (v == null) {
				continue;
			}
			Map<?,?> map = (Map<?,?>)v;
			String mapKey = mapGet.getName().substring(3);
			int upperIndex = 1; // skip first character which is already uppercase
			boolean found = false;
			while (upperIndex < mapKey.length() && !found) {
				found = Character.isUpperCase(mapKey.charAt(upperIndex));
				if (!found) upperIndex++;
			}
			mapKey = mapKey.substring(0, upperIndex).toLowerCase() + ".";
			for (Object mk : map.keySet()) {
				Object mv = map.get(mk);
				if (mv == null) {
					continue;
				}
				String mkey = mapKey + mk.toString();
				boolean pwd = (mkey.toUpperCase().contains(PASSWORD_KEY));
				props.put(mkey, (pwd ? PASSWORD_VALUE : mv.toString()));
			}
		}
		return sb.toString();
	}
	
	protected static String firstCharLowerCase(String v) {
		
		String s = v.substring(1);
		return v.substring(0, 1).toLowerCase() + s;
	}
	
	/**
	 * Returns a new map containing only the keys that have the prefix.
	 * The prefix is removed from the keys.
	 * Combine this method with {@link BeanClone#clone(Object)} to mix template-values with basic values.
	 * See also this class' {@link BeanConfig} description. 
	 */
	public static Map<String, Object> filterPrefix(Map<?, ?> props, String prefix) {
		
		Map<String, Object> filtered = new HashMap<String, Object>();
		for(Object okey : props.keySet()) {
			if (okey == null) continue;
			String key = okey.toString();
			if (key == null || key.isEmpty()) continue;
			if (!key.startsWith(prefix)) continue;
			key = key.substring(prefix.length());
			if (key.isEmpty()) continue;
			filtered.put(key, props.get(okey));
		}
		return filtered;
	}
	
	/**
	 * Sets the properties containing the suffix as default properties.
	 * E.g. given the following properties:
	 * <code>
	 * <br> maxsize = 10
	 * <br> maxsize.test = 4
	 * <br> maxsize.prod = 20
	 * </code>
	 * <br> Calling this method with suffix <code>.test</code> would set properties:
	 * <code>
	 * <br> maxsize = 4
	 * <br> maxsize.test = 4
	 * <br> maxsize.prod = 20
	 * </code>
	 * <br> (i.e. the <code>.test</code> properties take preference).
	 * 
	 */
	public static void prioritizeSuffix(Map<?, ?> props, String suffix) {
		
		prioritizeSuffix(props, suffix, (String[]) null);
	}
	
	/**
	 * Same as {@link #prioritizeSuffix(Map, String)} but also removes properties with a suffix  
	 * that is listed in filterSuffix.
	 */
	@SuppressWarnings("unchecked")
	public static void prioritizeSuffix(Map<?, ?> props, String suffix, String... filterSuffix) {
		
		if (suffix == null || suffix.isEmpty()) return;
		HashSet<String> filtered = new HashSet<String>();
		if (filterSuffix != null && filterSuffix.length > 0) {
			filtered.addAll(Arrays.asList(filterSuffix));
		}
		List<Object> pkeys = new ArrayList<Object>();
		pkeys.addAll(props.keySet());
		for(Object okey : pkeys) {
			if (okey == null) continue;
			String key = okey.toString();
			if (key == null || key.isEmpty()) continue;
			boolean filter = false;
			for (String s : filtered) {
				filter = key.endsWith(s);
				if (filter) {
					break;
				}
			}
			if (filter) {
				props.remove(key);
				continue;
			}
			if (!key.endsWith(suffix)) continue;
			key = key.substring(0, key.length() - suffix.length());
			if (key.isEmpty()) continue;
			((Map<String, Object>)props).put(key, props.get(okey));
		}
	}

	protected static void setMethodValue(Object bean, Method m, Object value, StringBuilder sb) {
		setMethodValue(bean, m, value, false, sb);
	}
	
	protected static void setMethodValue(Object bean, Method m, Object value, boolean times1000, StringBuilder sb) {

		Class<?> setType = m.getParameterTypes()[0];
		try {
			boolean unknown = false;
			if (value == null) {
				sb.append("\nCannot set a null value for ").append(m.getName());
				return;
			}
			String svalue = value.toString().trim();
			if (svalue.isEmpty()) {
				sb.append("\nCannot set an empty value for ").append(m.getName());
				return;
			}
			if (times1000) svalue += "000";
			if (setType == String.class) {
				m.invoke(bean, value.toString());
			} else if (setType == int.class || setType == Integer.class) {
				m.invoke(bean, Integer.valueOf(svalue));
			} else if (setType == boolean.class || setType == Boolean.class) {
				m.invoke(bean, Boolean.valueOf(svalue));
			} else if (setType == long.class || setType == Long.class) {
				m.invoke(bean, Long.valueOf(svalue));
			} else if (setType == byte.class || setType == Byte.class) {
				m.invoke(bean, Byte.valueOf(svalue));
			} else if (setType == short.class || setType == Short.class) {
				m.invoke(bean, Short.valueOf(svalue));
			} else if (setType == float.class || setType == Float.class) {
				m.invoke(bean, Float.valueOf(svalue));
			} else if (setType == double.class || setType == Double.class) {
				m.invoke(bean, Double.valueOf(svalue));
			} else if (Map.class.isAssignableFrom(setType)) {
				if (value instanceof Map) {
					m.invoke(bean, value);
				} else {
					sb.append("\nIncompatible value for map-type property ").append(m.getName()).append(": ").append(value.getClass().getName());
				}
			} else {
				unknown = true;
				sb.append("\nUnknown parameter type ").append(setType.getSimpleName()).append(" for method ").append(m.getName());
			}
			if (!unknown) {
				sb.append('\n').append(m.getName()).append(" to ");
				if (m.getName().toUpperCase().contains(PASSWORD_KEY)) {
					sb.append(PASSWORD_VALUE);
				} else if (Map.class.isAssignableFrom(setType)) {
					@SuppressWarnings("unchecked")
					Map<Object, Object> mvalue = (Map<Object, Object>) value;
					for (Object k : mvalue.keySet()) {
						if (k.toString().toUpperCase().contains(PASSWORD_KEY)) {
							svalue = svalue.replace(k.toString() + "=" + mvalue.get(k), k.toString() + "=" + PASSWORD_VALUE);
						}
					}
					sb.append(svalue);
				} else {
					sb.append(svalue);
				}

			}
		} catch (Exception e) {
			sb.append("\nFailed to set value for ").append(m.getName()).append(": ").append(e.toString());
		}
	}
	
	protected static Object getMethodValue(Object bean, Method m, StringBuilder sb) {
		
		Object value = null;
		try {
			value = m.invoke(bean, (Object[])null);
			if (value == null) {
				return null;
			}
			Class<?> getType = value.getClass();
			boolean known = false;
			if (getType == String.class) {
				known = true;
			} else if (getType == int.class || getType == Integer.class) {
				known = true;
			} else if (getType == boolean.class || getType == Boolean.class) {
				known = true;
			} else if (getType == long.class || getType == Long.class) {
				known = true;
			} else if (getType == byte.class || getType == Byte.class) {
				known = true;
			} else if (getType == short.class || getType == Short.class) {
				known = true;
			} else if (getType == float.class || getType == Float.class) {
				known = true;
			} else if (getType == double.class || getType == Double.class) {
				known = true;
			} else if (Map.class.isAssignableFrom(getType)) {
				known = true;
			} else {
				sb.append("\nUnknown value type ").append(getType.getSimpleName()).append(" for method ").append(m.getName());
			}
			if (known) {
				sb.append('\n').append(m.getName()).append(" = ");
				if (m.getName().toUpperCase().contains(PASSWORD_KEY)) {
					sb.append(PASSWORD_VALUE);
				} else if (Map.class.isAssignableFrom(getType)) {
					String s = value.toString();
					@SuppressWarnings("unchecked")
					Map<Object, Object> mvalue = (Map<Object, Object>) value;
					for (Object k : mvalue.keySet()) {
						if (k.toString().toUpperCase().contains(PASSWORD_KEY)) {
							s = s.replace(k.toString() + "=" + mvalue.get(k), k.toString() + "=" + PASSWORD_VALUE);
						}
					}
					sb.append(s);
				} else {
					sb.append(value);
				}
			} else {
				value = null;
			}
		} catch (Exception e) {
			sb.append("\nFailed to get value from ").append(m.getName()).append(": ").append(e.toString());
		}
		return value;
	}
	
	/**
	 * Utility method, only sets the value in the map if the key is not already in the map.
	 */
	@SuppressWarnings("unchecked")
	public static void putIfNotExists(Map<?,?> props, Object key, Object value) {
		
		if (!props.containsKey(key)) {
			((Map<Object, Object>)props).put(key, value);
		}
	}

}
