# ms-andesstay-rabbit-admin

Microservicio administrador de **RabbitMQ** del proyecto **AndesStay** (caso DSY1107 – Desarrollo Cloud Native I, Duoc UC): expone una API REST para crear, consultar y eliminar colas, exchanges y bindings del broker, sin entrar a la consola de RabbitMQ.

## Rol en la arquitectura

```
Angular / Postman → API Gateway → ms-andesstay-bff → ms-andesstay-rabbit-admin ──AMQP──► RabbitMQ (EC2 infra)
```

- No aloja RabbitMQ: el broker sigue en la infraestructura compartida ([andesstay-infra](https://github.com/MarCOCO999/andesstay-infra), contenedor `rabbitmq:3.13-management`). Este servicio se conecta a él por AMQP y lo administra con `AmqpAdmin` / `RabbitAdmin` de Spring AMQP.
- El BFF le reenvía todo lo que llega a `/api/rabbit/**`, y ambos exigen el rol **Admin** del JWT de Azure AD (defensa en profundidad, igual que el resto de los microservicios).
- **Protege la topología del sistema**: las colas, DLQs, colas de reintento y exchanges que usan `ms-andesstay-notify` y `ms-andesstay-reservations` (`q.cmd.*`, `cmd.*`) no se pueden eliminar desde la API (`409`), para que una llamada de administración no corte las notificaciones. La lista está en `application.yml` (`andesstay.rabbit-admin`).

## Diseño

| Capa | Clases | Responsabilidad |
|---|---|---|
| Web | `QueueController`, `ExchangeController`, `BindingController` | Rutas REST, validación de entrada (`@Valid`), códigos HTTP. No conocen Spring AMQP. |
| DTOs | `web/dto` (`CreateQueueRequest`, `CreateExchangeRequest`, `BindingRequest`, respuestas) | Contrato de la API con Bean Validation. |
| Servicio | `RabbitAdminService` | **Toda** la lógica de administración: comprueba existencia, aplica la protección, construye `Queue`/`Exchange`/`Binding`, llama a `AmqpAdmin` y traduce los errores del broker a excepciones del dominio. |
| Errores | `GlobalExceptionHandler` | Un JSON uniforme `{timestamp, status, message, errors}`. |

## Endpoints

Todos requieren `Authorization: Bearer <access_token>` con el rol `Admin`.

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/rabbit/queues` | Crea una cola | `201` + `Location` · `400` · `404` (DLX inexistente) · `409` (ya existe) |
| `GET` | `/api/rabbit/queues/{name}` | Mensajes listos y consumidores de la cola | `200` · `404` |
| `DELETE` | `/api/rabbit/queues/{name}?ifUnused=&ifEmpty=` | Elimina la cola; con `ifUnused`/`ifEmpty` solo si no tiene consumidores/mensajes | `204` · `404` · `409` (protegida o condición no cumplida) |
| `DELETE` | `/api/rabbit/queues/{name}/messages` | Vacía la cola (purge) | `200` `{queue, purgedMessages}` · `404` |
| `POST` | `/api/rabbit/exchanges` | Crea un exchange `direct`, `topic`, `fanout` o `headers` | `201` · `400` · `409` |
| `DELETE` | `/api/rabbit/exchanges/{name}` | Elimina el exchange | `204` · `404` · `409` (protegido) |
| `POST` | `/api/rabbit/bindings` | Enlaza un exchange con una cola | `201` · `400` · `404` (falta el exchange o la cola) |
| `DELETE` | `/api/rabbit/bindings?exchange=&queue=&routingKey=` | Elimina el binding | `204` · `400` · `404` · `409` (binding del sistema) |

### Ejemplos

```http
POST /api/rabbit/queues
{ "name": "q.reportes.pdf", "durable": true, "deadLetterExchange": "cmd.dead.dlx",
  "deadLetterRoutingKey": "reportes.dead", "messageTtlMs": 60000, "maxLength": 1000 }

POST /api/rabbit/exchanges
{ "name": "reportes.topic", "type": "topic" }

POST /api/rabbit/bindings
{ "exchange": "reportes.topic", "queue": "q.reportes.pdf", "routingKey": "reportes.#" }
```

### Validación de entrada

| Campo | Regla |
|---|---|
| `name`, `exchange`, `queue`, `deadLetterExchange` | Obligatorios donde corresponde; solo letras, números, `.`, `_`, `-` (1–200); no pueden empezar con `amq.` (reservado por RabbitMQ) |
| `routingKey`, `deadLetterRoutingKey` | Letras, números, `.`, `_`, `-`, `*`, `#` (máx. 255); vacía es válida (fanout) |
| `deadLetterRoutingKey` | Requiere `deadLetterExchange` |
| `messageTtlMs` | Mayor que 0 y hasta 7 días |
| `maxLength` | Mayor que 0 |
| `type` | `direct`, `topic`, `fanout` o `headers` (sin distinguir mayúsculas) |

Un error de validación responde `400` con el detalle por campo:

```json
{ "status": 400, "message": "La solicitud tiene parametros invalidos",
  "errors": { "name": "solo puede tener letras, numeros, '.', '_' o '-' (1 a 200 caracteres) y no puede empezar con 'amq.'" } }
```

## Stack

- Spring Boot 4.1.1 / Java 21
- Spring AMQP (`AmqpAdmin` / `RabbitAdmin`)
- Spring Security OAuth2 Resource Server (JWT de Azure AD)
- Bean Validation

## Configuración

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8086` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | Conexión a RabbitMQ | `localhost` / `5672` / `guest` / `guest` / `/` |
| `AZURE_ISSUER_URI` / `AZURE_API_AUDIENCE` | Validación del JWT de Azure AD | tenant/App ID URI del caso |

## Cómo ejecutarlo en local

Requiere Java 21 y RabbitMQ levantado (ver [andesstay-infra](https://github.com/MarCOCO999/andesstay-infra)).

```bash
./mvnw spring-boot:run
./mvnw test
```

Pruebas:

- `RabbitAdminServiceTest`: lógica del servicio con `AmqpAdmin` simulado (protección, existencia, argumentos de la cola, broker caído).
- `RabbitAdminControllersTest`: validación de entrada y códigos HTTP.
- `RabbitAdminApiIntegrationTest`: contra el RabbitMQ local real, con el JWT simulado por `spring-security-test`. Cubre `401` sin token, `403` con otro rol, el ciclo completo de crear y eliminar exchange, cola y binding, y la protección de la topología. Se omite si no hay broker en `localhost:5672`.

## Repos relacionados

[frontend-andesstay](https://github.com/MarCOCO999/frontend-andesstay) · [ms-andesstay-bff](https://github.com/MarCOCO999/ms-andesstay-bff) · [ms-andesstay-reservations](https://github.com/MarCOCO999/ms-andesstay-reservations) · [ms-andesstay-catalog](https://github.com/MarCOCO999/ms-andesstay-catalog) · [ms-andesstay-notify](https://github.com/MarCOCO999/ms-andesstay-notify) · [ms-andesstay-audit](https://github.com/MarCOCO999/ms-andesstay-audit) · [ms-andesstay-report](https://github.com/MarCOCO999/ms-andesstay-report) · [andesstay-infra](https://github.com/MarCOCO999/andesstay-infra)
