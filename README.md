Jdbc Util
--------

A small package to aid in working with plain JDBC. 
It is similar to [commons-dbutils](http://commons.apache.org/proper/commons-dbutils/index.html)
but taking a different approach using an extendable fluent API to query the database.
Mapping result-sets to objects is not included, have a look at [sql2o](http://www.sql2o.org/) or the previously mentioned commons-dbutils if you want this. For a full Object-relational mapping Hibernate can be used, see [HibHik](https://github.com/intercommit/hibhik) to get started. 

Below an example of an usage pattern with a datasource backed by a database connection pool (from [DbConn](./src/main/java/nl/fw/util/jdbc/DbConn.java)):
```java
try (DbConn dbc = new DbConn(myDataSource)) {
	// DbConn will fetch a connection from the datasource.
	ResultSet rs = dbc.createStatement().executeQuery("select * from myTable").getResulSet();
	// do work 
	rs = dbc.createStatement().executeQuery("select * from anotherTable").getResulSet();
	// DbConn will have closed all previously used resources (resultset and statement)
	// do some more work
	dbc.commitAndClose();
}
// DbConn closes any open resources and active connection, rolls back before closing if needed.
```
Other JDBC features:
 * An implementation of a named parameter statement: instead of using `?` for parameter placeholders, `@name`-style parameter placeholders can be used.
 * Loading (anonymous) queries from (SQL) file: clearly separate your code from used SQL statements and easily setup and populate test-databases.   
 * A separate package using [HikariCP](https://github.com/brettwooldridge/HikariCP) as backend database connection pool (HikariCP is an optional dependency in this Maven project).

The unit test class [TestNamed](./src/test/java/nl/fw/util/jdbc/TestNamed.java) shows the usage of all these features, 
the more bare-metal unit test class [TestDbCrud](./src/test/java/nl/fw/util/jdbc/TestDbCrud.java) shows the usage of Create, Read, Update and Delete queries.

Other "historical" features:
 * A socket server called [SocketAcceptor](./src/main/java/nl/fw/util/socket/SocketAcceptor.java). Includes production features like "too busy" support and "wait for sockets tasks to complete". 
 * Object cloning and gzipping in [BeanClone](./src/main/java/nl/fw/util/BeanClone.java).
 * Properties manipulation and mapping to (and extracting from) beans in [BeanConfig](./src/main/java/nl/fw/util/BeanConfig.java). 
