# Plan · CM-279 · Réplica de la fecha de nacimiento en Perfil

Spec: `spec.md` de esta carpeta. Base: `origin/develop` `2a54ca7`. Rama: `CM-279-replica-fecha-nacimiento`.
Estado: pendiente de aprobación de Paula. Las decisiones de diseño están respondidas (sección 14 de la spec); no hay tarjetas bloqueadas.

## 1. Enfoque

| Bloque | PR (base) | Qué deja funcionando | Líneas estimadas | Horas |
|---|---|---|---|---|
| PR 0 | `develop` | Solo `specs/CM-279-replica-fecha-nacimiento/` (≈ 1100 líneas de documentos) | ≈ 1100 | 0,5 |
| P1 | PR 0 fusionado (o `develop`) | Tablas de la réplica y del Inbox, puertos, adaptadores, servicio de aplicación con su transacción, `Clock`, excepción y código 503 en el catálogo. Sin RabbitMQ | ~800 | 2,5 |
| P2 | P1 | Topología, consumidores, fallidos, configuración, `docker-compose.yml` corregido, copia del JSON Schema, pruebas con RabbitMQ real, documentación, Postman | ~850 | 3,0 |

Total ≈ 5,5 h en Perfil (más 0,5 h del PR 0) (más ≈ 9,5 h en Cuentas). El bloque 5 de CM-274 se apila sobre P1.

## 2. Archivos

| Acción | Ruta (bajo `src/main/java/co/edu/unicauca/cameia/perfil/` salvo que se diga otra) | Bloque |
|---|---|---|
| Crear | `src/main/resources/db/migration/V5__replica_fecha_nacimiento.sql` | P1 |
| Crear | `domain/port/BirthDateReplica.java`, `domain/port/ProcessedMessageInbox.java` | P1 |
| Crear | `domain/exception/BirthDateUnavailableException.java`, `domain/exception/InvalidAccountEventException.java` | P1 |
| Modificar | `domain/exception/ErrorCode.java` (`BIRTH_DATE_UNAVAILABLE`) | P1 |
| Modificar | `presentation/advice/ErrorCatalog.java` (fila 503) | P1 |
| Modificar | `presentation/advice/ApiExceptionHandler.java` (`reject`: `ERROR` sin traza para los 5xx de negocio, `WARN` para los 4xx; D19 de CM-274) | P1 |
| Crear | `application/command/ReplicateBirthDateCommand.java`, `application/command/RemoveBirthDateCommand.java` | P1 |
| Crear | `application/service/BirthDateReplicaAppService.java` | P1 |
| Crear | `infrastructure/config/ClockConfig.java` | P1 |
| Crear | `infrastructure/persistence/entity/BirthDateReplicaEntity.java`, `infrastructure/persistence/entity/ProcessedMessageEntity.java` | P1 |
| Crear | `infrastructure/persistence/repository/BirthDateReplicaJpaRepository.java`, `BirthDateReplicaRepositoryAdapter.java`, `ProcessedMessageJpaRepository.java`, `ProcessedMessageInboxAdapter.java` | P1 |
| Modificar | `docs/errores.md` (fila del código nuevo; cita del libro `09102026_01_Backlog_v6.xlsx`) | P1 |
| Crear | `infrastructure/messaging/config/AccountEventsRabbitConfig.java` | P2 |
| Modificar | `infrastructure/messaging/config/RabbitConfig.java` (quitar `cuenta-eliminada`; convertidor seguro) | P2 |
| Crear | `infrastructure/messaging/payload/AccountCreatedPayloadV1.java`, `AccountDeletedPayloadV1.java` | P2 |
| Crear | `infrastructure/messaging/consumer/AccountCreatedListener.java`, `AccountEventsErrorHandler.java` | P2 |
| Reescribir | `infrastructure/messaging/consumer/AccountDeletedListener.java` | P2 |
| Crear | `src/test/resources/contracts/cuenta-creada-v1.schema.json` (copia literal del de Cuentas) | P2 |
| Modificar | `src/main/resources/application.yml` (sección `spring.rabbitmq`) | P2 |
| Modificar | `.env.example` (el `docker-compose.yml` no cambia: `guest` sí se conecta desde el contenedor) | P2 |
| Crear | `infrastructure/messaging/config/AccountEventsRetryConfig.java` (excluye del reintento los rechazos por contrato) | P2 |
| Crear | `docs/perfil-local.postman_environment.json` (`base_url`, datos del broker local) | P2 |
| Crear | `docs/adr/0004-replica-de-fecha-de-nacimiento-por-evento.md` | P2 |
| Modificar | `CLAUDE.md` (tabla de componentes, datos, pendientes de la sección 10), `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` y su entorno | P2 |

**No se tocan:** `SubscriptionUpdatedListener`,
`ConsumptionRecordedListener`, `ProfileEventPublisher`, las colas y el exchange `cameia.events` que no son de esta CM, migraciones
existentes, `.github/`.

## 3. Decisiones técnicas

| # | Decisión | Por qué | Descartado |
|---|---|---|---|
| T1 | La validación del mensaje vive en `BirthDateReplicaAppService` y lanza `InvalidAccountEventException` con un `reason` | Regla probada sin Spring ni broker; el consumidor solo traduce | Bean Validation sobre el registro de la carga (necesita configurar el validador del contenedor de mensajes y no da el código de causa) |
| T2 | El consumidor traduce `InvalidAccountEventException` y `MessageConversionException` a `AmqpRejectAndDontRequeueException` | Un dato inválido no mejora al reintentar | Reintentar todo (tres intentos inútiles por mensaje inválido) |
| T3 | Reintento con las propiedades de Spring Boot (`spring.rabbitmq.listener.simple.retry.*`, con `max-retries: 2`) y `default-requeue-rejected=false`, más un `RabbitListenerRetrySettingsCustomizer` que excluye `AmqpRejectAndDontRequeueException` | Sin código propio de reintento; por defecto Spring reintentaría también los mensajes inválidos; el mensaje agotado va al DLX de la cola | `RetryTemplate` a mano; reintentar todo |
| T4 | Inbox y réplica con `INSERT … ON CONFLICT DO NOTHING` nativo dentro de una transacción del servicio de aplicación; los adaptadores llevan `@Transactional` en sus métodos, como `ProfessionalProfileRepositoryAdapter` | Idempotente sin capturar excepciones de integridad; seguro con dos consumidores; IOP-04 y FIA-02 | `existsById` + `save` (carrera); solo clave natural sin Inbox (no cumple IOP-04) |
| T5 | Topología en una clase nueva `AccountEventsRabbitConfig` | `RabbitConfig` mezcla esqueletos de otras tareas; esta CM no los toca | Ampliar `RabbitConfig` |
| T6 | `setAlwaysConvertToInferredType(true)` en el convertidor existente | Ignora `__TypeId__` y usa el tipo del parámetro (deserialización segura) | Lista de paquetes de confianza |
| T7 | RabbitMQ en pruebas con `GenericContainer` y `@DynamicPropertySource` | Sin dependencia nueva | Módulo `testcontainers-rabbitmq` |
| T8 | `ClockConfig` con `Clock.systemUTC()` | No hay reloj inyectable; lo usan el Inbox (`procesado_en`), la regla de fecha futura y CM-274 | `LocalDate.now(ZoneOffset.UTC)` en el servicio (no se puede fijar en pruebas) |
| T9 | El consumidor lee `message_id` con `@Header(AmqpHeaders.MESSAGE_ID)` (puede ser `null`) y el servicio lo convierte a `UUID` | Un `message_id` ausente o inválido es un mensaje inválido con su código (`MESSAGE_ID_INVALID`), no un fallo técnico | Generar un id si falta (rompe la idempotencia) |
| T10 | La prueba de contrato deserializa `examples[0]` de la copia del JSON Schema con el registro de la carga y comprueba nombres y obligatorios leyendo el esquema con Jackson | Cumple IOP-01 sin dependencia nueva, igual que `ProfileUpdatedEventContractTest` de CM-67 | Validador de JSON Schema (dependencia nueva) |
| T11 | El nivel de log del rechazo se elige por estado en `ApiExceptionHandler.reject` (`>= 500` → `ERROR` sin traza) | Lo decide D19 de CM-274 y ninguna tarjeta de CM-274 lo implementa; esta CM introduce el 503 | Una rama por excepción (frágil cuando aparezca otro 503) |

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| Spring AMQP 4 y Jackson 3: nombres de clase del convertidor (`JacksonJsonMessageConverter`) y del método `setAlwaysConvertToInferredType` | La tarjeta T-P2.2 lo comprueba en el jar antes de escribir; si no existe, se detiene y reporta |
| Con reintento activo, Spring reintenta también `AmqpRejectAndDontRequeueException` (comprobado: `DefaultRetryPolicy` reintenta toda excepción salvo las excluidas) | `AccountEventsRetryConfig` la excluye; `AccountEventsRetryIT` exige 1 invocación para un mensaje inválido y 3 para un fallo técnico |
| Número de migración: la spec de CM-274 también pide `V5` | P1 va primero y toma `V5`; la revisión de CM-274 (acción A6) mueve su migración al siguiente número. T-P1.1 comprueba que `V5` siga libre |
| La copia del JSON Schema puede desviarse del original de Cuentas | La tarjeta T-P2.4 la copia literal del bloque de la spec de Cuentas; la prueba de punta a punta de la sección 7 de las tarjetas de Cuentas la confirma |
| Las `*IT` necesitan Docker | Igual que hoy (Failsafe en `verify`) |
| `PerfilApplicationIT` sin broker | Spring AMQP no falla el arranque si no hay broker; se comprueba que sigue en verde |

## 5. Matriz de pruebas

| Camino | Prueba | Capa | Bloque |
|---|---|---|---|
| Guardar válido; mensaje ya procesado; otro mensaje del mismo Usuario con otra fecha; 128 y 129 caracteres; vacío, espacios, tabulador, nulo; fecha nula, hoy, mañana, bisiesto; `message_id` ausente, vacío y no UUID | `BirthDateReplicaAppServiceTest` | aplicación | P1 |
| Borrar existente y ausente; borrar con mensaje ya procesado; `usuarioId` o `message_id` inválido al borrar | `BirthDateReplicaAppServiceTest` | aplicación | P1 |
| Falla de la réplica revierte el Inbox | `BirthDateReplicaAppServiceIT` | aplicación + BD | P1 |
| Restricciones de las dos tablas (PK duplicada, uid en blanco, fecha nula, uid de 129, tipo fuera de la lista, `procesado_en` nulo), largo = `FirebaseUid.MAX_LENGTH`, columnas exactas, índices | `BirthDateReplicaSchemaIT` | persistencia | P1 |
| `saveIfAbsent` inserta, ignora, concurrente; `findBirthDate` presente y ausente; `delete` presente y ausente | `BirthDateReplicaRepositoryAdapterIT` | persistencia | P1 |
| `registerIfAbsent` nuevo, repetido, concurrente | `ProcessedMessageInboxAdapterIT` | persistencia | P1 |
| 503 `BIRTH_DATE_UNAVAILABLE` con forma completa; 5xx de negocio en `ERROR` sin traza y 4xx en `WARN` | `ApiExceptionHandlerTest`, `ErrorCatalogTest` | web | P1 |
| Consumidor: comando correcto con `message_id`; inválido → rechazo sin reintento; fallo técnico → se propaga; nunca registra la carga | `AccountCreatedListenerTest`, `AccountDeletedListenerTest` | infraestructura | P2 |
| Ejemplo del JSON Schema v1 deserializado | `AccountCreatedPayloadV1Test` | contrato | P2 |
| Topología con los nombres del contrato y los argumentos de fallidos | `AccountEventsTopologyIT` | mensajería real | P2 |
| De punta a punta en el broker: válido, mismo mensaje 20 veces, reentrega tras el commit, campos extra, `__TypeId__`, cada inválido de la sección 10, eliminación, logs sin datos personales | `BirthDateReplicationIT` | mensajería real + BD | P2 |
| Arranque sin broker | `PerfilApplicationIT` (existente) | integración | P2 |

## 6. Orden y paralelismo

P1 → P2. Independiente de Cuentas salvo la copia del JSON Schema (se toma de la spec de Cuentas). El bloque 5 de CM-274 se apila
sobre P1 (necesita solo el puerto, el doble y la excepción). La prueba manual de punta a punta está en la sección 7 de `tasks.md` de
Cuentas.

## 7. Verificación de cierre

`.\mvnw.cmd clean verify` (Surefire y Failsafe) con la salida real; cobertura ≥ 90 % de líneas y ramas por clase de la sección 2;
Newman de la colección completa contra el jar con PostgreSQL y RabbitMQ de compose.
