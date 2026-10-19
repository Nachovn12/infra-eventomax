# EventoMax Rabbit Admin (`infra-eventomax/rabbit-admin`)

Microservicio Spring Boot 4.1.1 (Java 25) para la administración controlada de recursos RabbitMQ en el namespace aislado `demo.*`, implementado para cumplir los indicadores **6, 7 y 8** de la evaluación EP2.

---

## 1. Arquitectura y Seguridad

- **Separación de capas:**
  - `RabbitAdminController`: expone los endpoints REST bajo `/api/rabbit-admin` y aplica `@Valid`.
  - `RabbitAdminService`: encapsula `AmqpAdmin` y `RabbitOperations`, aplica `ResourcePolicy` y traduce errores AMQP mediante `BrokerFailureTranslator`.
  - `RabbitConfiguration`: configura `RabbitAdmin` con `autoStartup=false`, `ignoreDeclarationExceptions=false` y `redeclareManualDeclarations=false` (`spring.rabbitmq.dynamic=false`).
- **Autenticación y Autorización OAuth2 / JWT (Microsoft Entra ID):**
  - Valida obligatoriamente `iss` (`ENTRA_ISSUER_URI`), `aud` (`ENTRA_AUDIENCE`), `exp` y `nbf`.
  - Requiere simultáneamente el rol `ROLE_Admin` (claim `roles`) y la autoridad delegada `SCOPE_access_as_user` (claim `scp`) en `/api/rabbit-admin/**`.
  - Política *fail-closed*: solo `GET /actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` son públicos; cualquier otra ruta no autorizada es denegada.
- **Protección de Topología Operativa (`ResourcePolicy`):**
  - Solo se permite administrar o consultar recursos con prefijo `demo.` e identificador válido (`demo\.[A-Za-z0-9][A-Za-z0-9._-]*`, máx. 128 caracteres).
  - Nombres fuera del namespace `demo.*` (incluyendo colas operativas `q.cmd.email`, `q.cmd.crew`, `q.cmd.quote`, DLQs `*.dlq` y exchanges `cmd.direct`, `cmd.topic`, `cmd.dead.dlx`, `amq.*`) son rechazados con `403 Forbidden` antes de interactuar con el broker.
  - Nombres malformados dentro de `demo.` (`demo.`, `demo.-invalid`, `demo._invalid`) o con caracteres inválidos son rechazados con `400 Bad Request`.
  - **Sin purga ni consumo:** no existen endpoints para purgar colas (`purgeQueue`) ni leer/consumir mensajes (`basicGet` / `basicConsume`).
  - **Eliminación condicional atómica:**
    - `DELETE /queues/{name}` utiliza `admin.deleteQueue(name, true, true)` (`ifUnused=true`, `ifEmpty=true`), devolviendo `409 Conflict` si la cola contiene mensajes listos o tiene consumidores activos.
    - `DELETE /exchanges/{name}` utiliza `channel.exchangeDelete(name, true)` (`ifUnused=true`), devolviendo `409 Conflict` si el exchange tiene bindings activos.

---

## 2. Contratos DTO y Endpoints REST

Jackson está configurado con `spring.jackson.deserialization.fail-on-unknown-properties: true`. Cualquier propiedad JSON no listada en los DTOs (por ejemplo `exclusive`, `autoDelete` o `arguments` arbitrarios) produce `400 Bad Request` (`application/problem+json`).

### 2.1 Declarar Cola — `POST /api/rabbit-admin/queues`

**Campos soportados en `QueueRequest`:**
| Campo | Tipo | Obligatorio | Restricciones |
|---|---|---|---|
| `name` | `String` | Sí | `@ResourceName` (`[A-Za-z0-9][A-Za-z0-9._-]*`, máx. 128) y prefijo `demo.` |
| `durable` | `Boolean` | Sí | `@NotNull` (`true` o `false`) |
| `messageTtlMs` | `Integer` | No | `@Min(1) @Max(86400000)` |
| `maxLength` | `Integer` | No | `@Min(1) @Max(100000)` |
| `deadLetterExchange` | `String` | No | `@Size(max=128) @Pattern("[A-Za-z0-9][A-Za-z0-9._-]*")`, debe pertenecer a `demo.*` y existir en el broker |
| `deadLetterRoutingKey` | `String` | No | `@Size(max=128) @Pattern("[A-Za-z0-9][A-Za-z0-9._-]*")`, requiere `deadLetterExchange` |

*(El servidor fija internamente `exclusive=false`, `autoDelete=false` y `x-queue-type=classic`).*

**Ejemplo Request:**
```json
{
  "name": "demo.jobs",
  "durable": true,
  "messageTtlMs": 60000,
  "maxLength": 1000,
  "deadLetterExchange": "demo.dlx",
  "deadLetterRoutingKey": "demo.dead"
}
```

**Ejemplo Response (`200 OK` — `DeclarationResponse`):**
```json
{
  "name": "demo.jobs",
  "outcome": "DECLARED_AFTER_ABSENCE"
}
```
*(Si la cola ya existía con parámetros compatibles, retorna `"outcome": "EXISTING_COMPATIBLE"`; si existe con parámetros incompatibles, retorna `409 Conflict`).*

### 2.2 Consultar Cola (Inspección Pasiva) — `GET /api/rabbit-admin/queues/{name}`

**Ejemplo Response (`200 OK` — `QueueResponse`):**
```json
{
  "name": "demo.jobs",
  "readyMessages": 0,
  "consumers": 0
}
```

### 2.3 Eliminar Cola — `DELETE /api/rabbit-admin/queues/{name}`
- **Respuestas:** `204 No Content` (idempotente si no existe), `409 Conflict` si tiene mensajes o consumidores, `403 Forbidden` fuera de `demo.*`, `400 Bad Request` si el nombre es inválido.

### 2.4 Declarar Exchange — `POST /api/rabbit-admin/exchanges`

**Campos soportados en `ExchangeRequest`:**
| Campo | Tipo | Obligatorio | Restricciones |
|---|---|---|---|
| `name` | `String` | Sí | `@ResourceName` y prefijo `demo.` |
| `type` | `String` | Sí | `direct`, `topic` o `fanout` |
| `durable` | `Boolean` | Sí | `@NotNull` |

**Ejemplo Request:**
```json
{
  "name": "demo.events",
  "type": "topic",
  "durable": true
}
```

### 2.5 Eliminar Exchange — `DELETE /api/rabbit-admin/exchanges/{name}`
- **Respuestas:** `204 No Content` (idempotente si no existe), `409 Conflict` si el exchange está en uso.

### 2.6 Crear / Eliminar Binding — `POST /api/rabbit-admin/bindings` y `DELETE /api/rabbit-admin/bindings`

**Campos soportados en `BindingRequest`:**
```json
{
  "exchange": "demo.events",
  "queue": "demo.jobs",
  "routingKey": "jobs.*"
}
```
- **Respuestas:** `204 No Content`.

---

## 3. Variables de Entorno Requeridas

Para el broker local usar **eventomax-rabbit-admin**, nunca el usuario operativo ni EventoMax. Cargar la contraseña desde el archivo privado según [la guía local](../mq/LOCAL_BOOTSTRAP.md). ResourcePolicy y los permisos AMQP deben coincidir en demo.*.

Consultar `.env.example` (no versionar `.env` con secretos reales):
- `ENTRA_ISSUER_URI`
- `ENTRA_AUDIENCE`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT` (default `5672`)
- `RABBITMQ_VHOST`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `SERVER_PORT` (default `8080`)

---

## 4. Ejecución de Pruebas

### Pruebas unitarias y HTTP (sin conexión a broker real)
```bash
mvn -B test
```

### Pruebas de integración aisladas con Testcontainers (requiere Docker y opt-in explícito)
```bash
EVENTOMAX_RUN_ISOLATED_RABBIT_IT=true mvn -B -Pintegration verify
```

