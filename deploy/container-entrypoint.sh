#!/bin/sh
set -eu
fail() { printf '%s\n' "$1" >&2; exit 64; }
[ "${WORKHUB_ENABLE_DEMO:-}" = "true" ] || fail 'Explicit WORKHUB_ENABLE_DEMO=true is required.'
[ "${WORKHUB_HTTPS_PROXY:-}" = "true" ] || fail 'A trusted HTTPS proxy is required; set WORKHUB_HTTPS_PROXY=true only after configuring it.'
WORKHUB_PUBLIC_HOST=${WORKHUB_PUBLIC_HOST:-${RENDER_EXTERNAL_HOSTNAME:-}}
[ -n "${WORKHUB_PUBLIC_HOST:-}" ] || fail 'WORKHUB_PUBLIC_HOST is required.'
case "$WORKHUB_PUBLIC_HOST" in *[!A-Za-z0-9.-]*|.*|-*|*..*|*.) fail 'Invalid WORKHUB_PUBLIC_HOST.';; esac
[ "${#WORKHUB_PUBLIC_HOST}" -le 253 ] || fail 'Invalid WORKHUB_PUBLIC_HOST length.'
PORT=${PORT:-10000}
case "$PORT" in ''|*[!0-9]*) fail 'PORT must be numeric.';; esac
[ "$PORT" -ge 1024 ] && [ "$PORT" -le 65535 ] || fail 'PORT must be 1024-65535.'
[ -n "${WORKHUB_DEMO_PASSWORD:-}" ] || fail 'Configure WORKHUB_DEMO_PASSWORD through the hosting secret manager.'
case "$WORKHUB_DEMO_PASSWORD" in *[!A-Za-z0-9@%+=:,._-]*) fail 'Demo secret contains unsupported characters.';; esac
[ "${#WORKHUB_DEMO_PASSWORD}" -ge 24 ] && [ "${#WORKHUB_DEMO_PASSWORD}" -le 72 ] || fail 'Demo secret must be 24-72 characters.'
WORKHUB_DATA_DIR=${WORKHUB_DATA_DIR:-/var/lib/workhub}
case "$WORKHUB_DATA_DIR" in /*) ;; *) fail 'WORKHUB_DATA_DIR must be absolute.';; esac
case "$WORKHUB_DATA_DIR" in *[!A-Za-z0-9_./-]*|*..*) fail 'Invalid WORKHUB_DATA_DIR.';; esac
[ -d "$WORKHUB_DATA_DIR" ] && [ -w "$WORKHUB_DATA_DIR" ] || fail 'Provide a writable data directory owned by UID 10001.'
export PORT WORKHUB_DATA_DIR
# No secret is placed in JVM arguments or console output. Fixed public scheme/host
# are valid only when the internal listener is isolated behind the approved proxy.
CATALINA_OPTS="-Xms64m -Xmx256m -Dspring.profiles.active=container-demo -Dworkhub.port=$PORT -Dworkhub.publicHost=$WORKHUB_PUBLIC_HOST"
export CATALINA_OPTS
exec /usr/local/tomcat/bin/catalina.sh run
