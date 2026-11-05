# infra-eventomax — Infraestructura Cloud Native: RabbitMQ Cluster, Rabbit Admin (`AmqpAdmin`) y Kafka (EP2)

**Asignatura:** Desarrollo Cloud Native I (`DSY1107`)
**Caso de Estudio:** Caso 8 — Plataforma de Producción y Montaje de Eventos (**EventoMax**)
**Equipo:** Ignacio Valeria, Benjamín Flores, Benjamín Espinoza

---

## 1. Propósito del Repositorio y Rol en EventoMax

`infra-eventomax` centraliza la infraestructura de mensajería y administración programática de **EventoMax EP2**:

1. **`mq/` — Clúster RabbitMQ 4.3.6 (Local y Producción Cloud EC2):**
   - Topología declarativa (`definitions.json`) con **3 exchanges**, **6 colas durables** (3 operativas + 3 Dead Letter Queues) y **9 bindings**.
   - Manifiesto productivo de **clúster de 2 nodos** (`rabbitmq-1` y `rabbitmq-2`) en `mq/compose.prod.yml` unido a la red externa `eventomax-net`, con `RABBITMQ_ERLANG_COOKIE` compartida, límites de memoria (`mem_limit: 512m` por nodo) y descubrimiento clásico de pares (`rabbitmq.prod.conf`).
   - Manifiesto de laboratorio local (`mq/compose.yml` + `mq/prepare-local.py`) con identidades separadas de mínimo privilegio (`eventomax-app`, `eventomax-rabbit-admin`, `eventomax-demo-ui`).
2. **`rabbit-admin/` — Microservicio de Administración Programática (`AmqpAdmin`):**
   - Servicio Spring Boot 4.1.1 / Java 25 que cumple el requisito de la rúbrica EP2 de **crear, consultar y eliminar colas, exchanges y bindings programáticamente mediante `AmqpAdmin`**.
   - Protegido con JWT de Microsoft Entra ID (rol exclusivo `Admin`) y acotado por prefijo de seguridad `demo.` para impedir la alteración accidental de las colas productivas `q.cmd.*` y `cmd.*`.
   - Configurado con `mem_limit: 384m` y `HEALTHCHECK` activo en `rabbit-admin/docker-compose.prod.yml`.
3. **`kafka/` — Stack Apache Kafka:**
   - Manifiesto Docker Compose en `kafka/compose.yml` con inicialización automática de los tópicos `productions.events`, `productions.events.report.DLT` y `productions.events.audit.DLT`.
   - `kafka-ui` mapeado en `${KAFKA_UI_PORT:-18080}:8080` para evitar cualquier colisión en el puerto `8080` con `ms-eventomax-bff`.

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

## 3. Endpoints de `rabbit-admin` (`/api/rabbit-admin`)

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

## 4. Ejecución Local y Pruebas Automatizadas

```bash
# 1. Ejecutar pruebas unitarias y de seguridad en rabbit-admin (95 tests)
mvn -f rabbit-admin/pom.xml clean verify

# 2. Validar sintaxis de los manifiestos Compose
docker compose --env-file .env.prod.example -f mq/compose.prod.yml config -q
docker compose --env-file rabbit-admin/.env.prod.example -f rabbit-admin/docker-compose.prod.yml config -q
docker compose -f kafka/compose.yml config -q
```

**Resultado certificado en EP2:** `Tests run: 95, Failures: 0, Errors: 0, Skipped: 7 — BUILD SUCCESS`.
