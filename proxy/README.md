# Edge Reverse Proxy (`eventomax-edge-proxy`)

Unifica bajo un único puerto HTTP de entrada en EC2 (`8080`, consumido vía HTTPS `:443` por **AWS API Gateway**) las rutas operativas y de documentación de EventoMax:

| Ruta Pública (HTTPS vía API Gateway) | Destino Interno (`eventomax-net`) | Seguridad |
|---|---|---|
| `/api/**`, `/actuator/health` | `ms-eventomax-bff:8080` | Bearer JWT Entra ID (excepto health) |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | `ms-eventomax-bff:8080` | UI pública; ejecución exige Bearer JWT |
| `/kafka-ui/` | `eventomax-kafka-ui:8080` | Formulario login (`AUTH_TYPE=LOGIN_FORM`) |
| `/rabbitmq/` | `rabbitmq-1:15672` | Usuario y contraseña RabbitMQ |

## Despliegue en EC2 (reemplazando el mapeo directo del puerto `8080` del BFF)

1. Detener/recrear `ms-eventomax-bff` **sin** `-p 8080:8080` hacia el host (solo conectado a `--network eventomax-net`, `--name ms-eventomax-bff`) para que Nginx tome el puerto `8080` del host.
2. Recrear `rabbitmq-1` y `eventomax-kafka-ui` con `mq/rabbitmq.prod.conf` (`management.path_prefix = /rabbitmq`) y `kafka/compose.prod.yml` (`SERVER_SERVLET_CONTEXT_PATH=/kafka-ui`, `AUTH_TYPE=LOGIN_FORM`, `KAFKA_UI_PASSWORD` definido en `.env`).
3. Levantar el proxy:
   ```bash
   docker compose --env-file ../.env -f compose.prod.yml up -d
   ```
4. Verificar en **AWS API Gateway (`7dewsdyt7i`)** que existan rutas hacia `http://<IP_EC2>:8080/{proxy}` (por ejemplo `$default` o rutas explícitas `/swagger-ui.html`, `/swagger-ui/{proxy+}`, `/v3/api-docs`, `/v3/api-docs/{proxy+}`, `/kafka-ui`, `/kafka-ui/{proxy+}`, `/rabbitmq`, `/rabbitmq/{proxy+}` sin Authorizer en API Gateway para permitir cargar el HTML de cada UI).
