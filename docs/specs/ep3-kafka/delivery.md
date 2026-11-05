# Manual de Entrega y Guía de Operación — EP3 / EP5 Kafka EventoMax (delivery.md)

**Versión:** 1.1.0  
**Fecha:** 2026-11-05  
**Proyecto:** EventoMax — Cloud Native I (EP3 / EP5 Apache Kafka & RabbitMQ Cluster)  

---

## 1. Arquitectura Desplegada

El sistema opera distribuido en 3 instancias virtuales EC2 (o entornos de contenedores Docker locales) comunicados mediante red interna:

```
+-----------------------------------------------------------------------------------+
| ec2-kafka: Apache Kafka & ZooKeeper Cluster                                      |
|  - zookeeper-1, zookeeper-2, zookeeper-3 (Puertos 2181, 2182, 2183)               |
|  - kafka-1, kafka-2, kafka-3 (Listeners: 9092-9094, RF=3, ISR=2)                  |
|  - kafka-ui (Puerto 8080, DYNAMIC_CONFIG_ENABLED=true)                            |
|  - kafka-admin (Spring Boot Microservice en puerto 8087)                          |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ Eventos de Dominio (productions.events)
                                         │ Timeline Compactado (audit.timeline)
                                         │ DLTs (productions.events.report.DLT, etc.)
                                         ▼
+-----------------------------------------------------------------------------------+
| ec2-apps: Microservicios de Dominio & Persistencia (PostgreSQL)                   |
|  - PostgreSQL (Bases de datos independientes: productions, catalog, report, audit)|
|  - ms-eventomax-productions (8081) - Productor Kafka y asignación de cuadrilla    |
|  - ms-eventomax-catalog (8082)     - Inventario de equipamiento y reservas        |
|  - ms-eventomax-report (8084)      - Consumidor Kafka (groupId: report-service)   |
|  - ms-eventomax-audit (8085)       - Consumidor/Productor Kafka (audit.timeline)  |
|  - ms-eventomax-bff (8080 / 8090)  - Enrutamiento seguro Entra ID (JWT + RBAC)    |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ Comandos Asíncronos (q.cmd.crew, q.cmd.email)
                                         ▼
+-----------------------------------------------------------------------------------+
| ec2-mq: Clúster RabbitMQ                                                          |
|  - rabbitmq-1 (AMQP 5672, Management UI 15672)                                    |
|  - rabbitmq-2 (AMQP 5673, Management UI 15673)                                    |
|  - ms-eventomax-notify (8083) - Emisión de tickets PDF y hojas de ruta de cuadrilla|
+-----------------------------------------------------------------------------------+
```

---

## 2. Protocolo de Arranque en Frío (Cold Start)

### Paso 1: Levantar Clúster Kafka (ZooKeeper + 3 Brokers + Kafka-UI)
```bash
cd infra-eventomax/kafka

# 1. Iniciar los 3 ZooKeepers, los 3 Brokers Kafka, kafka-init y Kafka-UI
docker compose -f compose.yml up -d

# (Opcional) Si desea administrar únicamente el servicio Kafka-UI de forma modular:
docker compose -f compose.kafka-ui.yml up -d

# 2. Verificar estado de los contenedores
docker compose ps
```

### Paso 2: Levantar Clúster RabbitMQ (2 Nodos en Clúster)
```bash
cd infra-eventomax/mq

# Iniciar clúster RabbitMQ de 2 nodos (rabbitmq-1 y rabbitmq-2) con topología definitions.json
docker compose -f compose.cluster.yml up -d

# Verificar que ambos nodos formen el clúster correctamente
docker exec eventomax-rabbitmq-1 rabbitmqctl cluster_status
```

### Paso 3: Levantar Microservicio Administrador de Kafka (`kafka-admin`)
```bash
cd infra-eventomax/kafka-admin
./mvnw spring-boot:run
```

### Paso 4: Levantar Microservicios de Dominio y BFF
```bash
# ms-eventomax-productions:
cd ms-eventomax-productions && ./mvnw spring-boot:run &
# ms-eventomax-catalog:
cd ms-eventomax-catalog && ./mvnw spring-boot:run &
# ms-eventomax-report:
cd ms-eventomax-report && ./mvnw spring-boot:run &
# ms-eventomax-audit:
cd ms-eventomax-audit && ./mvnw spring-boot:run &
# ms-eventomax-notify:
cd ms-eventomax-notify && ./mvnw spring-boot:run &
# ms-eventomax-bff:
cd ms-eventomax-bff && ./mvnw spring-boot:run &
```

### Paso 5: Levantar Frontend Angular
```bash
cd frontend-eventomax
npm install
npm start
# Disponible en http://localhost:4200
```

---

## 3. Guía de Pruebas y Comprobación para la Evaluación (EP5)

### Comprobación 1: Administración de Brokers y Tópicos en Kafka-UI
- Ingresar en el navegador a `http://localhost:8080` (puerto de `kafka-ui`).
- Verificar los **3 Brokers** activos (`kafka-1`, `kafka-2`, `kafka-3`).
- Verificar los tópicos aprovisionados (`productions.events`, `audit.timeline`, `productions.events.report.DLT`, `productions.events-audit-retry`, `productions.events-audit-dlt`) y la capacidad de crear/editar tópicos gracias a `DYNAMIC_CONFIG_ENABLED=true`.

### Comprobación 2: Creación, Modificación, Eliminación de Tópicos e Inspección de Consumer Groups en `kafka-admin`

1. **Crear un tópico nuevo (`POST /api/kafka-admin/topics`):**
   ```bash
   curl -X POST "http://localhost:8080/api/kafka-admin/topics" \
        -H "Authorization: Bearer <TOKEN_ADMIN>" \
        -H "Content-Type: application/json" \
        -d '{
          "name": "demo.evaluations.events",
          "partitions": 3,
          "replicationFactor": 3,
          "configs": {
            "retention.ms": "86400000",
            "cleanup.policy": "delete"
          }
        }'
   ```

2. **Modificar configuraciones de un tópico existente (`PUT /api/kafka-admin/topics/{name}/configs`):**
   ```bash
   curl -X PUT "http://localhost:8080/api/kafka-admin/topics/demo.evaluations.events/configs" \
        -H "Authorization: Bearer <TOKEN_ADMIN>" \
        -H "Content-Type: application/json" \
        -d '{
          "configs": {
            "retention.ms": "172800000",
            "cleanup.policy": "compact,delete"
          }
        }'
   ```

3. **Incrementar particiones de un tópico (`PUT /api/kafka-admin/topics/{name}/partitions`):**
   ```bash
   curl -X PUT "http://localhost:8080/api/kafka-admin/topics/demo.evaluations.events/partitions" \
        -H "Authorization: Bearer <TOKEN_ADMIN>" \
        -H "Content-Type: application/json" \
        -d '{
          "totalPartitions": 6
        }'
   ```

4. **Inspeccionar Consumer Groups (`GET /api/kafka-admin/consumer-groups`):**
   ```bash
   curl -X GET "http://localhost:8080/api/kafka-admin/consumer-groups" \
        -H "Authorization: Bearer <TOKEN_ADMIN>"
   ```

5. **Inspeccionar detalle de un Consumer Group (`GET /api/kafka-admin/consumer-groups/{groupId}`):**
   ```bash
   curl -X GET "http://localhost:8080/api/kafka-admin/consumer-groups/report-service" \
        -H "Authorization: Bearer <TOKEN_ADMIN>"
   ```

6. **Eliminar el tópico de prueba (`DELETE /api/kafka-admin/topics/{name}`):**
   ```bash
   curl -X DELETE "http://localhost:8080/api/kafka-admin/topics/demo.evaluations.events" \
        -H "Authorization: Bearer <TOKEN_ADMIN>"
   ```

### Comprobación 3: Flujo Completo Productor → Kafka → Consumidores (`report` y `audit`)
- Cambiar el estado de una producción a `EN_MONTAJE` asignando cuadrilla:
  ```bash
  curl -X PUT "http://localhost:8080/api/productions/1/status" \
       -H "Authorization: Bearer <TOKEN_PRODUCTOR>" \
       -H "Content-Type: application/json" \
       -d '{
         "status": "EN_MONTAJE",
         "crewId": "crew-001",
         "crewName": "Cuadrilla Técnica Alfa"
       }'
  ```
- Confirmar que:
  1. `ms-eventomax-productions` publica `ProductionStatusChanged` en `productions.events`.
  2. `ms-eventomax-report` consume el evento con el grupo `report-service` y actualiza los KPIs de montaje y eventos por día.
  3. `ms-eventomax-audit` consume el evento con el grupo `eventomax-audit-cg`, persiste la auditoría en PostgreSQL y publica la proyección en el tópico compactado `audit.timeline`.
