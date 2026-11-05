# Manual de Entrega y Guía de Operación — EP3 Kafka EventoMax (delivery.md)

**Versión:** 1.0.0  
**Fecha:** 2026-11-05  
**Proyecto:** EventoMax — Cloud Native I (EP3 Apache Kafka)  

---

## 1. Arquitectura Desplegada

El sistema opera distribuido en 3 instancias virtuales EC2 (o entornos de contenedores locales) comunicados mediante VPC y Docker Bridge:

```
+-----------------------------------------------------------------------------------+
| ec2-kafka: Apache Kafka & ZooKeeper Cluster                                      |
|  - zookeeper-1, zookeeper-2, zookeeper-3 (Puerto 2181, 2182, 2183)                |
|  - kafka-1, kafka-2, kafka-3 (Listeners: 9092-9096, RF=3, ISR=2)                  |
|  - kafka-ui (Puerto 8080)                                                         |
|  - kafka-admin (Spring Boot Microservice en puerto 8087)                          |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ Eventos de Dominio (productions.events)
                                         │ Timeline Compactado (audit.timeline)
                                         │ DLTs (productions.events.report.DLT, etc.)
                                         ▼
+-----------------------------------------------------------------------------------+
| ec2-apps: Microservicios de Dominio & Persistencia                                |
|  - PostgreSQL (Bases de datos independientes: productions, catalog, report, audit)|
|  - ms-eventomax-productions (8081) - Transición de estado y asignación de cuadrilla|
|  - ms-eventomax-catalog (8082)     - Inventario de equipamiento y servicios        |
|  - ms-eventomax-report (8084)      - Analítica streaming de montaje y eventos/día |
|  - ms-eventomax-audit (8085)       - Registro forense y línea de tiempo           |
|  - ms-eventomax-bff (8080 / 8090)  - Enrutamiento seguro Entra ID (JWT + RBAC)     |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ Comandos Asíncronos (q.cmd.crew, q.cmd.email)
                                         ▼
+-----------------------------------------------------------------------------------+
| ec2-mq: Broker RabbitMQ                                                           |
|  - rabbitmq (Puertos 5672 AMQP, 15672 Management UI)                              |
|  - ms-eventomax-notify (8083) - Emisión de tickets PDF y hojas de ruta de cuadrilla|
+-----------------------------------------------------------------------------------+
```

---

## 2. Protocolo de Arranque en Frío (Cold Start)

### Paso 1: Levantar Clúster Kafka y Aprovisionar Tópicos (`ec2-kafka`)
```bash
cd infra-eventomax/kafka

# 1. Iniciar los 3 ZooKeepers y los 3 Brokers Kafka con Kafka UI
docker compose up -d

# 2. Verificar que los 3 brokers estén listos
docker compose ps

# 3. Inicializar tópicos con Replicación=3 y particiones=3
chmod +x init-topics.sh
./init-topics.sh

# 4. Iniciar el microservicio kafka-admin
cd ../kafka-admin
mvn clean package -DskipTests
docker compose up -d
```

### Paso 2: Levantar Infraestructura RabbitMQ (`ec2-mq`)
```bash
cd infra-eventomax/rabbitmq
docker compose up -d
```

### Paso 3: Levantar Microservicios de Dominio (`ec2-apps`)
```bash
# 1. Asegurar base de datos PostgreSQL activa
cd infra-eventomax/postgres
docker compose up -d

# 2. Iniciar microservicios (o ejecutar cada contenedor)
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

### Paso 4: Levantar Frontend Angular
```bash
cd frontend-eventomax
npm install
npm start
# La aplicación queda disponible en http://localhost:4200
```

---

## 3. Pruebas y Comprobación en Vivo

### Comprobación 1: Auditoría de Tópicos Kafka
- **Kafka UI:** Ingresar en navegador a `http://<ec2-kafka-ip>:8080`.
  - Confirmar 3 Brokers online (`kafka-1`, `kafka-2`, `kafka-3`).
  - Confirmar tópicos:
    - `productions.events` (Particiones: 3, Replication Factor: 3)
    - `audit.timeline` (Particiones: 3, RF: 3, Cleanup Policy: `compact,delete`)
    - `productions.events.report.DLT` (Particiones: 3, RF: 3)
    - `productions.events-audit-retry` y `productions.events-audit-dlt` (Particiones: 3, RF: 3)

### Comprobación 2: Microservicio `kafka-admin` vía BFF
- **Petición con token JWT de Administrador:**
  ```bash
  curl -X GET "http://localhost:8080/api/kafka-admin/topics" \
       -H "Authorization: Bearer <TOKEN_ADMIN>"
  ```
- **Respuesta esperada (HTTP 200):**
  Listado con el resumen de tópicos, particiones, réplicas y políticas de retención.

### Comprobación 3: Asignación de Cuadrilla Técnica y Transición de Montaje
- **Actualizar estado de una producción asignando cuadrilla:**
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
- **Resultado:**
  1. Base de datos `productions` persiste `crew_id` y `crew_name`.
  2. Se emite evento `ProductionStatusChanged` hacia el tópico Kafka `productions.events`.
  3. Se emite comando hacia RabbitMQ `q.cmd.crew`.
  4. `ms-eventomax-notify` genera el ticket con la hoja de ruta y la cuadrilla asignada.

### Comprobación 4: Consumo en Report y Frontend
- En Frontend Angular ingresar a `/reports` con rol `Admin`.
- Observar el indicador **Tiempo Promedio de Montaje** (calculado entre `EN_MONTAJE` y `EN_EJECUCION`).
- Observar la tabla de **Eventos por Día** con la agregación de producciones.
- El panel se refresca automáticamente cada 30 segundos sin bloquear la UI ni requerir F5.

