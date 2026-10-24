# Deployment

Oro, Umoja and their database on a server with Docker Compose. The three repositories side by side, as on a developer's
machine:

```text
~/asata/1_ASATA_DATABASE   the database (PostgreSQL 17, its backups)
~/asata/oro                Oro (its backend, ClamAV, the backups of its files, Prometheus, Alertmanager)
~/asata/umoja              Umoja (its backend, its frontend, Caddy in front)
```

Each has a `docker-compose.production.yml` over its `docker-compose.yml`. When something goes wrong: the
[runbooks](runbooks.md), one per alert. Only Caddy opens ports (80 and 443); every
other service talks on the Docker network `asataDB`. The secrets are files in `secrets/` (git ignored, mode 600), read
by the services as Docker secrets, never in an image nor an environment.

## The server, once

- Docker with Compose 2.24 or later (`!reset`, `!override`), a DNS record of the domain to the server, ports 80 and 443
  open, nothing else.
- Two S3 buckets off the server (the backups of the database, of the files), and their keys.
- A vault for the secrets below: they are the only copy that matters, the files on the server are copies of them.

```bash
mkpass() { openssl rand -base64 36 | tr -d '\n/+=' ; }
umask 077
```

## 1. The database

```bash
cd ~/asata/1_ASATA_DATABASE
mkdir -p secrets
mkpass > secrets/postgres_password
mkpass > secrets/oro_api_db_password
mkpass > secrets/oro_worker_db_password
mkpass > secrets/oro_auth_db_password
cat > .env <<EOF
POSTGRES_USER=postgres
PGBACKREST_CIPHER_PASS=$(mkpass)
PGBACKREST_REPO1_S3_BUCKET=...
PGBACKREST_REPO1_S3_ENDPOINT=...
PGBACKREST_REPO1_S3_REGION=...
PGBACKREST_REPO1_S3_KEY=...
PGBACKREST_REPO1_S3_KEY_SECRET=...
EOF
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
```

The first start creates the schemas and the reference data, never the data of development. Then the logins of Oro,
with the passwords of `secrets/`:

```bash
docker compose -f docker-compose.yml -f docker-compose.production.yml exec postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d asata -f /oro-schema/oro/production-logins.sql'
docker compose logs backup                       # "full backup done"
```

## 2. Oro

```bash
cd ~/asata/oro
mkdir -p secrets
cp ../1_ASATA_DATABASE/secrets/oro_api_db_password ../1_ASATA_DATABASE/secrets/oro_worker_db_password \
   ../1_ASATA_DATABASE/secrets/oro_auth_db_password secrets/
# the key that signs the tokens of Oro's accounts: in the vault too (lost, everyone signs in again)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 \
  | tr -d '\n' > secrets/oro_accounts_signing_key
# the key of the notifications (Web Push): in the vault too (lost, every browser subscribes again)
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 \
  | tr -d '\n' > secrets/oro_push_vapid_private_key
UMOJA_KEY=$(openssl rand -hex 32)                 # Umoja's, in step 3; Oro keeps its hash only
printf 'current:%s' "$(printf %s "$UMOJA_KEY" | shasum -a 256 | cut -d' ' -f1)" > secrets/umoja_integration_keys
mkpass > secrets/files_backup_password
printf %s '<the SMTP password of the provider>' > secrets/smtp_password
cat > backend/.env.local <<EOF
ORO_CORS_ALLOWED_ORIGINS=https://umoja.example.org
ORO_SMTP_HOST=smtp.provider.example
ORO_SMTP_USERNAME=...
ORO_ACCOUNTS_LINK_BASE=https://oro.example.org
ORO_ACCOUNTS_MAIL_FROM=Oro <no-reply@oro.example.org>
ORO_PUSH_SUBJECT=mailto:ops@oro.example.org
ORO_FILES_BACKUP_REPOSITORY=s3:<endpoint>/<bucket>/oro-files
ORO_FILES_BACKUP_S3_KEY=...
ORO_FILES_BACKUP_S3_SECRET=...
EOF
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
docker compose logs oro | grep "production settings checked"
docker compose ps oro                             # "healthy": ready, the database answering
```

Oro refuses to start in production on a setting of development (`ProductionChecks`): a placeholder or short password,
no ClamAV, the API description on, the rate limits off, an origin that is not HTTPS, the actuator on the API's port, the
Umoja integration without a key, no signing key for the accounts, a refresh cookie that is not Secure, emails to a
development mailbox, links that are not HTTPS. It lists every problem at once. The sender's domain needs SPF and DKIM
at the provider, or the emails land in spam.

Give Alertmanager a receiver that reaches someone (`monitoring/alertmanager.yml`) before going live.

## 3. Umoja and the edge

```bash
cd ~/asata/umoja
cat >> backend/.env.local <<EOF
UMOJA_JWT_SIGNING_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')
UMOJA_INTEGRATION_KEY=$UMOJA_KEY
EOF
ASATA_DOMAIN=umoja.example.org ACME_EMAIL=ops@example.org \
  docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
```

Caddy gets the certificate at its first start. Then check from outside: `https://umoja.example.org` answers,
`http://` redirects, `https://umoja.example.org/oro/internal/x` and `/oro/actuator/x` answer 404, nothing answers on
5433, 8081, 8082, 9082, 9090 or 9093, and an external TLS test (SSL Labs) grades A.

## Upgrades

1. CI green on the commit (`.github/workflows/backend.yml`).
2. The schema first, when it changed: `oro/migrate.sh` (its migrations are expand-only, `oro/migrations/README.md`):
   ```bash
   cd ~/asata/1_ASATA_DATABASE && git pull
   docker compose -f docker-compose.yml -f docker-compose.production.yml exec postgres \
     sh -c 'PSQL_ARGS="-U $POSTGRES_USER -d asata" sh /oro-schema/oro/migrate.sh'
   ```
3. Then Oro: `git pull` and `docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build oro`.
   The stop is graceful (the streams told to reconnect, the requests in flight finished, 45 s at most), and the
   streams reconnect by themselves. Wait for `healthy` (`docker compose ps oro`) before calling it done.

## Rollbacks

The backend of before runs on the schema of after (expand, then contract), so a rollback is the previous commit of
Oro, built and started again; the schema stays. A migration that went wrong is undone by the next one, or, for lost
data, by a point-in-time recovery (`1_ASATA_DATABASE/README.md`, then the backend README, Recovering Oro).

## Rotations

The integration key: the backend README, Rotating an integration key. The signing key of Umoja: the new one in
`UMOJA_JWT_SIGNING_KEY`, the former in `UMOJA_JWT_PREVIOUS_KEYS` for 15 minutes, then removed. The passwords of the
logins: a new file in `secrets/` of both repositories, `production-logins.sql` again, Oro restarted.
