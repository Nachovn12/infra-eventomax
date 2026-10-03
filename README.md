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

## Integración Spring Boot

Los servicios ya se encuentran integrados con RabbitMQ:
- **Publisher:** `ms-eventomax-productions` publica comandos RabbitMQ.
- **Consumer:** `ms-eventomax-notify` consume comandos RabbitMQ (email, crew).

Características implementadas en la mensajería de Spring Boot:
- ACK/NACK explícito.
- Retries controlados.
- Propagación hacia DLQ automatizada y validada.
- Trazabilidad mediante `traceId` y `correlationId` en el envelope común.

*Nota:* La idempotencia actual en `ms-eventomax-notify` es en memoria; no es durable ni compartida entre múltiples instancias.

## RabbitMQ Cloud / AWS

**Estado:** Configuración *cloud-ready* preparada; despliegue en AWS pendiente de validación.

La arquitectura productiva de RabbitMQ está diseñada conceptualmente para desplegarse en una infraestructura EC2 dedicada (`ec2-mq`), separada de los microservicios (`ec2-apps`).

**Archivos de configuración:**
- `mq/compose.yml`: Desarrollo local, 1 nodo.
- `mq/compose.prod.yml`: Configuración cloud/evaluación, clúster de 2 nodos.

**Características del diseño Cloud:**
- **Cluster formation estático:** Nodos `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2`.
- El cluster productivo/evaluación utiliza el nombre explícito `eventomax-mq-prod` para evitar depender del nodo que inicialice primero el cluster.
- Ambos nodos comparten la misma Erlang cookie.
- Credenciales inyectadas de forma segura mediante variables de entorno (NO existen secretos en Git).
- *Nota académica:* Dos nodos cumplen el escenario de evaluación académico, pero no equivalen a una arquitectura de Alta Disponibilidad (HA) ideal de producción frente a fallos severos de particionamiento.

**Procedimiento de Bootstrap (Inicialización Cloud):**
El proceso de arranque productivo se realiza en este orden específico para evitar conflictos en la inicialización:

1. Crear el archivo de secretos productivo (fuera de Git) a partir de la plantilla `mq/.env.prod.example`. (No asumir que el archivo real está versionado).
2. Levantar los contenedores:
   ```powershell
   docker compose --env-file .\mq\.env.prod -f .\mq\compose.prod.yml up -d
   ```
3. Esperar a que ambos nodos alcancen el estado `healthy`.
4. Validar que el clúster se formó correctamente:
   ```powershell
   docker exec eventomax-rabbitmq-1 rabbitmqctl cluster_status
   ```
   *(Debe esperarse que aparezcan los nodos `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2`).*
5. Importar la topología **UNA VEZ** después de formar el clúster:
   ```powershell
   docker exec eventomax-rabbitmq-1 rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
   ```
6. Verificar posteriormente la creación de exchanges, queues y bindings.

**¿Por qué se importa la topología manualmente después del arranque?**
- Evita que la importación temprana a través del archivo de configuración suprima la creación por defecto del vhost `/` y el usuario productivo configurado mediante variables de entorno.
- El archivo `definitions.json` representa metadatos globales del clúster; por ende, no corresponde que cada nodo intente realizar una importación independiente de la misma topología durante el arranque concurrente.

**Seguridad y Redes:**
Mapeo de puertos definido en el clúster EC2:
- `rabbitmq-1`: Puerto `5672` para AMQP | Puerto `15672` para Management UI.
- `rabbitmq-2`: Puerto `5673` para AMQP del segundo nodo | Puerto `15673` para Management UI del segundo nodo.

Restricciones y consideraciones:
- El Security Group de `ec2-mq` debe permitir el tráfico AMQP **únicamente** desde la instancia `ec2-apps` (o desde un SG explícitamente autorizado).
- La interfaz Management UI debe estar expuesta solo a un origen administrativo autorizado.
- **NO** se deben abrir estos puertos indiscriminadamente a Internet.
- Los puertos internos de Erlang requeridos para el clúster se resuelven exclusivamente dentro de la red interna de Docker creada por Compose y **NO** necesitan publicarse al host físico en este escenario de nodos compartiendo la misma EC2.
- Desde los microservicios desplegados en cloud no se usará `localhost` ni `host.docker.internal`.
- RabbitMQ **no** transporta eventos de auditoría/reporting; su rol es exclusivo de comandos, mientras que Kafka sigue reservado para eventos.

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

**Estado:** Infraestructura base Kafka implementada y validada localmente.

### Componentes locales
- **ZooKeeper:** `eventomax-zookeeper` (puerto 2181)
- **Kafka:** `eventomax-kafka` (puertos 9092 para externo, 29092 interno)
- **Kafka UI:** `eventomax-kafka-ui` (puerto 8080)
- **Inicialización de tópicos:** Servicio idempotente `kafka-init` que asegura la creación automática de tópicos (`init-topics.sh`).

### Diferencia conceptual
Aclaración de arquitectura:
- **RabbitMQ** = Se usa para comandos / tareas.
- **Kafka** = Se usa para eventos.

### Tópicos implementados
- `productions.events`: Eventos de producción del core.
- `audit.timeline`: Trazabilidad y auditoría.

### Notas importantes sobre el entorno
- El entorno local utiliza `replication-factor: 1` al existir un solo broker. El diseño cloud oficial contempla `replication-factor: 3`.
- Los tópicos DLT (ej. `*.DLT`) se crearán por consumidor. Permanecen pendientes hasta la implementación de `ms-eventomax-audit` y `ms-eventomax-report`.
- Los clientes productores y consumidores en Spring Boot (Spring Kafka) se implementarán en una etapa posterior.

**Flujo previsto:**
`ms-eventomax-productions` → Kafka → `ms-eventomax-audit` y `ms-eventomax-report`

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
- [x] Publisher Spring Boot
- [x] ms-eventomax-notify
- [x] ACK/NACK desde Spring
- [x] Kafka
- [x] Kafka UI
- [x] productions.events
- [x] audit.timeline
- [x] Prueba producer/consumer Kafka
- [ ] DLT por consumidor
- [ ] Producer Spring Boot
- [ ] ms-eventomax-audit consumer
- [ ] ms-eventomax-report consumer
- [ ] Deploy cloud RabbitMQ/Kafka
