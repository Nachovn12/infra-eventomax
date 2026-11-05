# Informe de Verificación y Auditoría de Calidad — EP3 / EP5 Kafka (review.md)

**Fecha:** 2026-11-05  
**Proyecto:** EventoMax — EP3 / EP5 Kafka Streaming, Clúster RabbitMQ y Trazabilidad Operativa  
**Metodología:** Specspire (Verificación disciplinada de requerimientos contra código y pruebas)  

---

## 1. Reconciliación de Requisitos (`Caso 8 - EventoMax` y Rúbrica `EP5_DSY1107`)

| Requisito / Indicador Rúbrica EP5 | Estado | Evidencia de Código | Evidencia de Prueba |
|:---|:---:|:---|:---|
| **`RF-01`: Clúster Kafka Multi-Broker + ZooKeeper** | **CUMPLIDO** | `infra-eventomax/kafka/compose.yml` y `compose.prod.yml`: 3 ZK (`zookeeper-1..3`), 3 Kafka Brokers (`kafka-1..3`), `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3`, `KAFKA_DEFAULT_REPLICATION_FACTOR=3`, `KAFKA_MIN_INSYNC_REPLICAS=2`. | Validado con `docker compose config -q`. Salud verificada por broker (`9092`, `9093`, `9094`). |
| **`RF-02`: Tópicos con Replicación 3, Políticas de Retención/Compactación y DLTs** | **CUMPLIDO** | `infra-eventomax/kafka/init-topics.sh`: Aprovisionamiento idempotente de `productions.events` (`delete`, 7d), `audit.timeline` (`compact,delete`, 30d), `productions.events.report.DLT` (`delete`, 14d), `productions.events-audit-retry` y `productions.events-audit-dlt`. | Aprovisionamiento con `--partitions 3 --replication-factor 3` y `min.insync.replicas=2`. |
| **`RF-03`: Microservicio `kafka-admin` (CRUD completo de Tópicos, Configuraciones, Particiones y Consumer Groups) + Proxy BFF** | **CUMPLIDO** | `infra-eventomax/kafka-admin`: Spring Boot 4 / Java 25 con `AdminClient`. Soporta `GET/POST/DELETE /topics`, `PUT /topics/{name}/configs` (`incrementalAlterConfigs`), `PUT /topics/{name}/partitions` (`createPartitions`), y `GET/DELETE /consumer-groups`. Enrutado en `ms-eventomax-bff` (`KafkaAdminRoutingController`). | `kafka-admin`: **26 tests** unitarios/WebMvc aprobados (0 fallos). `ms-eventomax-bff`: **237 tests** aprobados (0 fallos). |
| **`RF-04`: Productores y Consumidores Kafka con DTOs, Consumer Groups y Manejo de Errores** | **CUMPLIDO** | Productores: `ms-eventomax-productions` (`KafkaEventPublisher` → `productions.events`) y `ms-eventomax-audit` (`KafkaEventEnvelope` → `audit.timeline`). Consumidores: `ms-eventomax-report` (`groupId="report-service"`, DLT publisher) y `ms-eventomax-audit` (`groupId="eventomax-audit-cg"`, `@RetryableTopic`). | `ms-eventomax-productions`: 76 tests. `ms-eventomax-report`: 52 tests. `ms-eventomax-audit`: 7 tests. |
| **`RF-05`: Clúster RabbitMQ y Kafka-UI con Administración Dinámica** | **CUMPLIDO** | Clúster RabbitMQ de 2 nodos (`rabbitmq-1`, `rabbitmq-2`) en `infra-eventomax/mq/compose.cluster.yml` y `compose.prod.yml` con `rabbitmq.prod.conf` (`classic_config`) y `definitions.json`. Kafka-UI en `infra-eventomax/kafka/compose.yml` y `compose.kafka-ui.yml` con `DYNAMIC_CONFIG_ENABLED="true"`. | Todos los manifiestos Docker Compose validados sin errores con `docker compose config -q`. |
| **`RF-06`: Visualización de KPIs y Refresco Automático en Frontend** | **CUMPLIDO** | `frontend-eventomax`: Tarjeta de "Tiempo Promedio de Montaje", tabla de "Eventos por Día", intervalo reactivo de auto-refresco (30s con `DestroyRef`) en `/reports` y `/dashboard`. | 24 suites de prueba (**199 tests** aprobados, 0 fallos) en Vitest y `npm run build` exitoso. |
| **`RF-07`: Asignación de Cuadrilla Técnica** | **CUMPLIDO** | `ms-eventomax-productions`: Migración `V4__add_crew_assignment_to_productions.sql`, entidad `Production` con `crew_id` y `crew_name`, propagación hacia `CrewTicketPayload` y RabbitMQ `q.cmd.crew`. `ms-eventomax-notify`: Inclusión de cuadrilla en hoja de ruta PDF. | `ms-eventomax-productions`: 76 tests aprobados. `ms-eventomax-notify`: 83 tests aprobados. `ms-eventomax-catalog`: 51 tests aprobados. |

---

## 2. Resumen de Ejecución de Pruebas Automatizadas

| Repositorio / Módulo | Framework / Herramienta | Tests Ejecutados | Tests Aprobados | Fallos / Errores | Estado |
|:---|:---|:---:|:---:|:---:|:---:|
| `frontend-eventomax` | Vitest / Angular TestBed | 199 | 199 | 0 | **PASS** |
| `ms-eventomax-bff` | JUnit 5 / Spring Boot Test | 237 | 237 | 0 | **PASS** |
| `infra-eventomax/kafka-admin` | JUnit 5 / MockMvc / Mockito | 26 | 26 | 0 | **PASS** |
| `ms-eventomax-productions` | JUnit 5 / DataJpaTest / Mockito | 76 | 76 | 0 | **PASS** |
| `ms-eventomax-catalog` | JUnit 5 / Mockito | 51 | 51 | 0 | **PASS** |
| `ms-eventomax-report` | JUnit 5 / Mockito | 52 | 52 | 0 | **PASS** |
| `ms-eventomax-audit` | JUnit 5 / MockMvc | 7 | 7 | 0 | **PASS** |
| `ms-eventomax-notify` | JUnit 5 / Spring Boot Test | 83 | 83 | 0 | **PASS** |
| **Total Global** | — | **731** | **731** | **0** | **PASS** |

---

## 3. Controles de Calidad, Seguridad y Buenas Prácticas Senior Backend

1. **Administración Segura de Kafka (`kafka-admin`):**
   - Whitelist estricta de claves configurables (`ALLOWED_CONFIG_KEYS`: `retention.ms`, `cleanup.policy`, `min.insync.replicas`, `max.message.bytes`, etc.) para prevenir inyección de propiedades arbitrarias en los brokers.
   - Protección de tópicos nucleares (`productions.events`, `audit.timeline`, DLTs) y grupos de consumidores activos (`report-service`, `eventomax-audit-cg`) contra eliminación accidental (`HTTP 403 Forbidden`).
   - Alteración incremental de configuraciones (`AdminClient.incrementalAlterConfigs`) y escalado monotónico creciente de particiones (`AdminClient.createPartitions`).

2. **Seguridad y Control de Acceso (RBAC + OAuth2 / Entra ID):**
   - Todos los endpoints de `/api/kafka-admin/**` requieren rol `Admin` y scope `access_as_user`.
   - El BFF valida tokens JWT emitidos por Microsoft Entra ID y propaga encabezados de trazabilidad (`X-Trace-Id`, `X-Correlation-Id`).

3. **Resiliencia de Mensajería y Streaming:**
   - Clúster Kafka: 3 ZooKeepers + 3 Brokers (`RF=3`, `min.insync.replicas=2`).
   - Clúster RabbitMQ: 2 nodos en clúster (`rabbit@rabbitmq-1`, `rabbit@rabbitmq-2`) con descubrimiento `classic_config`, cookie Erlang compartida y topología de colas durables + DLX (`cmd.dead.dlx`).
