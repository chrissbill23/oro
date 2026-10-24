-- the logins of the tests, like the ones of oro/INSERT.sql: NOINHERIT, no right without SET LOCAL ROLE; the API's takes
-- oro_app only, the worker's oro_system only
CREATE ROLE oro_api LOGIN NOINHERIT PASSWORD 'oro_api_test';
GRANT oro_app TO oro_api WITH INHERIT FALSE, SET TRUE;
CREATE ROLE oro_worker LOGIN NOINHERIT PASSWORD 'oro_worker_test';
GRANT oro_system TO oro_worker WITH INHERIT FALSE, SET TRUE;
CREATE ROLE oro_auth LOGIN NOINHERIT PASSWORD 'oro_auth_test';
GRANT oro_account TO oro_auth WITH INHERIT FALSE, SET TRUE;
