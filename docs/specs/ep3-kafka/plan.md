# Plan de Arquitectura e Implementación — Cierre EP3 Kafka (plan.md)

## 1. Estrategia y Justificación Tecnológica

Para implementar los requisitos de la EP3 sin sobreingeniería y respetando el ecosistema existente de Spring Boot 3 / Java 21, Angular 18/19 y Docker Compose, se define la siguiente arquitectura por capas:

### 1.1. Infraestructura de Streaming (`infra-eventomax/kafka`)
- **Imágenes:** `confluentinc/cp-zookeeper:7.8.11` y `confluentinc/cp-kafka:7.8.11`.
- **Topología:**
  - 3 contenedores ZooKeeper formando ensemble (puertos internos 2888/3888, clientes en 2181, 2182, 2183).
  - 3 brokers Kafka con IDs 1, 2 y 3.
  - Listener dual: `INTERNAL` para comunicación entre contenedores de la red Docker (`eventomax-net`) y `EXTERNAL` para acceso desde fuera del host.
  - Parámetros de resiliencia:
    - `default.replication.factor: 3`
    - `min.insync.replicas: 2`
    - `offsets.topic.replication.factor: 3`
    - `transaction.state.log.replication.factor: 3`
    - `transaction.state.log.min.isr: 2`
- **Gestión:** `provectuslabs/kafka-ui:v0.7.2` configurado para monitorizar los 3 brokers y sus métricas JMX.
- **Aprovisionamiento de tópicos:** Script `init-topics.sh` extendido con comandos idempotentes (`--if-not-exists`) que crean tópicos principales y DLTs con particiones=3 y RF=3.

### 1.2. Microservicio `kafka-admin` (`infra-eventomax/kafka-admin`)
- **Stack:** Spring Boot 3.3.x, Java 21, `spring-kafka` (`org.apache.kafka:kafka-clients`), Spring Security OAuth2 Resource Server.
- **Patrón:** Siguiendo la arquitectura limpia y probada de `rabbit-admin`, se encapsula `AdminClient` en un `KafkaAdminService` desacoplado de controladores web.
- **Endpoints:**
  - `GET /api/kafka-admin/topics`: listado y metadatos de tópicos.
  - `POST /api/kafka-admin/topics`: creación declarativa de tópico.
  - `DELETE /api/kafka-admin/topics/{name}`: eliminación protegida (solo de prueba).
- **Seguridad:** Validación de JWT de Azure Entra ID; requiere rol `Admin` y scope `access_as_user`.
- **BFF:** `ms-eventomax-bff` añade `KafkaAdminRoutingController` y regla de seguridad `.requestMatchers("/api/kafka-admin/**").access(scopeAndAnyRole(ADMIN))`.

### 1.3. Analítica Streaming (`ms-eventomax-report`)
- **Entidad Hecho:** `ReportProductionFact` ya registra los cambios de estado con marcas de tiempo `occurred_at`.
- **KPI Tiempo de Montaje:**
  - Consulta JPA que empareja para cada `production_id` el evento `new_status = 'EN_MONTAJE'` con su posterior `new_status = 'EN_EJECUCION'`.
  - Calcula la duración `EXTRACT(EPOCH FROM (ejecucion.occurred_at - montaje.occurred_at)) / 60.0` y promedia los valores del período.
- **KPI Eventos por Día:**
  - Consulta agregada: `SELECT CAST(f.occurred_at AS LocalDate), count(f) FROM ReportProductionFact f WHERE f.occurred_at BETWEEN :from AND :to GROUP BY CAST(f.occurred_at AS LocalDate) ORDER BY 1 ASC`.
  - Devuelve lista de `DailyEventCountDto(LocalDate date, long count)`.
- **DTOs y API:** Extensión de `KpiMetricsDto` y `KpisReportResponse` manteniendo compatibilidad retroactiva.

### 1.4. Trazabilidad y Tópico `audit.timeline` (`ms-eventomax-audit`)
- **Productor / Publicador:** `ms-eventomax-audit` (o relay de dominio) publica un mensaje consolidado en `audit.timeline` con clave `productionId` tras validar e ingerir el evento.
- **Trazabilidad de Procedencia:** Extensión del modelo `AuditEvent` para incluir el atributo `source` (por defecto `"web-ui"` o extraído de cabeceras HTTP propagadas).

### 1.5. Interfaz de Usuario y Panel en Tiempo Real (`frontend-eventomax`)
- **Componente Reportes:**
  - Tarjeta de métrica: "Tiempo Promedio de Montaje" formateado en horas o minutos.
  - Sección visual para "Eventos por Día" con barras o tabla cronológica.
- **Panel en Tiempo Real:**
  - Incorporación de `timer(0, 30000)` en los observables de estado de `reports.ts` y `dashboard.ts`, refrescando los datos cada 30 segundos automáticamente sin parpadeos de interfaz.

### 1.6. Asignación de Cuadrilla (`ms-eventomax-productions`)
- **Modelo:** Campo `crew_id` (String o Long) y `crew_name` en entidad `Production`.
- **DTOs:** Actualización de `ProductionRequestDTO` y `ProductionStatusUpdateDTO`.
- **Integración:** El evento emitido hacia RabbitMQ `q.cmd.crew` y el ticket almacenado en Notify llevarán la cuadrilla asignada explícita.

---

## 2. Fases de Ejecución Progresiva (Paso a Paso)

```text
Paso 1: Infraestructura Kafka (Clúster 3 ZK + 3 Brokers + Tópicos DLT)
Paso 2: Microservicio kafka-admin + Integración BFF
Paso 3: Analítica en ms-eventomax-report (Tiempo de Montaje y Eventos/Día)
Paso 4: Auditoría audit.timeline y Registro de Origen
Paso 5: Frontend Angular (KPIs UI + Refresco Reactivo en Tiempo Real)
Paso 6: Asignación de Cuadrilla en Producciones
```

