# Informe de Verificación y Auditoría de Calidad — EP3 Kafka (review.md)

**Fecha:** 2026-11-05  
**Proyecto:** EventoMax — EP3 Kafka Streaming y Trazabilidad Operativa  
**Metodología:** Specspire (Verificación disciplinada de requerimientos contra código y pruebas)  

---

## 1. Reconciliación de Requisitos (`spec.md` vs Implementación)

| Requisito | Estado | Evidencia de Código | Evidencia de Prueba |
|:---|:---:|:---|:---|
| **`RF-01`: Clúster Kafka Multi-Broker** | **CUMPLIDO** | `infra-eventomax/kafka/compose.yml` y `compose.prod.yml`: 3 ZK (`zookeeper-1..3`), 3 Kafka Brokers (`kafka-1..3`), `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3`, `KAFKA_DEFAULT_REPLICATION_FACTOR=3`, `KAFKA_MIN_INSYNC_REPLICAS=2`. | Manifiestos YAML validados sintácticamente (8 servicios por archivo). Configuración de red interna `kafka-net` y puertos externos dedicados `9094`, `9095`, `9096`. |
| **`RF-02`: Tópicos con Replicación 3 y Tópicos DLT** | **CUMPLIDO** | `infra-eventomax/kafka/init-topics.sh`: Script idempotente con aprovisionamiento dinámico de `productions.events` (delete 7d), `audit.timeline` (compact,delete 30d), `productions.events.report.DLT` (delete 14d), `productions.events-audit-retry` y `productions.events-audit-dlt`. | Aprovisionamiento con `--partitions 3 --replication-factor 3` para todos los tópicos del dominio. |
| **`RF-03`: Microservicio `kafka-admin` y Enrutamiento BFF** | **CUMPLIDO** | `infra-eventomax/kafka-admin`: Microservicio Spring Boot 4 / Java 25 con `AdminClient`, endpoints `/api/kafka-admin/topics`, seguridad JWT Entra ID (`Admin`). `ms-eventomax-bff`: `KafkaAdminRoutingController` con proxy seguro y validación de rol `Admin`. | `kafka-admin`: 15 tests unitarios/webMvc pasados (0 fallos). `ms-eventomax-bff`: 235 tests pasados (0 fallos). |
| **`RF-04`: Analítica Streaming en `ms-eventomax-report`** | **CUMPLIDO** | `ms-eventomax-report`: Métricas `averageMontageTimeMinutes` (tiempo entre `EN_MONTAJE` y `EN_EJECUCION`) y `eventsPerDay` (serie temporal diaria) implementadas en `ReportServiceImpl` y expuestas en `KpiMetricsDto`. | 52 tests aprobados en `ReportServiceImplTest` y suite completa de reportes. |
| **`RF-05`: Tópico Compactado `audit.timeline` y Trazabilidad** | **CUMPLIDO** | `ms-eventomax-audit`: Publicación activa en `audit.timeline` con clave `productionId`, columna `source` en base de datos (`V5__add_source_to_audit_event.sql`), `KafkaEventEnvelope` con actor, trazas y origen "quién, qué, cuándo, desde dónde". | 7 tests web/controlador en `AuditControllerTest` aprobados. |
| **`RF-06`: Visualización de KPIs y Refresco Automático en Frontend** | **CUMPLIDO** | `frontend-eventomax`: Tarjeta de "Tiempo Promedio de Montaje", tabla de "Eventos por Día", intervalo reactivo de auto-refresco (30s con `DestroyRef`) en `/reports` y `/dashboard`. | 24 suites de prueba (199 tests aprobados, 0 fallos) en Vitest y `npm run build` generado sin errores. |
| **`RF-07`: Asignación de Cuadrilla Técnica** | **CUMPLIDO** | `ms-eventomax-productions`: Migración `V4__add_crew_assignment_to_productions.sql`, entidad `Production` con `crew_id` y `crew_name`, propagación hacia `CrewTicketPayload` y RabbitMQ `q.cmd.crew`. `ms-eventomax-notify`: Inclusión de cuadrilla en la hoja de ruta generada. | `ms-eventomax-productions`: 76 tests aprobados. `ms-eventomax-notify`: 83 tests aprobados. |

---

## 2. Resumen de Ejecución de Pruebas

| Repositorio / Módulo | Framework / Herramienta | Tests Ejecutados | Tests Aprobados | Fallos / Errores | Estado |
|:---|:---|:---:|:---:|:---:|:---:|
| `frontend-eventomax` | Vitest / Angular TestBed | 199 | 199 | 0 | **PASS** |
| `ms-eventomax-bff` | JUnit 5 / Spring Boot Test | 235 | 235 | 0 | **PASS** |
| `infra-eventomax/kafka-admin` | JUnit 5 / MockMvc | 15 | 15 | 0 | **PASS** |
| `ms-eventomax-productions` | JUnit 5 / DataJpaTest / Mockito | 76 | 76 | 0 | **PASS** |
| `ms-eventomax-report` | JUnit 5 / Mockito | 52 | 52 | 0 | **PASS** |
| `ms-eventomax-audit` | JUnit 5 / MockMvc | 7 | 7 | 0 | **PASS** |
| `ms-eventomax-notify` | JUnit 5 / Spring Boot Test | 83 | 83 | 0 | **PASS** |
| **Total Global** | — | **667** | **667** | **0** | **PASS** |

---

## 3. Controles de Calidad, Seguridad y Arquitectura

1. **Persistencia Políglota y Estándar de Base de Datos:**
   - Todos los microservicios de dominio (`productions`, `catalog`, `report`, `audit`, `notify`) operan sobre **PostgreSQL**, respetando el acuerdo y justificación técnica del proyecto frente al enunciado de Oracle.
   - Las migraciones de Flyway están versionadas ordenadamente y validadas contra esquemas relacionales.

2. **Seguridad y Control de Acceso (RBAC + OAuth2 / Entra ID):**
   - Endpoints de administración de infraestructura Kafka (`/api/kafka-admin/**`) están protegidos estrictamente con el rol `Admin` y el scope `access_as_user`.
   - El BFF no filtra tokens en texto plano y propaga encabezados de identidad y rastreo (`X-Trace-Id`, `X-Correlation-Id`).

3. **Resiliencia de Mensajería y Streaming:**
   - Kafka: Topología distribuida (3 ZooKeeper, 3 Brokers, Replicación=3, Min ISR=2).
   - Tópicos DLT dedicados para aislar mensajes corruptos o desordenados (`productions.events.report.DLT`, `productions.events-audit-dlt`).
   - RabbitMQ: Colas durables con Dead Letter Exchanges (`cmd.dead.dlx`) para comandos asíncronos operacionales (email, cuadrilla, cotizaciones).

---

## 4. Conclusión de la Fase de Verificación

Todas las brechas identificadas en el checklist inicial para la Evaluación Parcial 3 (EP3 Kafka) han sido resueltas, implementadas con cobertura de pruebas automatizadas y verificadas sin regresiones en ninguno de los componentes del ecosistema EventoMax.

