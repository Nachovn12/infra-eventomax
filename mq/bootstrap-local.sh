#!/bin/sh
set -eu

# This entrypoint is for a new LOCAL volume only; never adopt a shared broker.
: "${RABBITMQ_DEFAULT_USER:?Set the private local username}"
: "${RABBITMQ_DEFAULT_PASS:?Set the private local password}"
case "$RABBITMQ_DEFAULT_USER" in
  guest|'') echo 'A dedicated non-guest local user is required' >&2; exit 1 ;;
esac
[ "${RABBITMQ_DEFAULT_VHOST:-/}" = '/' ] || {
  echo 'The existing EventoMax topology uses vhost /; custom vhosts need a reviewed topology' >&2
  exit 1
}
marker=/var/lib/rabbitmq/.eventomax-local-bootstrap-v1
rm -f /tmp/eventomax-bootstrap-ready
if [ ! -f "$marker" ] && [ -d /var/lib/rabbitmq/mnesia ] &&
   [ -n "$(find /var/lib/rabbitmq/mnesia -mindepth 1 -print -quit)" ]; then
  echo 'Refusing bootstrap on a pre-existing unmarked volume. Use a new isolated Compose project.' >&2
  exit 1
fi

docker-entrypoint.sh rabbitmq-server &
server_pid=$!
trap 'kill -TERM "$server_pid" 2>/dev/null || true; wait "$server_pid" || true' TERM INT
ready=false
deadline=$(( $(date +%s) + 90 ))
while [ "$(date +%s)" -lt "$deadline" ] && kill -0 "$server_pid" 2>/dev/null; do
  if gosu rabbitmq rabbitmqctl await_startup --timeout 3 >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo 'RabbitMQ did not start; bootstrap incomplete' >&2
  kill -TERM "$server_pid" 2>/dev/null || true
  wait "$server_pid" || true
  exit 1
fi

if [ ! -f "$marker" ]; then
  # The normal broker startup has already seeded user, credentials and vhost from env.
  # Import only topology, then restrict the application user to EventoMax resources.
  if ! gosu rabbitmq rabbitmqctl import_definitions /etc/rabbitmq/definitions.json >/dev/null 2>&1 ||
     ! gosu rabbitmq rabbitmqctl set_user_tags "$RABBITMQ_DEFAULT_USER" >/dev/null 2>&1 ||
     ! gosu rabbitmq rabbitmqctl set_permissions -p / "$RABBITMQ_DEFAULT_USER" \
       '^(cmd\.(direct|topic|dead\.dlx)|q\.cmd\.(email|crew|quote)(\.dlq)?)$' \
       '^(cmd\.(direct|topic|dead\.dlx)|q\.cmd\.(email|crew|quote)(\.dlq)?)$' \
       '^(cmd\.(direct|topic|dead\.dlx)|q\.cmd\.(email|crew|quote)(\.dlq)?)$' >/dev/null 2>&1; then
    echo 'Local bootstrap failed; broker will stop without changing any other volume' >&2
    kill -TERM "$server_pid" 2>/dev/null || true
    wait "$server_pid" || true
    exit 1
  fi
  touch "$marker"
fi
touch /tmp/eventomax-bootstrap-ready
echo 'EventoMax local bootstrap ready (credentials not logged)'
wait "$server_pid"
