#!/bin/sh
set -eu

# Never adopt an existing v1 or unmarked volume. No automatic migration.
marker=/var/lib/rabbitmq/.eventomax-local-bootstrap-v2
[ ! -f /var/lib/rabbitmq/.eventomax-local-bootstrap-v1 ] || {
  echo 'Legacy local volume: migration requires separate review; nothing changed.' >&2
  exit 1
}
rm -f /tmp/eventomax-bootstrap-ready
if [ ! -f "$marker" ] && [ -d /var/lib/rabbitmq/mnesia ] &&
   [ -n "$(find /var/lib/rabbitmq/mnesia -mindepth 1 -print -quit)" ]; then
  echo 'Refusing bootstrap on a pre-existing unmarked volume. Use a new isolated Compose project.' >&2
  exit 1
fi

# Only password hashes enter the container; no passwords in environment or CLI args.
# At-boot import suppresses creation of the default guest account on a blank node.
cp /etc/rabbitmq/rabbitmq.conf /tmp/eventomax-local.conf
if [ ! -f "$marker" ]; then
  [ -s /run/secrets/local_identities ] || {
    echo 'Private identities file missing; run prepare-local.py first.' >&2
    exit 1
  }
  # Compose file secrets retain host ownership on Linux. Copy hashes privately for the broker UID.
  (umask 077; cp /run/secrets/local_identities /tmp/eventomax-identities.json)
  chown rabbitmq:rabbitmq /tmp/eventomax-identities.json
  chmod 600 /tmp/eventomax-identities.json
  printf '\ndefinitions.import_backend = local_filesystem\ndefinitions.local.path = /tmp/eventomax-identities.json\n' >> /tmp/eventomax-local.conf
fi
export RABBITMQ_CONFIG_FILE=/tmp/eventomax-local
# Ignore inherited defaults; identities come exclusively from the private import.
unset RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS RABBITMQ_DEFAULT_VHOST
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
  # Identities have been seeded securely at boot. Import the unchanged business topology.
  if ! gosu rabbitmq rabbitmqctl import_definitions /etc/rabbitmq/definitions.json >/dev/null 2>&1; then
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
