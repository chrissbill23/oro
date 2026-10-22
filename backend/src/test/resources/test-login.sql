-- the login of the tests, like the one of oro/INSERT.sql: NOINHERIT, no right without SET LOCAL ROLE
CREATE ROLE oro_service LOGIN NOINHERIT PASSWORD 'oro_service_test';
GRANT oro_app TO oro_service WITH INHERIT FALSE, SET TRUE;
GRANT oro_system TO oro_service WITH INHERIT FALSE, SET TRUE;
