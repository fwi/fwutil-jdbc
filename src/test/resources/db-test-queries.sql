-- Test database queries
-- This file must be UTF-8 encoded.
-- Format is explained in nl.fw.util.jdbc.NamedQuery.loadQueries.

--[SELECT_CODE_VALUE]
select "value" from settings where code=@code

--[INSERT_CODE_VALUE]
-- not used, merge is used
insert into settings (code, "value") values (@code, @value)

--[UPDATE_CODE_VALUE]
-- not used, merge is used
update settings set "value" = @value where code = @code;

--[MERGE_CODE_VALUE]
-- Kind of hard to read but this is HSQLDB's "update or insert", see http://hsqldb.org/doc/2.0/guide/dataaccess-chapt.html#dac_merge_statement

merge into settings using (values(@code, @value))
as vals(c, v) on settings.code = vals.c
when matched then update set settings."value"=vals.v
when not matched then insert (code, "value") values (vals.c, vals.v)
