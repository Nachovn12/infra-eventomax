# EventoMax — Constitución del Proyecto (PROJECT.md)

## 1. Visión y Objetivo
EventoMax es una plataforma unificada para la coordinación, producción y montaje de eventos para una red de 20 productoras. El objetivo de este ciclo es completar la implementación correspondiente a la **Evaluación Parcial N° 3 (EP3 - Streaming con Apache Kafka)** de la asignatura *Desarrollo Cloud Native I (DSY1107)*, cerrando las brechas funcionales y no funcionales del caso semestral.

## 2. Actores y Roles
- **Admin:** Administra catálogo de servicios y equipos, gestiona infraestructura/tópicos y visualiza KPIs y reportes analíticos.
- **Productor (Operador):** Confirma eventos, asigna cuadrillas de montaje y cierra producciones.
- **Organizador (Cliente):** Solicita eventos nuevos, consulta el estado de sus producciones y descarga actas/cotizaciones.
- **Auditor:** Consulta la línea de tiempo (timeline) de trazabilidad de producciones (solo lectura).

## 3. Decisiones de Arquitectura Ratificadas
- **Base de Datos:** PostgreSQL en sustitución de Oracle en todos los microservicios (`productions`, `catalog`, `report`, `audit`, `notify`).
- **Seguridad e Identidad (IDaaS):** Microsoft Entra ID (Azure AD) con MSAL en frontend Angular y Resource Server JWT en Spring Boot BFF.
- **Mensajería Asíncrona (EP2):** RabbitMQ (3 exchanges, 6 colas, 3 DLQs, tareas de correo, tickets y generación de PDFs con `rabbit-admin`).
- **Streaming de Eventos (EP3):** Apache Kafka con Zookeeper, particionamiento por clave de agregado (`productionId`), replicación distribuida (`RF=3`), patrón Outbox transaccional y derivación a Dead Letter Topics (DLT).

## 4. No-Objetivos
- No realizar migraciones destructivas a Oracle; la base PostgreSQL es la arquitectura de persistencia oficial del proyecto.
- No alterar los flujos de autorización y tokens de Azure Entra ID validados en EP1 y EP2.
- No implementar notificaciones WebPush complejas mientras el canal SMTP de correo cubra el requisito de comunicación al organizador.
- No introducir componentes de infraestructura especulativos que no estén indicados en el Caso 8.

## 5. Reglas Innegociables
- Código probado y verificable: Todo cambio en microservicios debe compilar y contar con pruebas automatizadas.
- Integridad contractual: Respetar los contratos OpenAPI, esquemas de eventos JSON y rutas perimetrales del BFF.
- Seguridad en todas las capas: No exponer endpoints de backend directamente a Internet; todo tráfico transita por el API Gateway / BFF.

