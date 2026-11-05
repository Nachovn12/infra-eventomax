# Especificación Funcional y Técnica — Cierre EP3 Streaming con Kafka (spec.md)

## 1. Resumen y Contexto
Esta especificación define los requisitos funcionales, no funcionales y criterios de aceptación observables para completar la **EP3** de la plataforma EventoMax según el **Caso 8**. Integra streaming de eventos distribuido con Apache Kafka, reportería analítica en tiempo real, auditoría duradera, administración de infraestructura y complementos de dominio.

---

## 2. Requisitos y Criterios de Aceptación (Dado / Cuando / Entonces)

### RF-01: Clúster Kafka Distribuido y de Alta Disponibilidad
La infraestructura de Kafka debe componerse de un clúster tolerante a fallos con 3 nodos ZooKeeper y 3 brokers Kafka, más una consola de gestión web (Kafka UI).

* **Escenario 1 (Arranque del clúster):**
  * **Dado** que se despliega la infraestructura definida en `infra-eventomax/kafka`.
  * **Cuando** se ejecuta el archivo Compose correspondiente.
  * **Entonces** se inician 3 contenedores ZooKeeper (`zookeeper-1`, `zookeeper-2`, `zookeeper-3`), 3 contenedores Kafka (`kafka-1`, `kafka-2`, `kafka-3`) y 1 contenedor `kafka-ui` en el puerto 8080/18080 reportando estado saludable (*healthy*).
* **Escenario 2 (Tolerancia a fallos):**
  * **Dado** que el clúster está en ejecución y un tópico tiene factor de replicación 3 (`RF=3`).
  * **Cuando** se detiene uno de los 3 brokers Kafka.
  * **Entonces** los productores y consumidores continúan operando sin pérdida de datos gracias a los réplicas sincronizadas (ISR >= 2).

---

### RF-02: Aprovisionamiento de Tópicos y Dead Letter Topics (DLT)
Los tópicos de Kafka deben crearse con 3 particiones y factor de replicación 3 (`RF=3`), junto con sus políticas de retención y limpieza.

* **Escenario 1 (Tópicos principales):**
  * **Dado** el clúster Kafka operativo con 3 brokers.
  * **Cuando** se ejecuta el script de inicialización de tópicos.
  * **Entonces** se crean:
    * `productions.events`: 3 particiones, RF=3, `cleanup.policy=delete`, retención 7 días.
    * `audit.timeline`: 3 particiones, RF=3, `cleanup.policy=compact,delete`, retención 30 días.
* **Escenario 2 (Tópicos de Reintento y DLT):**
  * **Dado** los consumidores de Report y Audit que manejan errores con DLT.
  * **Cuando** se ejecuta el aprovisionamiento de infraestructura.
  * **Entonces** se crean los tópicos DLT explícitamente:
    * `productions.events.report.DLT`: 3 particiones, RF=3, retención 14 días.
    * `productions.events-audit-retry`: 3 particiones, RF=3.
    * `productions.events-audit-dlt`: 3 particiones, RF=3, retención 14 días.

---

### RF-03: Microservicio Administrador de Kafka (`kafka-admin`) y Rutas BFF
Debe existir un microservicio Spring Boot dedicado a la administración de Kafka que encapsule el `AdminClient` y exponga operaciones REST protegidas por Azure AD y rol `Admin`, accesibles a través del BFF.

* **Escenario 1 (Listado de tópicos por Admin):**
  * **Dado** un usuario autenticado con rol `Admin` y scope `access_as_user`.
  * **Cuando** solicita `GET /api/kafka-admin/topics` a través del BFF.
  * **Entonces** el BFF enruta la petición hacia `kafka-admin` y retorna código HTTP 200 con la lista de tópicos, número de particiones, factor de replicación y configuración de retención.
* **Escenario 2 (Denegación a roles no autorizados):**
  * **Dado** un usuario autenticado con rol `Productor`, `Organizador` o `Auditor`.
  * **Cuando** intenta acceder a `/api/kafka-admin/**`.
  * **Entonces** el BFF rechaza la petición con código HTTP 403 Forbidden.
* **Escenario 3 (Gestión de tópicos):**
  * **Dado** un usuario con rol `Admin`.
  * **Cuando** envía `POST /api/kafka-admin/topics` con nombre, particiones y factor de replicación.
  * **Entonces** el servicio crea el tópico en el clúster y retorna HTTP 201 Created.

---

### RF-04: Analítica en Streaming en `ms-eventomax-report` (KPIs Faltantes)
El microservicio de reportes debe procesar eventos en streaming desde `productions.events` y calcular los KPIs de negocio solicitados en el Caso 8.

* **Escenario 1 (KPI Tiempo de Montaje):**
  * **Dado** eventos de producciones que transitaron a `EN_MONTAJE` y posteriormente a `EN_EJECUCIÓN`.
  * **Cuando** el usuario Admin consulta `GET /api/report/kpis?range=...`.
  * **Entonces** la respuesta incluye la métrica `averageMontageTimeMinutes` (o equivalente en horas) calculada a partir de la diferencia entre ambos hitos.
* **Escenario 2 (KPI Eventos por Día):**
  * **Dado** producciones con eventos ocurridos en un rango temporal.
  * **Cuando** se solicita `GET /api/report/kpis?range=last7d`.
  * **Entonces** la respuesta incluye una serie cronológica `eventsPerDay` con objetos `{ date: "YYYY-MM-DD", count: number }`.

---

### RF-05: Tópico `audit.timeline` y Trazabilidad Completa en `ms-eventomax-audit`
El sistema debe cumplir con el propósito del tópico compactado `audit.timeline` y registrar el origen de las acciones.

* **Escenario 1 (Publicación y Consumo en `audit.timeline`):**
  * **Dado** un evento de ciclo de vida de producción emitido.
  * **Cuando** se procesa la auditoría de la producción.
  * **Entonces** se publica un mensaje en `audit.timeline` con el `productionId` como clave de compactación para preservar el último estado conocido y su histórico.
* **Escenario 2 (Trazabilidad "quién, qué, cuándo, desde dónde"):**
  * **Dado** un evento registrado en el timeline de auditoría.
  * **Cuando** un Auditor consulta `GET /api/audit/timeline`.
  * **Entonces** cada registro expone actor, tipo de acción, marca de tiempo y procedencia/origen (`source` o canal).

---

### RF-06: Frontend Angular — Visualización de KPIs y Panel en Tiempo Real
El frontend debe presentar los nuevos indicadores y ofrecer actualización automática sin requerir recarga manual del usuario.

* **Escenario 1 (Visualización de Tiempo de Montaje y Eventos/Día):**
  * **Dado** un Administrador en la pantalla `/reports`.
  * **Cuando** se cargan los datos analíticos.
  * **Entonces** se muestran tarjetas para el Tiempo Promedio de Montaje y una sección/gráfico para la serie de Eventos por Día.
* **Escenario 2 (Panel en Tiempo Real / Polling):**
  * **Dado** un usuario en `/dashboard` o `/reports`.
  * **Cuando** transcurre el intervalo configurado (ej. 30 segundos) sin interacción.
  * **Entonces** el componente refresca silenciosamente los datos manteniendo el estado visual sin parpadeos.

---

### RF-07: Asignación de Cuadrilla en `ms-eventomax-productions`
El Productor debe poder asignar formalmente una cuadrilla responsable a la producción al confirmarla o prepararla para montaje.

* **Escenario 1 (Asignación de Cuadrilla):**
  * **Dado** una producción en estado `SOLICITADO` o `CONFIRMADO`.
  * **Cuando** el Productor actualiza la producción especificando `crewId` / `crewName`.
  * **Entonces** la asignación se persiste en base de datos y se incluye en el payload del comando RabbitMQ `q.cmd.crew` y en el ticket generado.

---

## 3. Matriz de Trazabilidad con el Caso 8

| Requisito Spec | Sección Caso 8 | Componente Principal |
|:---|:---|:---|
| `RF-01` | Sección 1 y 7 (EC2 Kafka: 3 ZK + 3 Brokers) | `infra-eventomax/kafka` |
| `RF-02` | Sección 9 (Topología Kafka: delete 7d, compact 30d, DLTs) | `infra-eventomax/kafka/init-topics.sh` |
| `RF-03` | Sección 5 ("más un microservicio administrador de Kafka") | `infra-eventomax/kafka-admin` + `ms-eventomax-bff` |
| `RF-04` | Sección 1 y 3 ("tiempo de montaje, eventos por día") | `ms-eventomax-report` |
| `RF-05` | Sección 1, 5 y 9 ("quién, qué, cuándo, desde dónde" + `audit.timeline`) | `ms-eventomax-audit` |
| `RF-06` | Sección 1 y 6 ("panel en tiempo real, pantalla /reports") | `frontend-eventomax` |
| `RF-07` | Sección 2 y 3 ("Productor confirma y asigna cuadrilla") | `ms-eventomax-productions` |

