# RabbitMQ local para la demostración EP2

Preparar tres identidades separadas en un broker NUEVO. La consola observa; las operaciones de administración de la rúbrica se demuestran mediante rabbit-admin. Ver [alcance y matriz EP2](EP2_RABBITMQ.md).

## Identidades y permisos

| Uso | Usuario | Etiqueta | Configure / write / read en `/` |
|---|---|---|---|
| Aplicación | eventomax-app | Ninguna | Recursos operativos cmd.* y q.cmd.* exactos de definitions.json |
| Microservicio rabbit-admin | eventomax-rabbit-admin | Ninguna | Nombres válidos demo.*, máximo 128 caracteres |
| Observación web | EventoMax | management | `^$` en los tres permisos: ninguna operación sobre recursos |

La etiqueta management permite ver exchanges, colas, bindings y estadísticas del vhost autorizado. No permite gestionar usuarios, permisos o políticas. No ofrece visibilidad de todas las conexiones ajenas como monitoring. No se concede administrator, policymaker ni monitoring. [Referencia oficial](https://www.rabbitmq.com/docs/management).

La cuenta AMQP de rabbit-admin puede operar dentro de demo.*, incluido publicar/consumir; su API REST no expone publicación, consumo o purga. No puede usar el exchange por defecto (amq.default queda fuera de su permiso). El observador puede autenticar AMQP en `/`, pero no operar recursos.

## Preparar credenciales una sola vez

Desde la raíz del repositorio, con Python 3:

```powershell
python mq/prepare-local.py
git check-ignore mq/.local-secrets/credentials.json mq/.local-secrets/identities.json
```

Se generan contraseñas aleatorias independientes de 256 bits. credentials.json las conserva en el equipo; identities.json contiene hashes SHA-256 con salt para RabbitMQ. Ambos archivos son privados e ignorados por Git. El generador restringe la carpeta al propietario y SYSTEM en Windows, no imprime contraseñas y rechaza un directorio preexistente para evitar rotación accidental. No volver a generar durante una presentación.

El broker académico de presentación existente (`eventomax-ep2-demo-20261019`) ya cuenta con el usuario `EventoMax` y conserva su contraseña previamente configurada; no se debe modificar ni regenerar. Nuevos brokers o pruebas generan contraseñas independientes.

Los hashes también son sensibles. No adjuntar estos archivos a entregas, capturas, logs o chats. Guardar una copia en un gestor de contraseñas es válido. La carpeta no es cifrado en reposo: proteger la sesión del equipo.

## Levantar un entorno separado

Primero verificar nombres con `docker ps -a --format '{{.Names}}'` y `docker volume ls --format '{{.Name}}'`, y disponibilidad de puertos. Los nombres siguientes deben ser nuevos, salvo que se retome exactamente la demo creada por este procedimiento. Nunca usar un volumen ajeno.

```powershell
$env:RABBITMQ_CONTAINER_NAME = 'eventomax-ep2-demo-20261019'
$env:RABBITMQ_AMQP_PORT = '25692'
$env:RABBITMQ_MANAGEMENT_PORT = '15672'
docker compose -p eventomax-ep2-demo-20261019 -f mq/compose.yml config --quiet
docker compose -p eventomax-ep2-demo-20261019 -f mq/compose.yml up -d
docker compose -p eventomax-ep2-demo-20261019 -f mq/compose.yml ps
```

Esperar healthy. El volumen es eventomax-ep2-demo-20261019_rabbitmq-data. Los puertos se vinculan únicamente a 127.0.0.1. AMQP usa 25692 para no interferir con instalaciones anteriores; Management usa 15672.

## Acceso del docente y recuperación segura

1. Abrir http://localhost:15672.
2. Usuario: **EventoMax**.
3. Antes de compartir pantalla, copiar la contraseña desde el gestor o el archivo privado. En PowerShell, sin imprimirla:

```powershell
$demoCredentials = Get-Content -Raw mq/.local-secrets/credentials.json | ConvertFrom-Json
Set-Clipboard -Value $demoCredentials.management.password
Remove-Variable demoCredentials
```

4. Pegar en el campo de contraseña e ingresar. Limpiar después con `Set-Clipboard -Value ''`. Evitar historial/sincronización del portapapeles para secretos; usar el gestor si están activos.
5. Seleccionar vhost `/`, abrir Exchanges, Queues and Streams y los bindings de una cola. La gestión de usuarios no debe estar disponible.

Si se pierde el archivo, recuperar la copia del gestor. El broker almacena un hash: no permite recuperar la contraseña original. Cambiarla requiere una operación explícita y revisada sobre esa cuenta; recrear archivos o reiniciar no la rota. No borrar volúmenes ni resetear RabbitMQ.

## Conectar rabbit-admin

Cargar la identidad rabbit_admin en su propio proceso, nunca application ni management:

```powershell
$localCredentials = Get-Content -Raw mq/.local-secrets/credentials.json | ConvertFrom-Json
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '25692'
$env:RABBITMQ_VHOST = '/'
$env:RABBITMQ_USERNAME = $localCredentials.rabbit_admin.username
$env:RABBITMQ_PASSWORD = $localCredentials.rabbit_admin.password
Remove-Variable localCredentials
# Configurar privadamente ENTRA_ISSUER_URI y ENTRA_AUDIENCE.
# Elegir SERVER_PORT libre si el BFF utiliza 8080.
mvn -f rabbit-admin/pom.xml spring-boot:run
# Al terminar:
Remove-Item Env:RABBITMQ_PASSWORD
```

No volcar el entorno ni activar trazas de secretos. Los servicios operativos deben cargar application en sus propios procesos, usando las variables admitidas por cada repo. Esta guía no modifica ni conecta servicios existentes. Dentro de otro contenedor, 127.0.0.1 apunta a ese contenedor: se necesita una red/host acordado; el ejemplo anterior ejecuta Java en Windows.

El usuario Microsoft con rol Admin es distinto de las identidades RabbitMQ. El recorrido protegido sigue siendo Angular → Entra ID → API Gateway → BFF → microservicio. Las pruebas aisladas no certifican el recorrido cloud.

## Comprobar permisos

```powershell
docker exec eventomax-ep2-demo-20261019 rabbitmqctl list_users
docker exec eventomax-ep2-demo-20261019 rabbitmqctl list_permissions -p /
python mq/verify-management.py
```

Los comandos no imprimen contraseñas. La prueba HTTP verifica lectura para el observador, administración y creación denegadas, y acceso web denegado para las cuentas AMQP. Se dirige exclusivamente a 127.0.0.1:15672.

## Guion ante el docente

1. Explicar que confirmar un evento genera tareas asíncronas; el consumidor realiza el trabajo sin bloquear la petición. Mostrar el resultado real del caso probado: email/push, ticket o PDF.
2. Mostrar cmd.direct, cmd.topic, cmd.dead.dlx y seis colas. El binding email.send conduce a q.cmd.email: routing key y nombre de cola son distintos.
3. Ejecutar un caso válido desde el frontend integrado: observar publicación, entrega y ACK. Cola vacía puede significar procesamiento rápido; acompañar con métricas, trazabilidad y resultado de negocio.
4. En entorno aislado, provocar un error controlado: reintentos acotados cuando proceda, NACK sin requeue y llegada a la DLQ. Correlacionar con logs del consumidor. Un mensaje sin ruta no llega automáticamente a DLQ: requiere gestión de retornos/confirmaciones por el productor.
5. Con JWT y rol autorizados, usar rabbit-admin para crear demo.ep2.exchange (direct), demo.ep2.queue (durable) y un binding con routing key demo.ep2. Verlos desde Management.
6. Mostrar 400 por nombre vacío/configuración inválida, 403 fuera de demo.*, 401 sin JWT y 403 por rol insuficiente. Los tests HTTP usan identidades simuladas; la presentación debe usar login real.
7. Eliminar primero binding, luego cola vacía y sin consumidores y finalmente exchange. No borrar recursos operativos. La API devuelve 409 ante borrados incompatibles con sus restricciones.

Contratos y JSON en [rabbit-admin/README.md](../rabbit-admin/README.md). La consola observadora no hace mutaciones: las realiza la API con su identidad AMQP dedicada. Los pasos con frontend/consumidores son guion de aceptación pendiente, no resultados de las pruebas de infraestructura.

## Pruebas aisladas

Las pruebas de integración opt-in se ejecutan exclusivamente contra un broker NUEVO y aislado, nunca contra el broker académico de presentación:

```powershell
# Levantar contenedor de prueba en puertos independientes (ej. AMQP 25695, Management 15675)
$env:EVENTOMAX_TEST_LOCAL_IDENTITIES = 'new-isolated-broker'
$env:EVENTOMAX_TEST_BROKER_ID = 'eventomax-test-isolated'
$env:EVENTOMAX_TEST_PORT = '25695'
$env:EVENTOMAX_TEST_MGMT_PORT = '15675'
$env:EVENTOMAX_RESTART_PHASE = 'seed'
mvn -B -f rabbit-admin/pom.xml test
# Reiniciar exclusivamente el contenedor de prueba para verificar persistencia
docker restart eventomax-test-isolated
# Esperar healthy
$env:EVENTOMAX_RESTART_PHASE = 'verify'
mvn -B -f rabbit-admin/pom.xml '-Dtest=LocalIdentitiesTest' test
python mq/verify-management.py
Remove-Item Env:EVENTOMAX_TEST_LOCAL_IDENTITIES, Env:EVENTOMAX_TEST_BROKER_ID, Env:EVENTOMAX_TEST_PORT, Env:EVENTOMAX_TEST_MGMT_PORT, Env:EVENTOMAX_RESTART_PHASE
git diff --check
```

LocalIdentitiesTest exige obligatoriamente un identificador temporal inequívoco (`EVENTOMAX_TEST_BROKER_ID`) y rechaza de forma estricta cualquier conexión accidental hacia el broker académico de presentación (`eventomax-ep2-demo-20261019`). seed se ejecuta una sola vez por ciclo y deja un mensaje persistente; verify comprueba su supervivencia y retira únicamente los recursos demo.restart-proof. Sin opt-in, los tests normales omiten las pruebas de broker.

## Reinicios y volúmenes existentes

El marcador v2 se escribe solo al finalizar. Un volumen v2 reinicia sin reimportar identidades/topología, cambiar permisos o rotar contraseñas. Volúmenes v1 o sin marcador con datos son rechazados antes de arrancar RabbitMQ. Su migración requiere revisión separada y queda fuera de este cambio. Si falla el primer arranque tras crear datos, investigar: no resetear ni eliminar para ocultar el fallo.

Detener solo esta demo: `docker stop eventomax-ep2-demo-20261019`. Retomarla: `docker start eventomax-ep2-demo-20261019`. Conservar el volumen. No usar down -v, purgas, reset ni eliminación de volúmenes. No se cambia AWS ni configuración productiva.
