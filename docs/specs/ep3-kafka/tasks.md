# Lista de Tareas Verificables — Cierre EP3 Kafka (tasks.md)

## Tareas por Incremento Vertical

### Incremento 1: Infraestructura Kafka y Tópicos Distribuidos
- [x] **T01 [RF-01, RF-02] Configuración de Clúster Kafka (3 ZK + 3 Brokers) y Script de Tópicos con RF=3 y DLTs**
  - **Ubicación:** `infra-eventomax/kafka/compose.yml`, `infra-eventomax/kafka/compose.prod.yml` e `infra-eventomax/kafka/init-topics.sh`.
  - **Acción:** Definidos 3 servicios ZooKeeper y 3 servicios Kafka con replicación cruzada, listeners internos/externos y salud configurable. Actualizado `init-topics.sh` para crear `productions.events`, `audit.timeline`, `productions.events.report.DLT`, `productions.events-audit-retry` y `productions.events-audit-dlt` con particiones=3 y replication-factor=3.
  - **Prueba/Evidencia:** Manifiestos YAML validados sintácticamente (8 servicios por compose), script con soporte para bootstrap dinámico y particionamiento/replicación = 3.
  - **Dependencias:** Ninguna.

### Incremento 2: Microservicio Kafka Admin y BFF
- [x] **T02 [RF-03] Desarrollo de Microservicio `kafka-admin`**
  - **Ubicación:** `infra-eventomax/kafka-admin/`
  - **Acción:** Creada aplicación Spring Boot 4/Java 25 con `spring-kafka`, `KafkaAdminService` (usando `AdminClient`), endpoints REST (`/api/kafka-admin/topics`), seguridad Entra ID para rol `Admin` y suite de pruebas unitarias/integración (15 tests aprobados).
  - **Prueba/Evidencia:** `BUILD SUCCESS` en `.\mvnw.cmd test` (15 tests ejecutados: 0 fallos, 0 errores).
  - **Dependencias:** T01.

- [x] **T03 [RF-03] Enrutamiento Seguro de `kafka-admin` en `ms-eventomax-bff`**
  - **Ubicación:** `ms-eventomax-bff/`
  - **Acción:** Agregado `KafkaAdminRoutingController.java`, mapeadas rutas en `DomainRoutingConfiguration`, `DomainRoutingClient` y configurada regla de acceso en `SecurityConfiguration` restringida al rol `Admin`.
  - **Prueba/Evidencia:** `BUILD SUCCESS` en `.\mvnw.cmd test` en `ms-eventomax-bff` (235 tests ejecutados: 0 fallos, 0 errores).
  - **Dependencias:** T02.

### Incremento 3: Analítica Streaming en Report
- [x] **T04 [RF-04] Implementación de KPIs "Tiempo de Montaje" y "Eventos por Día"**
  - **Ubicación:** `ms-eventomax-report/`
  - **Acción:** Creado `DailyEventCountDto`, extendido `KpiMetricsDto`, implementado cálculo de tiempo de montaje promedio (`EN_MONTAJE` a `EN_EJECUCIÓN`) y agregación cronológica de eventos por fecha en `ReportServiceImpl`. Agregado test específico en `ReportServiceImplTest`.
  - **Prueba/Evidencia:** `BUILD SUCCESS` en `.\mvnw.cmd test -Dtest="!*IntegrationTest"` (52 tests ejecutados: 0 fallos, 0 errores).
  - **Dependencias:** Ninguna.

### Incremento 4: Auditoría y Línea de Tiempo
- [x] **T05 [RF-05] Publicación en `audit.timeline` y Atributo de Origen**
  - **Ubicación:** `ms-eventomax-audit/`
  - **Acción:** Habilitada publicación hacia `audit.timeline` con clave agregada `productionId`, agregado atributo `source` en modelo `AuditEvent`, migración Flyway `V5__add_source_to_audit_event.sql`, DTO `AuditEventResponse`, mapeo en `AuditQueryService` y test unitario.
  - **Prueba/Evidencia:** `BUILD SUCCESS` en `.\mvnw.cmd test -Dtest=AuditControllerTest` (7 tests ejecutados: 0 fallos, 0 errores).
  - **Dependencias:** T01.

### Incremento 5: Experiencia de Usuario en Frontend
- [x] **T06 [RF-06] Visualización de KPIs y Actualización Automática en Tiempo Real**
  - **Ubicación:** `frontend-eventomax/`
  - **Acción:** En `reports.html`/`reports.ts`, añadida tarjeta para el tiempo de montaje y sección con tabla/lista de eventos por día. En `reports.model.ts`, añadidos los campos del DTO. Incorporado refresco periódico reactivo mediante `setInterval` cada 30 segundos acoplado al ciclo de vida (`DestroyRef`) en `reports.ts` y `dashboard.ts`.
  - **Prueba/Evidencia:** `npm test -- --watch=false` (24 archivos de prueba, 199 tests aprobados, 0 fallos) y `npm run build` completado exitosamente.
  - **Dependencias:** T04.

### Incremento 6: Dominio y Asignación de Cuadrilla
- [x] **T07 [RF-07] Asignación de Cuadrilla en Producciones y Enriquecimiento de Ticket**
  - **Ubicación:** `ms-eventomax-productions/` y `ms-eventomax-notify/`
  - **Acción:** Añadida migración Flyway `V4__add_crew_assignment_to_productions.sql` con columnas `crew_id` y `crew_name`. Modificada la entidad `Production`, `ProductionRequestDTO`, `ProductionResponseDTO`, `ProductionStatusUpdateDTO`, `ProductionCreatedEvent` y `ProductionStatusChangedEvent`. En `ProductionCrewCommandListener`, propagado `crewId` y `crewName` hacia `CrewTicketPayload` y RabbitMQ `q.cmd.crew`. Adaptado `CrewTicketPayload` y `DatabaseCrewTicketSender` en `ms-eventomax-notify` para incorporar datos de cuadrilla en la hoja de ruta.
  - **Prueba/Evidencia:** `BUILD SUCCESS` en `ms-eventomax-productions` (76 tests unitarios/persistencia aprobados) y `BUILD SUCCESS` en `ms-eventomax-notify` (83 tests aprobados).
  - **Dependencias:** Ninguna.
