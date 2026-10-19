# RabbitMQ en EventoMax y cumplimiento EP2

## Fuentes y alcance

Revisión del 19 de octubre de 2026. Línea base: `infra-eventomax`, rama `develop`, commit `dd514b232844d0fa5562bd9ae20636f598ad6ac7`. Corrección implementada en rama `fix/local-rabbitmq-demo`, publicada en GitHub y formalizada en el [Pull Request #3](https://github.com/Nachovn12/infra-eventomax/pull/3) hacia `develop`, con CI remoto aprobado. El merge a `develop` se gestiona de forma controlada; sin deploy a AWS ni modificaciones de infraestructura productiva.

Fuentes entregadas por el equipo:

- `EP2_DSY1107_Estudiante_encargo.pdf`: siete páginas físicas; portada y encabezados dicen Evaluación Parcial N°3. Por aclaración expresa del usuario, su contenido se utiliza como rúbrica EP2. No se modificó el original.
- `Caso 8 - EventoMax.docx`: secciones 3, 5, 7 y 8, incluidas las tablas dentro del documento. El caso es semestral; no convierte automáticamente Kafka en requisito de esta EP2.

El PDF exige incorporar RabbitMQ, productores/consumidores, DLQ y logs, además del microservicio administrador. RabbitMQ es central para esta pauta: la clasificación previa como complementario no corresponde a este documento. También exige frontend funcional, compilación y pruebas, base cloud, JWT y login IDaaS. Esta corrección de infraestructura no certifica por sí sola esos otros requisitos.

El caso menciona Oracle y un clúster RabbitMQ de dos nodos. La línea base acordada del proyecto usa PostgreSQL/RDS: se registra la diferencia sin migrar bases. La prueba local de un nodo no demuestra cumplimiento del clúster de dos nodos ni un despliegue AWS. No se modifica infraestructura remota.

## Qué debe hacer RabbitMQ en el negocio

Productions coordina el evento y publica un comando. El exchange lo dirige por routing key a una cola. Un consumidor de dominio procesa la tarea y confirma ACK únicamente cuando corresponde. Ante errores debe decidir entre reintento acotado y NACK/rechazo sin requeue hacia DLQ, con trazabilidad del fallo. La consola permite observar este recorrido, pero no demuestra por sí sola que se haya enviado un correo o generado un documento.

| Cola principal | Trabajo pedido por el caso | Binding direct | Binding topic | DLQ |
|---|---|---|---|---|
| q.cmd.email | Email/push al organizador: confirmado, en montaje, cerrado | email.send | email.* | q.cmd.email.dlq |
| q.cmd.crew | Ticket de montaje / hoja de ruta para cuadrilla | crew.ticket | crew.# | q.cmd.crew.dlq |
| q.cmd.quote | PDF de cotización o acta de cierre | quote.gen | quote.* | q.cmd.quote.dlq |

Exchanges: `cmd.direct` (direct), `cmd.topic` (topic), `cmd.dead.dlx` (direct para DLQ). `definitions.json` coincide con estas seis colas y nueve bindings explícitos. Cada cola principal define DLX y routing key a su propia DLQ. No publicar el mismo comando por ambos exchanges salvo que se desee duplicar el trabajo.

El caso asigna a Notify email/webpush y tickets por RabbitMQ; no basta con tener colas vacías ni se debe atribuir un consumidor real de quote sin revisar su implementación. También pide envelope con type, eventId, timestamp, traceId, correlationId, idempotencia y métricas de tasa de DLQ. La prueba sintética de broker verifica transporte/ACK/NACK, no ese contrato de negocio ni sus efectos externos.

Un mensaje no enrutable no llega automáticamente a DLQ: comprobar en el productor mandatory/returns y confirmaciones según su implementación. Las DLQ demostradas aquí reciben mensajes previamente encolados y luego rechazados sin requeue. La rúbrica requiere además logs del consumidor.

## Corrección implementada

Antes, ResourcePolicy permitía demo.* pero el bootstrap solo concedía cmd.* y q.cmd.* al usuario de aplicación. Conectar rabbit-admin con ese usuario producía una incoherencia entre la API y el broker.

Ahora las tres identidades son distintas. `eventomax-app` conserva el conjunto de permisos operativos en un entorno nuevo; `eventomax-rabbit-admin` tiene permisos AMQP que coinciden con la expresión válida y el máximo de 128 caracteres de ResourcePolicy; `EventoMax` usa management con permisos de recursos vacíos para observar. No se amplía ResourcePolicy ni se permite administrar la topología del negocio desde la API.

`prepare-local.py` genera secretos privados y hashes con salt. Compose monta solo los hashes. El bootstrap los importa al primer arranque, evitando el usuario guest; después importa la topología. Un reinicio v2 no vuelve a importar ni cambiar credenciales. Volúmenes v1 o no marcados con datos se rechazan. No se realizó ninguna migración de volúmenes anteriores.

Ver [guía paso a paso y recuperación segura](LOCAL_BOOTSTRAP.md).

## Matriz de rúbrica y evidencias

Los porcentajes son ponderaciones del documento, NO puntajes obtenidos. Implementado no equivale a probado o desplegado.

| Indicador | Peso | Evidencia disponible en esta revisión | Estado y validación pendiente |
|---|---:|---|---|
| Nombres centralizados en application.yml o @Configuration | 12% | Topología central en mq/definitions.json | Parcial: JSON de infraestructura no sustituye la configuración Java exigida; revisar productores y consumidores reales |
| Beans Queue, Exchange y Binding por caso de uso | 13% | Topología broker importada y recorridos de tres colas probados | Pendiente verificar beans Spring de cada dominio |
| Separar configuración de mensajería y negocio | 10% | RabbitConfiguration separada de RabbitAdminService | Evidencia en rabbit-admin; falta auditar servicios de negocio |
| Consumidores @RabbitListener por dominio | 15% | No se revisaron ni ejecutaron consumidores de los otros repos en este cambio | Pendiente verificar listeners reales de email, crew y quote |
| ACK explícito y decisiones de error/reintento/NACK/DLQ | 20% | LocalIdentitiesTest prueba ACK, NACK sin requeue y x-death en tres DLQ con mensajes sintéticos | Broker probado; falta demostrar manejo en consumidores, reintentos, logs y efecto real |
| API REST administradora clara | 13% | RabbitAdminController: queues, exchanges, bindings; suite HTTP y servicio con broker restringido | Probado localmente; pendiente recorrido frontend/Entra/Gateway/BFF con JWT real |
| Administración encapsulada en servicio | 10% | RabbitAdminService, RabbitConfiguration, ResourcePolicy, BrokerFailureTranslator; pruebas unitarias y contra broker | Implementado y probado localmente; sin deploy |
| Validación de parámetros y errores | 7% | DTOs, @Valid, ResourcePolicy; suite HTTP/unitaria de validaciones | Probado en suite; pendiente demostración integrada |

## Resultado verificable de esta corrección

- Suite Maven local (modo estándar sin opt-in): 95 pruebas descubiertas, 88 ejecutadas y aprobadas, 0 fallos, 0 errores, 7 omitidas intencionalmente (`LocalIdentitiesTest` requiere opt-in explícito para evitar ejecución sobre brokers compartidos).
- Suite Maven con broker aislado temporal (`eventomax-test-isolated-ep2` en puerto AMQP 25695): 95 casos descubiertos, 95 ejecutados correctamente, 0 fallos, 0 errores, 0 omitidos. Verifica permisos restrictivos de `eventomax-rabbit-admin`, rechazo 403 al intentar alterar recursos operativos de `cmd.*` y `q.cmd.*`, y flujo de enrutamiento/ACK/DLQ con mensajes sintéticos.
- Prueba de persistencia y reinicio: se ejecutó en dos fases (`seed` y `verify`) con reinicio exclusivo del contenedor de prueba; el mensaje persistente sobrevivió en `demo.restart-proof`, se confirmó su consumo con ACK y se retiraron los recursos temporales.
- Diez comprobaciones HTTP en `verify-management.py` (puerto 15675): lectura de topología/estadísticas permitida para `EventoMax`, administración y creación denegadas con 401/403; cuentas AMQP sin acceso web.
- Broker de presentación académico existente (`eventomax-ep2-demo-20261019` en puertos 25692/15672): preservado intacto y saludable, con el usuario visual `EventoMax` (`tags: [management]`, permisos `^$`) y su contraseña preconfigurada sin alteraciones.
- Seguridad de credenciales: generador seguro con `secrets.token_urlsafe(32)` y hashes SHA-256 con salt; archivos privados protegidos por `icacls`/`0600` e ignorados en Git (`.gitignore`). `git diff --check` sin errores; escaneo de contraseñas y hashes en el diff sin coincidencias.
- Validación remota en GitHub Actions: workflow `Infra & Rabbit Admin CI` ejecutado con éxito (`pass` en 1m08s, Run 39199505080) sobre el PR #3 hacia `develop`.

## Qué falta para una presentación completa

Esta corrección certifica la seguridad, identidades, persistencia y API administrativa de RabbitMQ a nivel de infraestructura y broker aislado. Aún queda pendiente para EP2:
1. Revisar y validar conjuntamente en la prueba local integrada los consumidores reales en `ms-eventomax-notify` (email, crew) y el productor en `ms-eventomax-productions`: beans de configuración, listeners, ACK explícito, reintentos, logs de error, idempotencia y efectos de negocio.
2. Ejecutar el frontend Angular integrado con login real IDaaS, roles Entra ID y flujo completo a través del API Gateway y BFF.
3. El clúster de dos nodos y despliegue cloud corresponden a fases posteriores de infraestructura. No se asignan puntajes definitivos de la rúbrica con estas pruebas aisladas.

Referencias técnicas: [permisos Management](https://www.rabbitmq.com/docs/management), [control de acceso](https://www.rabbitmq.com/docs/access-control), [hash de contraseñas](https://www.rabbitmq.com/docs/passwords).
