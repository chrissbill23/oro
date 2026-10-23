# Quick start

You need Docker, and the repositories `1_ASATA_DATABASE` and `oro` side by side. Run the commands from the root of
`oro`.

1. **Start the shared database.** On a new database, the Oro tables and the development logins `oro_api` (the API) and
   `oro_worker` (the integrations and workers) are created on the first start.

   ```bash
   docker compose -f ../1_ASATA_DATABASE/docker-compose.yml up -d
   ```

   An existing database needs the two scripts run once: see [Database](../backend/README.md#database).

2. **Write the settings** in `backend/.env.local` (git ignores it; never commit it):

   ```bash
   # name:sha256 of each integration key, never the key: the key itself goes in umoja/backend/.env.local
   #   KEY=$(openssl rand -hex 32); printf %s "$KEY" | shasum -a 256
   UMOJA_INTEGRATION_KEYS=current:...
   ORO_INTEGRATION_KEYS=current:...
   ```

   All the settings are listed in [Backend with Docker](../backend/README.md#backend-with-docker).

3. **Start Oro.**

   ```bash
   docker compose up --build
   ```

   - API: http://localhost:8082/oro (`GET /oro/ping` answers when it is up)
   - Swagger UI: http://localhost:8082/oro/doc

To run it without Docker, or to run the tests (`./mvnw test` in `backend`, Docker running), see
[the backend README](../backend/README.md#run). To use it from Umoja, see [Connecting Umoja](connecting-umoja.md).
