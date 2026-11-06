#!/bin/sh
# docker compose for production, its settings and secrets read from Infisical (../1_ASATA_DATABASE/INFISICAL.md):
#   deploy/compose.sh up -d --build        deploy/compose.sh logs --tail=200 oro
#   deploy/compose.sh config --quiet       (every setting and secret present in Infisical, nothing printed)
#   deploy/compose.sh -- <command>         (another command with the same values in its environment)
# Logs in with the server's machine identity (/etc/asata/infisical.env), then runs docker compose with the folders
# below of the environment prod in its environment: the settings for the ${...} of the compose files, the secrets for
# its secrets (environment:, files in /run/secrets inside the containers, never in their environment). Nothing is
# written to disk. Infisical unreachable: no compose command runs; the containers go on, and docker exec and docker
# restart on a container still work.
set -eu

# the folders of Infisical this stack reads (a name in two folders: the last one wins)
stack_paths="/database /oro"

here=$(cd "$(dirname "$0")/.." && pwd)
config=${ASATA_INFISICAL_CONFIG:-/etc/asata/infisical.env}

die() {
    echo "deploy/compose.sh: $*" >&2
    exit 1
}

command -v infisical >/dev/null 2>&1 || die "the Infisical CLI is not installed (1_ASATA_DATABASE/INFISICAL.md)"
[ -r "$config" ] || die "$config is missing or unreadable (1_ASATA_DATABASE/INFISICAL.md)"
case "$(ls -ln "$config")" in
    -rw-------*|-r--------*) ;;
    *) die "$config must be mode 600: chmod 600 $config" ;;
esac
[ -n "$(find "$config" -prune -user "$(id -u)")" ] || die "$config must belong to $(id -un)"

# the env_file of the included development compose file (docker-compose.yml): compose needs it to exist, and in
# production it stays empty (a value there would fill what Infisical does not set)
env_files=$(awk '$1 == "env_file:" { print $2 }' "$here/docker-compose.yml")
for env_file in $env_files; do
    if [ ! -e "$here/$env_file" ]; then
        : > "$here/$env_file"
    elif [ -s "$here/$env_file" ]; then
        die "$env_file holds settings: in production they are in Infisical (1_ASATA_DATABASE/INFISICAL.md); empty it"
    fi
done

set -a
# shellcheck disable=SC1090
. "$config"
set +a
for name in INFISICAL_DOMAIN INFISICAL_PROJECT_ID INFISICAL_UNIVERSAL_AUTH_CLIENT_ID \
    INFISICAL_UNIVERSAL_AUTH_CLIENT_SECRET; do
    eval "value=\${$name:-}"
    [ -n "$value" ] || die "$name is not set in $config"
done
environment=${ASATA_INFISICAL_ENV:-prod}
export INFISICAL_DISABLE_UPDATE_CHECK=true

INFISICAL_TOKEN=$(infisical login --method=universal-auth --silent --plain --telemetry=false) \
    || die "the login to Infisical ($INFISICAL_DOMAIN) failed"
[ -n "$INFISICAL_TOKEN" ] || die "the login to Infisical ($INFISICAL_DOMAIN) gave no token"
export INFISICAL_TOKEN
unset INFISICAL_UNIVERSAL_AUTH_CLIENT_ID INFISICAL_UNIVERSAL_AUTH_CLIENT_SECRET

paths=""
for path in $stack_paths; do
    paths="$paths --path=$path"
done

# the variables the secrets of the compose file are made of (its top-level secrets:, each "environment: <name>"):
# docker compose config does not check them, so each one is checked here before any command (unset: refused; set,
# even empty, is fine)
ASATA_SECRET_NAMES=$(awk '/^secrets:/ { inside = 1; next } /^[^ #]/ { inside = 0 }
    inside && $1 == "environment:" { print $2 }' "$here/docker-compose.production.yml" | tr '\n' ' ')
[ -n "$ASATA_SECRET_NAMES" ] || die "no secret found in docker-compose.production.yml"
export ASATA_SECRET_NAMES ASATA_STACK_WHERE="environment $environment, folders $stack_paths"

# docker compose, or (deploy/compose.sh -- <command>) the command given
if [ "${1:-}" = "--" ]; then
    shift
    [ $# -gt 0 ] || die "no command after --"
else
    set -- docker compose --project-directory "$here" \
        -f "$here/docker-compose.yml" -f "$here/docker-compose.production.yml" "$@"
fi

# --expand=false: each value as stored (a $ in a password is not a variable)
# shellcheck disable=SC2086,SC2016
exec infisical run --silent --telemetry=false --expand=false --env="$environment" \
    --projectId="$INFISICAL_PROJECT_ID" $paths -- \
    sh -c 'missing=""
        for name in $ASATA_SECRET_NAMES; do
            eval "[ -n \"\${$name+set}\" ]" || missing="$missing $name"
        done
        if [ -n "$missing" ]; then
            echo "deploy/compose.sh: not in Infisical ($ASATA_STACK_WHERE):$missing" >&2
            exit 1
        fi
        unset ASATA_SECRET_NAMES ASATA_STACK_WHERE INFISICAL_TOKEN
        exec "$@"' deploy/compose.sh "$@"
