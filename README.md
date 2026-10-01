# EventoMax Infrastructure

Este repositorio contiene la infraestructura de servicios auxiliares para la plataforma EventoMax, incluyendo la configuración y despliegue local mediante Docker de los brokers de mensajería (RabbitMQ y Kafka).

## Arquitectura de mensajería

En EventoMax, la mensajería se divide conceptualmente en dos grandes áreas:

- **RabbitMQ** = Se utiliza para la gestión de **comandos** y **trabajos asíncronos** ("hacer algo").
- **Kafka** = Se utilizará para **eventos**, **streaming**, **auditoría** y **reportería** ("algo ocurrió").

```text
+-----------------------+       +-------------------+       +-----------------------+
| ms-eventomax-notify   | <---- | RabbitMQ          | <---- | ms-eventomax-         |
| (Comandos/Tareas)     |       | (Broker Comandos) |       | productions (Core)    |
+-----------------------+       +-------------------+       +-----------------------+
                                                               |
                                                               |
+-----------------------+       +-------------------+          |
| ms-eventomax-audit /  | <---- | Kafka             | <--------+
| ms-eventomax-report   |       | (Broker Eventos)  |
+-----------------------+       +-------------------+
```

## Estructura del repositorio

```text
infra-eventomax/
├── mq/
│   ├── compose.yml
│   ├── rabbitmq.conf
│   └── definitions.json
├── kafka/
│   └── compose.yml
├── .env.example
├── .gitignore
└── README.md
```
*Nota: El archivo `kafka/compose.yml` actualmente está pendiente de implementación si está vacío.*

## RabbitMQ

### Estado
Implementado y validado localmente.

### Versión
RabbitMQ 4.3.6 Management.

### Puertos
- 5672: AMQP
- 15672: Management UI

### Variables de entorno
La configuración de credenciales utiliza las siguientes variables de entorno:
- `RABBITMQ_DEFAULT_USER`
- `RABBITMQ_DEFAULT_PASS`

*Nota: Las variables se configuran en el archivo `.env` local, el cual no está versionado. Existe un archivo `.env.example` que sirve como plantilla sin secretos.*

## Levantar RabbitMQ

Para iniciar RabbitMQ en segundo plano:

```powershell
docker compose --env-file .env -f .\mq\compose.yml up -d
```

## Verificar estado

```powershell
docker compose --env-file .env -f .\mq\compose.yml ps
```

Estado esperado: `eventomax-rabbitmq` con status `healthy`.

## Detener RabbitMQ

```powershell
docker compose --env-file .env -f .\mq\compose.yml down
```

## Management UI

La interfaz de administración se encuentra disponible en:
http://localhost:15672

- Usuario configurado mediante la variable: `RABBITMQ_DEFAULT_USER`
- Contraseña configurada mediante la variable: `RABBITMQ_DEFAULT_PASS`

*(Las credenciales reales deben obtenerse del archivo `.env` local).*

## Exchanges

| Exchange | Tipo | Propósito |
|---|---|---|
| `cmd.direct` | direct | comandos con routing exacto |
| `cmd.topic` | topic | comandos por patrón |
| `cmd.dead.dlx` | direct | mensajes rechazados hacia DLQ |

## Colas y DLQ

| Cola | Propósito | DLQ |
|---|---|---|
| `q.cmd.email` | email/push | `q.cmd.email.dlq` |
| `q.cmd.crew` | ticket de cuadrilla | `q.cmd.crew.dlq` |
| `q.cmd.quote` | cotización/acta | `q.cmd.quote.dlq` |

## Routing

**Bindings en `cmd.direct` (routing exacto):**
- `email.send` → `q.cmd.email`
- `crew.ticket` → `q.cmd.crew`
- `quote.gen` → `q.cmd.quote`

**Bindings en `cmd.topic` (por patrón):**
- `email.*` → `q.cmd.email`
- `crew.#` → `q.cmd.crew`
- `quote.*` → `q.cmd.quote`

## Flujo Dead Letter

Ejemplo del flujo de rechazo para correos:

```text
q.cmd.email
     |
     | reject / NACK
     | requeue=false
     v
cmd.dead.dlx
     |
     v
q.cmd.email.dlq
```

*Nota: Las colas `crew` y `quote` siguen exactamente el mismo patrón de Dead Letter.*

## Validación realizada

Se ha verificado exitosamente el siguiente escenario en el entorno local:
- contenedor `healthy`
- 3 exchanges EventoMax
- 6 colas (principales y DLQs)
- bindings correctos
- publicación con routing key `email.send`
- confirmación de `routed=True`
- la cola `q.cmd.email` recibió mensaje
- rechazo sin requeue (`requeue=false`)
- mensaje terminó en `q.cmd.email.dlq`

## Comandos útiles

Listar exchanges:
```powershell
docker exec eventomax-rabbitmq rabbitmqctl list_exchanges name type durable
```

Listar colas:
```powershell
docker exec eventomax-rabbitmq rabbitmqctl list_queues name durable arguments
```

Listar bindings:
```powershell
docker exec eventomax-rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key
```

Ver logs:
```powershell
docker logs eventomax-rabbitmq --tail 100
```

## Integración Spring Boot futura

**Importante:** RabbitMQ todavía no está conectado a un microservicio Spring Boot.

Siguiente integración planificada:
- `ms-eventomax-productions` → publisher RabbitMQ
- `ms-eventomax-notify` → consumer RabbitMQ

`ms-eventomax-notify` será:
- sin base de datos
- no público
- consumidor RabbitMQ

Posteriormente se debe implementar:
- ACK/NACK explícito
- retries controlados
- idempotencia
- DLQ
- traceId
- correlationId

## Envelope común

Contrato conceptual previsto para los mensajes:

```json
{
  "type": "ProductionConfirmed",
  "eventId": "...",
  "timestamp": "...",
  "traceId": "...",
  "correlationId": "...",
  "payload": {}
}
```
*(El contenido de `payload` dependerá del comando/evento correspondiente).*

## Kafka

**Estado:** Pendiente de implementación.

**Tópicos previstos:**
- `productions.events`
- `audit.timeline`
- `*.DLT`

**Flujo previsto:**
`ms-eventomax-productions` → Kafka → `ms-eventomax-audit` y `ms-eventomax-report`

*(Kafka todavía no está operativo en este repositorio).*

## Cloud

El entorno actual documentado es local para desarrollo y pruebas.

El diseño cloud para los ambientes superiores contempla:
- RabbitMQ en infraestructura AWS dedicada.
- Kafka + Zookeeper en infraestructura AWS dedicada.
- Backend/microservicios en instancias EC2 + Docker.
- PostgreSQL en RDS.

## Seguridad

Políticas de seguridad del repositorio:
- NO subir el archivo `.env`.
- NO subir contraseñas ni secretos en texto plano.
- NO subir tokens de acceso.
- NO subir AWS keys.
- NO subir credenciales PostgreSQL.
- Usar siempre variables de entorno.
- Usar AWS Secrets Manager / Parameter Store cuando corresponda en los despliegues cloud.
- NO exponer RabbitMQ ni Kafka públicamente en cloud.
- Abrir solamente los puertos necesarios mediante Security Groups.

## Estado actual

- [x] RabbitMQ Docker
- [x] RabbitMQ Management UI
- [x] Exchanges
- [x] Queues
- [x] DLQ
- [x] Routing
- [x] Prueba de publicación
- [x] Prueba de Dead Letter
- [ ] Publisher Spring Boot
- [ ] ms-eventomax-notify
- [ ] ACK/NACK desde Spring
- [ ] Kafka
- [ ] Kafka UI
- [ ] ms-eventomax-audit
- [ ] ms-eventomax-report
- [ ] Deploy cloud RabbitMQ/Kafka
