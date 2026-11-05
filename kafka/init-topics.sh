#!/bin/bash
set -e

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:29092,kafka-2:29093,kafka-3:29094}"
RF="${KAFKA_REPLICATION_FACTOR:-3}"

echo "[EventoMax] Waiting for Kafka brokers to be ready on $BOOTSTRAP..."
FIRST_BROKER=$(echo "$BOOTSTRAP" | awk -F',' '{print $1}')

for i in $(seq 1 30); do
  if kafka-topics --bootstrap-server "$FIRST_BROKER" --list >/dev/null 2>&1; then
    echo "[EventoMax] Kafka broker $FIRST_BROKER is accessible."
    break
  fi
  echo "[EventoMax] Waiting for Kafka... ($i/30)"
  sleep 2
done

echo "[EventoMax] Creating Kafka topics with Replication Factor = $RF..."

# 1. productions.events (Caso 8, Sec. 9: 3 particiones, 3 réplicas, delete, retención 7 días)
kafka-topics --create \
  --if-not-exists \
  --bootstrap-server "$BOOTSTRAP" \
  --topic productions.events \
  --partitions 3 \
  --replication-factor "$RF" \
  --config cleanup.policy=delete \
  --config retention.ms=604800000

# 2. audit.timeline (Caso 8, Sec. 9: 3 particiones, 3 réplicas, compact,delete, retención 30 días)
kafka-topics --create \
  --if-not-exists \
  --bootstrap-server "$BOOTSTRAP" \
  --topic audit.timeline \
  --partitions 3 \
  --replication-factor "$RF" \
  --config cleanup.policy=compact,delete \
  --config retention.ms=2592000000

# 3. productions.events.report.DLT (Caso 8, Sec. 9: *.DLT por consumidor, delete, 14 días)
kafka-topics --create \
  --if-not-exists \
  --bootstrap-server "$BOOTSTRAP" \
  --topic productions.events.report.DLT \
  --partitions 3 \
  --replication-factor "$RF" \
  --config cleanup.policy=delete \
  --config retention.ms=1209600000

# 4. productions.events-audit-retry (Consumer retry topic for Audit)
kafka-topics --create \
  --if-not-exists \
  --bootstrap-server "$BOOTSTRAP" \
  --topic productions.events-audit-retry \
  --partitions 3 \
  --replication-factor "$RF" \
  --config cleanup.policy=delete \
  --config retention.ms=604800000

# 5. productions.events-audit-dlt (Consumer DLT topic for Audit)
kafka-topics --create \
  --if-not-exists \
  --bootstrap-server "$BOOTSTRAP" \
  --topic productions.events-audit-dlt \
  --partitions 3 \
  --replication-factor "$RF" \
  --config cleanup.policy=delete \
  --config retention.ms=1209600000

echo "[EventoMax] Kafka topics created successfully. Topic details:"
kafka-topics --bootstrap-server "$BOOTSTRAP" --describe
