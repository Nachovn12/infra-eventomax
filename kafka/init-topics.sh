#!/bin/bash
set -e

echo "[EventoMax] Creating Kafka topics..."

kafka-topics --create \
  --if-not-exists \
  --bootstrap-server eventomax-kafka:29092 \
  --topic productions.events \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=delete \
  --config retention.ms=604800000

kafka-topics --create \
  --if-not-exists \
  --bootstrap-server eventomax-kafka:29092 \
  --topic audit.timeline \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=compact,delete \
  --config retention.ms=2592000000

echo "[EventoMax] Kafka topics ready."
