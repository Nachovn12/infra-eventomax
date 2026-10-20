# infra-eventomax — Infraestructura Cloud Native: RabbitMQ Cluster, Rabbit Admin (`AmqpAdmin`) y Kafka KRaft (EP2)

**Asignatura:** Desarrollo Cloud Native I (`DSY1107`)
**Caso de Estudio:** Caso 8 — Plataforma de Producción y Montaje de Eventos (**EventoMax**)
**Equipo:** Ignacio Valeria, Benjamín Flores, Benjamín Espinoza

---

## 1. Propósito del Repositorio y Rol en EventoMax

`infra-eventomax` centraliza la infraestructura de mensajería y administración programática de **EventoMax EP2**:

1. **`mq/` — Clúster RabbitMQ 4.3.6 (Local y Producción Cloud EC2):**
   - Topología declarativa (`definitions.json`) con **3 exchanges**, **6 colas durables** (3 operativas + 3 Dead Letter Queues) y **9 bindings**.
   - Manifiesto productivo de **clúster de 2 nodos** (`rabbitmq-1` y `rabbitmq-2`) en `mq/compose.prod.yml` unido a la red externa `eventomax-net`, con `RABBITMQ_ERLANG_COOKIE` compartida y descubrimiento clásico de pares (`rabbitmq.prod.conf`).
   - Manifiesto de laboratorio local (`mq/compose.yml` + `mq/prepare-local.py`) con identidades separadas de mínimo privilegio (`eventomax-app`, `eventomax-rabbit-admin`, `eventomax-demo-ui`).
2. **`rabbit-admin/` — Microservicio de Administración Programática (`AmqpAdmin`):**
   - Servicio Spring Boot 4.1.1 / Java 25 que cumple el requisito de la rúbrica EP2 de **crear, consultar y eliminar colas, exchanges y bindings programáticamente mediante `AmqpAdmin`**.
   - Protegido con JWT de Microsoft Entra ID (rol exclusivo `Admin`) y acotado por prefijo de seguridad `demo.` para impedir la alteración accidental de las colas productivas `q.cmd.*` y `cmd.*`.
3. **`kafka/` — Stack Apache Kafka (KRaft):**
   - Manifiesto Docker Compose sin ZooKeeper con inicialización automática de los tópicos `productions.events`, `productions.events.report.DLT` y `productions.events.audit.DLT`.

---

## 2. Arquitectura del Módulo y Diagrama

```mermaid
flowchart TB
    subgraph Red Compartida Docker eventomax-net
        subgraph Clúster RabbitMQ (mq/compose.prod.yml)
            RMQ1["rabbitmq-1\n(rabbit@rabbitmq-1)"] <-->|Erlang Cookie +\nPeer Discovery| RMQ2["rabbitmq-2\n(rabbit@rabbitmq-2)"]
        end

        subgraph Topología Declarativa (definitions.json)
            EX_DIRECT["cmd.direct (direct)"]
            EX_TOPIC["cmd.topic (topic)"]
            EX_DLX["cmd.dead.dlx (direct)"]
            Q_WORK["q.cmd.email\nq.cmd.crew\nq.cmd.quote"]
            Q_DLQ["q.cmd.email.dlq\nq.cmd.crew.dlq\nq.cmd.quote.dlq"]
            EX_DIRECT --> Q_WORK
            EX_TOPIC --> Q_WORK
            Q_WORK -.->|x-dead-letter-exchange| EX_DLX --> Q_DLQ
        end

        RADMIN["rabbit-admin (:8080 / :8086)\nSpring AMQP AmqpAdmin\nPrefijo seguro: demo.*"] -->|AMQP :5672| RMQ1
        PROD["ms-eventomax-productions"] -->|SPRING_RABBITMQ_ADDRESSES\nrabbitmq-1:5672,rabbitmq-2:5672| RMQ1
        NOTIFY["ms-eventomax-notify"] -->|SPRING_RABBITMQ_ADDRESSES\nrabbitmq-1:5672,rabbitmq-2:5672| RMQ2
    end
```

---

## 3. Stack Tecnológico y Versiones Reales

| Componente | Tecnología / Imagen | Versión |
|---|---|---|
| Broker AMQP | `rabbitmq:4.3.6-management` | `4.3.6` |
| Broker Eventos | `apache/kafka` (KRaft mode) | `3.9.0` |
| Microservicio `rabbit-admin` | Java 25 + Spring Boot + `spring-boot-starter-amqp` + `oauth2-resource-server` | `4.1.1` |
| Pruebas `rabbit-admin` | JUnit 5, Testcontainers (`rabbitmq:4.3.6-management`), MockMvc | `95` tests unitarios + `5` ITs |

---

## 4. Cumplimiento de Rúbrica EP2 (Matriz Caso 8)

| Criterio de Evaluación EP2 | Implementación en `infra-eventomax` | Estado |
|---|---|---|
| **Clúster RabbitMQ en Contenedores Docker** | `mq/compose.prod.yml` define 2 nodos (`rabbitmq-1`, `rabbitmq-2`) con volúmenes persistentes, healthchecks diagnósticos y `rabbitmq.prod.conf` (`cluster_formation.classic_config.nodes`). | **CUMPLIDO** |
| **Exchanges, Colas, Bindings y DLQ** | `mq/definitions.json` declara `cmd.direct`, `cmd.topic`, `cmd.dead.dlx`, `q.cmd.email[.dlq]`, `q.cmd.crew[.dlq]` y `q.cmd.quote[.dlq]`. | **CUMPLIDO** |
| **Administración de Objetos con `AmqpAdmin`** | `rabbit-admin` expone endpoints REST autenticados (`POST/GET/DELETE /api/rabbit-admin/queues`, `/exchanges`, `/bindings`) implementados sobre `org.springframework.amqp.core.AmqpAdmin`. | **CUMPLIDO** |
| **Seguridad y Mínimo Privilegio** | Separación de usuarios de broker, puertos AMQP internos a `eventomax-net`, Management UI ligado a `127.0.0.1` (acceso vía túnel SSH) y cero credenciales en Git. | **CUMPLIDO** |

---

## 5. Endpoints de `rabbit-admin` (`/api/rabbit-admin`)

Todos los endpoints exigen JWT válido de Microsoft Entra ID con `SCOPE_access_as_user` y rol `Admin`, y validan que los recursos operados comiencen con el prefijo `demo.`:

| Método | Ruta | Acción en `AmqpAdmin` | Código Éxito |
|---|---|---|---|
| `POST` | `/api/rabbit-admin/queues` | `amqpAdmin.declareQueue(QueueBuilder.durable(name)...)` | `201 Created` |
| `GET` | `/api/rabbit-admin/queues/{name}` | `amqpAdmin.getQueueInfo(name)` (mensajes y consumidores) | `200 OK` |
| `DELETE` | `/api/rabbit-admin/queues/{name}?unusedOnly=false&emptyOnly=false` | `amqpAdmin.deleteQueue(name, unused, empty)` | `204 No Content` |
| `POST` | `/api/rabbit-admin/exchanges` | `amqpAdmin.declareExchange(...)` (`direct`, `topic`, `fanout`, `headers`) | `201 Created` |
| `DELETE` | `/api/rabbit-admin/exchanges/{name}` | `amqpAdmin.deleteExchange(name)` | `204 No Content` |
| `POST` | `/api/rabbit-admin/bindings` | `amqpAdmin.declareBinding(...)` | `201 Created` |
| `DELETE` | `/api/rabbit-admin/bindings` | `amqpAdmin.removeBinding(...)` | `204 No Content` |

---

## 6. Variables de Entorno Requeridas (`.env.prod.example`)

| Variable | Ejemplo Placeholder | Descripción |
|---|---|---|
| `RABBITMQ_ERLANG_COOKIE` | `<COOKIE_ALEATORIA_ALFANUMERICA>` | Cookie Erlang compartida entre `rabbitmq-1` y `rabbitmq-2` |
| `RABBITMQ_DEFAULT_USER` | `eventomax-admin` | Usuario inicial de administración del broker |
| `RABBITMQ_DEFAULT_PASS` | `<SECRET_ADMIN_PASSWORD>` | Contraseña inicial del broker |
| `SPRING_RABBITMQ_ADDRESSES` | `rabbitmq-1:5672,rabbitmq-2:5672` | Lista de nodos AMQP para microservicios en `eventomax-net` |
| `RABBITMQ_USERNAME` | `eventomax-rabbit-admin` | Usuario restringido a `^demo\..*` para `rabbit-admin` |
| `RABBITMQ_PASSWORD` | `<SECRET_RABBIT_ADMIN_PASSWORD>` | Contraseña del usuario de `rabbit-admin` |
| `ENTRA_ISSUER_URI` | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` | Issuer URI de Microsoft Entra ID |
| `ENTRA_AUDIENCE` | `api://a1a87bcd-fed7-4d65-83c3-338389b80093` | Audience del API en Entra ID |

---

## 7. Ejecución Local y Pruebas Automatizadas

```bash
# 1. Ejecutar pruebas unitarias y de seguridad en rabbit-admin (95 tests)
mvn -f rabbit-admin/pom.xml clean test

# 2. Validar sintaxis del manifiesto productivo de clúster RabbitMQ
docker compose --env-file .env.prod.example -f mq/compose.prod.yml config -q
```

**Resultado certificado en EP2:** `Tests run: 95, Failures: 0, Errors: 0, Skipped: 7 — BUILD SUCCESS` (más `5/5` pruebas de integración Failsafe con Testcontainers).

---

## 8. Estructura del Repositorio

```text
infra-eventomax/
├── kafka/
│   └── compose.yml               # Broker Apache Kafka KRaft + init de tópicos
├── mq/
│   ├── compose.yml               # Broker RabbitMQ local para desarrollo/demo
│   ├── compose.prod.yml          # Clúster RabbitMQ de 2 nodos para AWS EC2
│   ├── definitions.json          # Topología declarativa (3 exchanges, 6 colas, 9 bindings)
│   ├── rabbitmq.local.conf       # Configuración local
│   ├── rabbitmq.prod.conf        # Configuración de clúster productivo
│   ├── prepare-local.py          # Provisionamiento local de usuarios de mínimo privilegio
│   └── LOCAL_BOOTSTRAP.md        # Guía operativa local
├── rabbit-admin/
│   ├── src/main/java/...         # Microservicio Spring Boot AmqpAdmin
│   ├── src/test/java/...         # Suite de 95 pruebas unitarias + ITs
│   ├── Dockerfile                # Multi-stage build no-root con healthcheck
│   ├── docker-compose.prod.yml   # Despliegue en eventomax-net
│   └── pom.xml
├── .env.example
└── .env.prod.example
```

---

## 9. Estado Actual de Certificación EP2 y Trazabilidad Jira

- **Auditoría de Infraestructura y Seguridad (Fase B5/B6A):** Certificada sin hallazgos críticos ni secretos expuestos.
- **Historias Jira:** `EMX-ep2-rabbit-admin` (Módulo `AmqpAdmin`), `feature/infra-cloud-rabbitmq-readiness` (Clúster RabbitMQ de 2 nodos y topología DLQ).
