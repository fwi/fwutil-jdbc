-- Test database initial data.
-- This file must be UTF-8 encoded.
-- Format is explained in nl.fw.util.jdbc.NamedQuery.loadQueries.
-- Usually a database (driver) allows multiple insert statements to be bundled in one statement.
-- But unit test "TestNamed" uses addBatch(sql)/executeBatch which requires each statement to be separate.
-- []
insert into users (name) values ('Marvin the Martian');
-- []
insert into users (name) values ('Bugs Bunny');
-- []
insert into settings (code, "value") values ('nl.fw.util.jdbc.version', '1.0.0-SNAPSHOT');
-- []
insert into settings (code, "value") values ('nl.fw.sql.hsql.version', '2.3.2');
