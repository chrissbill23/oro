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
other service talks on the Docker network `asataDB`. The settings and the secrets of the three come from Infisical,
never from a file of the server (no `.env`, no `secrets/`): every production compose command is
`deploy/compose.sh <compose arguments>`, never `docker compose` directly (the database reads the folder `/database`,
Oro `/database` and `/oro`, Umoja `/database` and `/umoja`). The secrets reach the services as Docker secrets (files in `/run/secrets`), never in an
image nor an environment.

## The server, once

- Docker with Compose 2.24 or later (`!reset`, `!override`), a DNS record of the domain to the server, ports 80 and 443
  open, nothing else.
- Two S3 buckets off the server (the backups of the database, of the files), and their keys.
- The Infisical project, its CLI and the server's machine identity (`/etc/asata/infisical.env`), as in
  [`INFISICAL.md`](../../1_ASATA_DATABASE/INFISICAL.md). The values below are its variables, the only copy: the server
  keeps none.

The values are written to Infisical as an operator of the project (`infisical login --interactive`, an account that can
write; never the machine identity), each one read from the standard input, never on a command line nor left on the
disk, without a trailing newline:

```bash
export INFISICAL_DOMAIN=https://eu.infisical.com   # the project's region
PROJECT=<the project's ID>
put() {   # put <folder> <name>: the standard input as the value
    f=$(mktemp); cat > "$f"
    infisical secrets set --silent --env=prod --projectId="$PROJECT" --path="$1" "$2=@$f" >/dev/null
    shred -u "$f"
}
mkpass() { openssl rand -base64 36 | tr -d '\n/+=' ; }
umask 077
```

## 1. The database

```bash
cd ~/asata/1_ASATA_DATABASE
# the superuser, Oro's three logins (Oro reads the same values: one copy), the passphrase of the backups
for name in postgres_password oro_api_db_password oro_worker_db_password oro_auth_db_password PGBACKREST_CIPHER_PASS; do
    mkpass | put /database "$name"
done
printf %s '...' | put /database PGBACKREST_REPO1_S3_KEY_SECRET
infisical secrets set --silent --env=prod --projectId="$PROJECT" --path=/database PGBACKREST_REPO1_S3_BUCKET=... \
  PGBACKREST_REPO1_S3_ENDPOINT=... PGBACKREST_REPO1_S3_REGION=... PGBACKREST_REPO1_S3_KEY=... >/dev/null
deploy/compose.sh config --quiet                 # every setting and secret present in Infisical (nothing printed)
deploy/compose.sh up -d --build
```

The first start creates the schemas and the reference data, never the data of development. Then the logins of Oro,
with the passwords of `/database` (files in `/run/secrets` of the container):

```bash
deploy/compose.sh exec postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d asata -f /oro-schema/oro/production-logins.sql'
deploy/compose.sh logs backup                    # "full backup done"
```

An hour after the first start, and every week, the service `restore-check` restores the latest backup and checks it
(`deploy/compose.sh logs restore-check`: "done"): keep as much free disk as the database for it.

## 2. Oro

```bash
cd ~/asata/oro
# Oro's three logins: already in /database (step 1), nothing to copy
# the key that signs the tokens of Oro's accounts (lost, everyone signs in again)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 \
  | tr -d '\n' | put /oro oro_accounts_signing_key
# the key of the notifications, Web Push (lost, every browser subscribes again)
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 \
  | tr -d '\n' | put /oro oro_push_vapid_private_key
UMOJA_KEY=$(openssl rand -hex 32)                 # Umoja's (/umoja); Oro keeps its hash only
printf %s "$UMOJA_KEY" | put /umoja umoja_integration_key
printf 'current:%s' "$(printf %s "$UMOJA_KEY" | shasum -a 256 | cut -d' ' -f1)" | put /oro umoja_integration_keys
unset UMOJA_KEY
mkpass | put /oro files_backup_password
read -rs SMTP_PASSWORD; printf %s "$SMTP_PASSWORD" | put /oro smtp_password; unset SMTP_PASSWORD
printf %s '...' | put /oro ORO_FILES_BACKUP_S3_SECRET
infisical secrets set --silent --env=prod --projectId="$PROJECT" --path=/oro \
  ORO_CORS_ALLOWED_ORIGINS=https://umoja.example.org ORO_SMTP_HOST=smtp.provider.example ORO_SMTP_USERNAME=... \
  ORO_ACCOUNTS_LINK_BASE=https://oro.example.org 'ORO_ACCOUNTS_MAIL_FROM=Oro <no-reply@oro.example.org>' \
  ORO_PUSH_SUBJECT=mailto:ops@oro.example.org 'ORO_FILES_BACKUP_REPOSITORY=s3:<endpoint>/<bucket>/oro-files' \
  ORO_FILES_BACKUP_S3_KEY=... >/dev/null
deploy/compose.sh config --quiet                  # every setting and secret present in Infisical (nothing printed)
deploy/compose.sh up -d --build
deploy/compose.sh logs oro | grep "production settings checked"
deploy/compose.sh ps oro                          # "healthy": ready, the database answering
```

Oro refuses to start in production on a setting of development (`ProductionChecks`): a placeholder or short password,
no ClamAV, the API description on, the rate limits off, an origin that is not HTTPS, the actuator on the API's port, the
Umoja integration without a key, no signing key for the accounts, a refresh cookie that is not Secure, emails to a
development mailbox, links that are not HTTPS. It lists every problem at once. The sender's domain needs SPF and DKIM
at the provider, or the emails land in spam.

Give Alertmanager a receiver that reaches someone (`monitoring/alertmanager.yml`) before going live.

## 3. Umoja and the edge

Umoja logs in to the database as its superuser (`postgres_password`, `/database`); its integration key is in
`/umoja` since step 2.

```bash
cd ~/asata/umoja
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 \
  | tr -d '\n' | put /umoja umoja_jwt_signing_key
# the API key of the application umoja in Sululu (sululu/docs/deployment.md), or empty: the notifications only logged
read -rs SULULU_KEY; printf %s "$SULULU_KEY" | put /umoja umoja_sululu_api_key; unset SULULU_KEY
# empty until needed: a rotation of the signing key, the CDN (then UMOJA_CDN_ENABLED=true and its settings too)
printf '' | put /umoja umoja_jwt_previous_keys
printf '' | put /umoja umoja_cdn_secret_key
infisical secrets set --silent --env=prod --projectId="$PROJECT" --path=/umoja \
  ASATA_DOMAIN=umoja.example.org ACME_EMAIL=ops@example.org >/dev/null
deploy/compose.sh config --quiet
deploy/compose.sh up -d --build
```

Caddy gets the certificate at its first start. Then check from outside: `https://umoja.example.org` answers,
`http://` redirects, `https://umoja.example.org/oro/internal/x` and `/oro/actuator/x` answer 404, nothing answers on
5433, 8081, 8082, 9082, 9090 or 9093, and an external TLS test (SSL Labs) grades A.

## Upgrades

1. CI green on the commit (`.github/workflows/backend.yml`).
2. The schema first, when it changed: `oro/migrate.sh` (its migrations are expand-only, `oro/migrations/README.md`):
   ```bash
   cd ~/asata/1_ASATA_DATABASE && git pull
   deploy/compose.sh exec postgres \
     sh -c 'PSQL_ARGS="-U $POSTGRES_USER -d asata" sh /oro-schema/oro/migrate.sh'
   ```
3. Then Oro: `git pull` and `deploy/compose.sh up -d --build oro`. The stop is graceful (the streams told to
   reconnect, the requests in flight finished, 45 s at most), and the streams reconnect by themselves. Wait for
   `healthy` (`deploy/compose.sh ps oro`) before calling it done.

## Rollbacks

The backend of before runs on the schema of after (expand, then contract), so a rollback is the previous commit of
Oro, built and started again; the schema stays. A migration that went wrong is undone by the next one, or, for lost
data, by a point-in-time recovery (`1_ASATA_DATABASE/README.md`, then the backend README, Recovering Oro).

## Rotations

The integration key: the backend README, Rotating an integration key. The signing key of Umoja (`/umoja`): the new one
in `umoja_jwt_signing_key`, the former in `umoja_jwt_previous_keys` for 15 minutes, then emptied, each time followed by
`deploy/compose.sh up -d --force-recreate umoja` in `~/asata/umoja`. The passwords of the
logins: a new value in Infisical (`/database`, one copy for both stacks), then the containers that read it created
again (a restart keeps the old value): the database first, `production-logins.sql` again, then Oro:

```bash
cd ~/asata/1_ASATA_DATABASE
deploy/compose.sh up -d --force-recreate postgres
deploy/compose.sh exec postgres \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d asata -f /oro-schema/oro/production-logins.sql'
cd ~/asata/oro && deploy/compose.sh up -d --force-recreate oro
```

Any other secret or setting of Oro changed in Infisical: `deploy/compose.sh up -d --force-recreate <the services that
read it>`.
