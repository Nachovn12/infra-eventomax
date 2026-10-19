# Bootstrap local de RabbitMQ

El Compose local inicia el broker sin importar definiciones durante el primer arranque. Así RabbitMQ crea el usuario y vhost con las variables privadas existentes; luego `bootstrap-local.sh` importa `definitions.json`, elimina etiquetas administrativas del usuario de aplicación y limita sus permisos a los recursos `cmd.*` y `q.cmd.*` del caso.

La topología existente usa el vhost `/`. Se rechaza un vhost distinto para evitar importar recursos en el destino equivocado. Las credenciales son obligatorias, no tienen defaults y no se imprimen. El healthcheck exige que el bootstrap haya terminado.

## Validación aislada

Usar un nombre de proyecto Compose nuevo, un nombre de contenedor único y puertos libres:

```powershell
# Las variables RABBITMQ_DEFAULT_USER/PASS deben venir de configuración privada existente.
$env:RABBITMQ_CONTAINER_NAME = 'eventomax-rabbitmq-emx92-validation'
$env:RABBITMQ_AMQP_PORT = '15692'
$env:RABBITMQ_MANAGEMENT_PORT = '15693'
docker compose -p eventomax-emx92-validation -f mq/compose.yml config --quiet
docker compose -p eventomax-emx92-validation -f mq/compose.yml up -d
```

Verificar primero que proyecto, volumen, nombre y puertos no existan. Nunca apuntar esta validación a un volumen compartido. El script rechaza un volumen RabbitMQ previamente inicializado que no tenga su marcador de bootstrap. En un reinicio del mismo volumen marcado no cambia usuarios, contraseñas, permisos, topología ni mensajes. Las variables de credenciales solo inicializan un volumen nuevo: no rotan contraseñas existentes.

La consola web queda vinculada a loopback, pero el usuario de aplicación no tiene etiqueta de administración. Usar `docker exec … rabbitmqctl list_users`, `list_vhosts`, `list_permissions -p /` y `list_queues -p /` para inspección local. No se añaden credenciales administrativas nuevas.

No ejecutar `down -v` ni eliminar volúmenes existentes. Detener únicamente el proyecto temporal tras la prueba; conservar su volumen identificado para revisión. No se modifica `compose.prod.yml` ni `rabbitmq.prod.conf`.

Referencia: [RabbitMQ access control](https://www.rabbitmq.com/docs/access-control), sección de usuario por defecto y la importación de definiciones en nodos vacíos.
