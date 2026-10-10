# CM-279 · Réplica de la fecha de nacimiento en Perfil (HT-04, consumidor)

- **Jira:** CM-279 (subtarea de CM-256, «HT-04 Fecha de nacimiento disponible para Perfil»).
- **Fuente de requisitos:** libro `09102026_01_Backlog_v6.xlsx`, hoja Backlog, fila HT-04 (CA-HT04.1 a CA-HT04.4 y su línea
  «Contrato»); hoja HE-02, HU-2.4 (nota técnica «Fecha de nacimiento» y CA-2.4.20 a 2.4.23, 2.4.31, 2.4.32, 2.4.36, 2.4.52 y 2.4.53);
  anexo `Anexo_Restricciones_Atributos_Calidad` de la Entrega 1 (FIA, DES, SEG, INT, MAN e IOP).
- **Repositorio:** `cameia-perfil`, base `origin/develop` `2a54ca7`. La otra mitad (productor en Cuentas) está en
  `cameia-cuentas/specs/CM-279-PublicarCuentaCreada/spec.md`.
- **Estado:** **LISTA PARA EJECUTAR** (sección 23); pendiente de aprobación de Paula. Las decisiones de diseño están respondidas (sección 14).

## 1. Contexto

HU-2.4 valida las fechas de la experiencia laboral y de la formación contra la fecha de nacimiento del Usuario. Por decisión del Product
Owner (backlog v5, 8-oct-2026) Perfil no llama a Cuentas: guarda una copia local (réplica) con solo el identificador del Usuario y su
fecha de nacimiento, alimentada por el evento `cuenta.creada` que publica Cuentas, y la borra con `cuenta.eliminada`. Cada mensaje
procesado queda anotado en una tabla Inbox para que una reentrega no tenga efecto (IOP-04 y FIA-02 del anexo de atributos de calidad).

**Estado del código en `2a54ca7`:**
- No hay tabla ni puerto para la fecha de nacimiento, ni tabla Inbox.
- `RabbitConfig` declara un exchange `cameia.events` y colas `perfil-profesional-actualizado`, `cuenta-eliminada`,
  `suscripcion-actualizada` y `consumo-registrado`, con nombres que no son los del contrato vigente; ninguna tiene cola de mensajes
  fallidos.
- `AccountDeletedListener` escucha `cuenta-eliminada`, recibe `Object` y escribe la carga completa en un `WARN`
  (`payload={}`): si llegara un mensaje, su contenido terminaría en el log. Es un defecto de privacidad que esta CM corrige al reemplazar
  el consumidor.
- `docker-compose.yml` levanta RabbitMQ con `guest/guest` y la aplicación se conecta con ellos. Funciona: la imagen
  `rabbitmq:3.13-management-alpine` trae `loopback_users.guest = false` (comprobado en su `10-defaults.conf`). No hay nada que corregir.
- `.env.example` declara que RabbitMQ queda fuera de las variables que la aplicación consume; con esta CM deja de ser cierto.
- El reintento de Spring (`spring.rabbitmq.listener.simple.retry`) reintenta toda excepción por defecto, también los rechazos por
  contrato; hay que excluirlos (REQ-RF-17).
- No existe un `Clock` inyectable en Perfil.
- La última migración es `V4__i18n_catalogo_roles.sql`.

## 2. Alcance

**Dentro:**
1. Tablas `fecha_nacimiento_usuario` y `evento_procesado` (migración `V5`) con sus puertos `BirthDateReplica` y `ProcessedMessageInbox`
   y sus adaptadores.
2. Consumo de `cuenta.creada` (cola `perfil.cuenta-creada`): guarda la fecha; idempotente por Inbox y por clave natural.
3. Consumo de `cuenta.eliminada` (cola `perfil.cuenta-eliminada`): borra la fecha; idempotente por Inbox.
4. Colas de mensajes fallidos y reintentos.
5. Excepción y código `BIRTH_DATE_UNAVAILABLE` (503), que usará CM-274 cuando la réplica no tenga la fecha.
6. `Clock` UTC inyectable.
7. Prueba de compatibilidad contra el JSON Schema versionado de `cuenta.creada` v1 que publica Cuentas (IOP-01).
8. Registro en `ERROR` sin traza de los rechazos de negocio con estado 5xx (`ApiExceptionHandler.reject`); los 4xx siguen en `WARN`.
9. Configuración (`application.yml`, exclusión de reintento), `.env.example`, `CLAUDE.md`, `docs/errores.md`, ADR, Postman y su archivo de entorno.

**Fuera (con destino):**

| Qué | Destino |
|---|---|
| Aplicar las reglas de fechas de HU-2.4 con la réplica (CA-2.4.20 a 2.4.23, 2.4.31, 2.4.32) y responder 503 al agregar (CA-2.4.36, 2.4.52) o no leer la réplica sin fechas (CA-2.4.53) | CM-274, bloque 5, que se apila sobre el bloque P1 de esta CM y usa su puerto y su excepción |
| Publicar `cuenta.creada` | Mitad Cuentas de CM-279 |
| Publicar `cuenta.eliminada` | CM-179 (purga) y la tarea de HU-1.9 |
| Borrar los perfiles del Usuario al recibir `cuenta.eliminada` | Tarea de HU-1.9 en Perfil, que Vela debe abrir cuando la HU entre (hoy «Próximamente») |
| Colas `suscripcion-actualizada`, `consumo-registrado`, `perfil-profesional-actualizado` y el exchange `cameia.events` | HT-03 y HU-2.12 (no se tocan aquí) |
| Separar API y consumidor en dos servicios de Cloud Run (pedido 10) | DevOps; Perfil ya lo permite con `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false` en la instancia de API |
| Agregar `cuenta-creada` al inventario de eventos activos del anexo de atributos de calidad (hoy lista cinco, sin este) | Actualización del anexo en la próxima entrega |

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA | Qué exige a Perfil | Requisitos | Prueba automática | Postman (carpeta «Réplica de fecha de nacimiento (local)») |
|---|---|---|---|---|
| CA-HT04.1 | Al consumir `cuenta.creada` con 15/03/2008, la réplica tiene esa fecha y Perfil la lee sin llamar a Cuentas | REQ-RF-01 a 05, REQ-RF-20 | `BirthDateReplicationIT.accountCreated_shouldStoreBirthDate_whenEventIsValid`, `BirthDateReplicaRepositoryAdapterIT.findBirthDate_shouldReturnStoredDate_whenPresent` | R-01, R-02 |
| CA-HT04.1 (reglas aplicadas) | Las reglas de HU-2.4 usan la réplica | — (CM-274) | En CM-274: `ProfileControllerIT.addWorkExperience_shouldReturn422_whenStartIsBeforeAge15` y `addEducation_shouldReturn422_whenStartIsBeforeBirthMonth` con la réplica cargada por el evento | En CM-274 |
| CA-HT04.2 | Sin fecha en la réplica → 503 «Ocurrió un error. Inténtalo de nuevo.», sin guardar | REQ-RF-20, 21 | `ApiExceptionHandlerTest.birthDateUnavailable_shouldReturn503_whenThrown`, `BirthDateReplicaRepositoryAdapterIT.findBirthDate_shouldBeEmpty_whenAbsent`; la respuesta del endpoint, en CM-274 | En CM-274 |
| CA-HT04.3 (duplicado) | `cuenta.creada` repetido → un registro, sin error | REQ-RF-04, 14, 15 | `BirthDateReplicationIT.accountCreated_shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times`, `BirthDateReplicaAppServiceTest.recordAccountCreated_shouldHaveNoEffect_whenMessageWasProcessed` | R-03 |
| CA-HT04.3 (solo dos datos) | La réplica guarda solo `usuarioId` y `fechaNacimiento` | REQ-RF-02, sección 9 | `BirthDateReplicaSchemaIT.table_shouldHaveOnlyIdentityAndBirthDate` | — |
| CA-HT04.3 (log) | La fecha no se escribe en el log | REQ-NF-RF-01 | `BirthDateReplicationIT.accountCreated_shouldNotLogBirthDateNorEmail_whenProcessed` | — |
| CA-HT04.3 (fallidos) | Un mensaje que falla de forma repetida va a la cola de fallidos | REQ-RF-10 a 13 | `BirthDateReplicationIT.accountCreated_shouldGoToDeadLetterQueue_whenPayloadIsInvalid` (y los demás casos inválidos de la sección 10) | R-04, R-05 |
| CA-HT04.4 | `cuenta.eliminada` borra la fecha | REQ-RF-06, 07 | `BirthDateReplicationIT.accountDeleted_shouldRemoveBirthDate_whenPresent` | R-06 |
| HT-04 «Contrato» | Exchange, clave y cola exactos; carga compatible con el JSON Schema v1 (IOP-01) | REQ-RF-08, 09, REQ-NF-RF-06 | `AccountEventsTopologyIT.topology_shouldDeclareContractNames_whenStarted`, `AccountCreatedPayloadV1Test.schemaExample_shouldDeserialize_whenReadWithPayloadRecord` | R-01 |

## 4. Comportamiento antes y después

- **Antes:** con un broker local y `2a54ca7`, publicar en `cuentas.events` con clave `cuenta.creada` no llega a ninguna cola de Perfil
  (no hay binding) y no hay tabla donde guardar: la prueba `accountCreated_shouldStoreBirthDate_whenEventIsValid`, escrita primero, falla
  («relation "fecha_nacimiento_usuario" does not exist» o tiempo agotado esperando la fila) y la petición R-02 de Postman falla con
  «queue perfil.cuenta-creada not found» (404 del API de administración). Además `docker compose up` termina con `ACCESS_REFUSED` en el
  log de Perfil. La salida real se pega en el PR antes de implementar.
- **Después:** la fila aparece con `2008-03-15`, el mismo mensaje repetido 20 veces deja una fila en la réplica y una en el Inbox, los
  inválidos terminan en la cola de fallidos y `cuenta.eliminada` borra la fecha.

## 5. Contratos que consume

**`cuenta.creada` versión 1** (lo publica Cuentas; contrato completo en el repositorio de Cuentas, `docs/eventos/cuenta-creada-v1.md` y
`docs/eventos/cuenta-creada-v1.schema.json`, y resumido aquí para que esta spec se baste sola):

```json
{ "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8", "email": "ana.perez@ejemplo.test",
  "fechaNacimiento": "2008-03-15", "creadaEn": "2026-10-09T15:04:05.123Z" }
```

Exchange `cuentas.events` (topic), clave `cuenta.creada`, `content_type` `application/json`. Los metadatos viajan en las propiedades
AMQP, no en el cuerpo: `message_id` (UUID, identifica el evento y es la clave del Inbox), `type` (`cuenta.creada`), `x-event-version`
(`1`), `app_id` (`cameia-cuentas`), `timestamp` y `correlation_id`. Entrega al menos una vez y sin orden. `usuarioId` es el
`firebaseUid`. Perfil lee **solo** `message_id`, `usuarioId` y `fechaNacimiento`: el registro de la carga no declara `email` ni
`creadaEn`, que se ignoran y nunca se guardan ni se registran.

**`cuenta.eliminada` versión 1** (lo publicará CM-179 y la tarea de HU-1.9; nota técnica de HU-1.9): `{"usuarioId": "…", "eliminadaEn": "…"}`,
clave `cuenta.eliminada`, con las mismas propiedades AMQP. Perfil lee solo `message_id` y `usuarioId`.

## 6. Requisitos funcionales (EARS)

### Réplica

- **REQ-RF-01.** Cuando llegue un `cuenta.creada` válido cuyo `message_id` no esté en el Inbox, el servicio debe guardar en
  `fecha_nacimiento_usuario` una fila con `firebase_uid` = `usuarioId` y `fecha_nacimiento` = `fechaNacimiento`.
- **REQ-RF-02.** La réplica debe guardar solo esos dos datos.
- **REQ-RF-03.** El servicio debe aceptar `usuarioId` de 1 a 128 caracteres (tras comprobar que no es nulo ni está en blanco; no se
  recorta) y `fechaNacimiento` como fecha ISO `yyyy-MM-dd` real, no posterior a «hoy» en UTC.
- **REQ-RF-04.** Si ya existe una fila para ese `usuarioId` (otro evento con distinto `message_id` para el mismo Usuario), el servicio no
  debe cambiarla ni fallar: anota el `message_id` en el Inbox, confirma el mensaje (`ack`) y registra en `INFO` «fecha ya replicada».
- **REQ-RF-05.** El mensaje se confirma solo después de que la transacción de base de datos (Inbox y réplica) termina con éxito.
- **REQ-RF-06.** Cuando llegue un `cuenta.eliminada` válido cuyo `message_id` no esté en el Inbox, el servicio debe borrar la fila de ese
  `usuarioId` y anotar el `message_id`.
- **REQ-RF-07.** Si no hay fila para ese `usuarioId`, el borrado no debe fallar: se anota el `message_id`, el mensaje se confirma y se
  registra en `INFO`.

### Inbox

- **REQ-RF-14.** En la misma transacción que el efecto sobre la réplica, el servicio debe insertar en `evento_procesado` el `message_id`,
  el tipo (`cuenta.creada` o `cuenta.eliminada`) y la hora del `Clock` en UTC, con `INSERT … ON CONFLICT (message_id) DO NOTHING`.
- **REQ-RF-15.** Si el `message_id` ya estaba en el Inbox, el servicio no debe tocar la réplica: confirma el mensaje y registra en `INFO`
  «mensaje ya procesado» con `messageId`.
- **REQ-RF-16.** Las filas del Inbox se conservan sin plazo (decisión de Paula, 9-oct): no llevan datos personales y son unas dos por
  Usuario en toda su vida; así cualquier reenvío tardío desde la cola de fallidos se reconoce como duplicado.

### Topología

- **REQ-RF-08.** Al conectarse, el servicio debe declarar (de forma idempotente): exchange `cuentas.events` (topic, durable); exchange de
  fallidos `perfil.dlx` (direct, durable); colas durables `perfil.cuenta-creada` y `perfil.cuenta-eliminada` con argumentos
  `x-dead-letter-exchange` = `perfil.dlx` y `x-dead-letter-routing-key` = el nombre de la cola; colas durables
  `perfil.cuenta-creada.dlq` y `perfil.cuenta-eliminada.dlq` enlazadas a `perfil.dlx` con el nombre de su cola de origen.
- **REQ-RF-09.** Los enlaces deben ser `cuentas.events` → `perfil.cuenta-creada` con `cuenta.creada` y `cuentas.events` →
  `perfil.cuenta-eliminada` con `cuenta.eliminada`. La cola `cuenta-eliminada` y su enlace con `cameia.events` dejan de declararse.

### Mensajes que fallan

- **REQ-RF-10.** Si el cuerpo no es JSON, no es un objeto, le falta `usuarioId` o `fechaNacimiento`, no cumplen REQ-RF-03, o el
  `message_id` falta o no es un UUID, el mensaje debe ir a la cola de fallidos sin cambiar la réplica ni el Inbox.
- **REQ-RF-11.** Si procesar un mensaje válido falla por una causa técnica (base de datos), el servicio debe reintentarlo hasta 3 veces en
  total con esperas de 1 s y 2 s y, si sigue fallando, enviarlo a la cola de fallidos.
- **REQ-RF-12.** Cada mensaje enviado a fallidos se registra una vez en `WARN` (inválido) o `ERROR` (técnico, con traza) con
  `messageId`, la cola y el código de la causa (`MESSAGE_ID_INVALID`, `USER_ID_INVALID`, `BIRTH_DATE_REQUIRED`,
  `BIRTH_DATE_IN_THE_FUTURE`, `PAYLOAD_INVALID_FORMAT` o la clase simple del fallo técnico), nunca con la carga.
- **REQ-RF-13.** El servicio nunca devuelve un mensaje a su propia cola (`default-requeue-rejected=false`): un mensaje venenoso no puede
  bloquearla.
- **REQ-RF-17.** Un mensaje que incumple el contrato (REQ-RF-10) debe ir a la cola de fallidos al primer intento, sin reintentos y con un
  solo registro; solo los fallos técnicos se reintentan (REQ-RF-11). Se logra excluyendo `AmqpRejectAndDontRequeueException` de la
  política de reintento con un `RabbitListenerRetrySettingsCustomizer`; la propiedad de reintentos de Spring Boot 4 es `max-retries`
  (reintentos posteriores al primer intento: 2), no `max-attempts`.

### Lectura para HU-2.4

- **REQ-RF-20.** El puerto `BirthDateReplica.findBirthDate(FirebaseUid)` debe devolver la fecha replicada o vacío si no hay fila.
- **REQ-RF-21.** Cuando un caso de uso necesite la fecha y la réplica no la tenga, debe lanzar `BirthDateUnavailableException`, que el
  manejador traduce a la sección 11 (la llamada la escribe CM-274).

## 7. Requisitos no funcionales

- **REQ-NF-RF-01 (privacidad).** Ningún log de Perfil escribe la fecha de nacimiento, el correo ni el cuerpo del mensaje; sí
  `firebaseUid` y `messageId`.
- **REQ-NF-RF-02 (deserialización segura).** El convertidor JSON toma el tipo del parámetro del consumidor e ignora el encabezado
  `__TypeId__`; un mensaje con `__TypeId__` de otra clase no instancia esa clase.
- **REQ-NF-RF-03 (consumo).** `prefetch` 10 y un consumidor por cola: la réplica no necesita paralelismo y así se limita la memoria.
- **REQ-NF-RF-04 (configuración).** Conexión al broker solo por `SPRING_RABBITMQ_*`; `.env.example` sin contraseñas.
- **REQ-NF-RF-05 (despliegue).** El consumo se puede apagar en una instancia con `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false`
  (propiedad estándar de Spring Boot; sin código).
- **REQ-NF-RF-06 (contrato versionado).** Una copia literal del JSON Schema `cuenta-creada-v1.schema.json` de Cuentas queda en
  `src/test/resources/contracts/` y una prueba deserializa su `examples[0]` con `AccountCreatedPayloadV1`: si Cuentas cambia la v1 de
  forma incompatible, la copia y la prueba lo muestran en la revisión.

## 8. Diseño

| Pieza | Paquete | Por qué |
|---|---|---|
| `BirthDateReplica` | `domain/port` | Puerto de lectura y escritura de la réplica; lo usa también CM-274 |
| `ProcessedMessageInbox` | `domain/port` | Puerto del Inbox: `boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt)` (`true` si el mensaje es nuevo) |
| `BirthDateUnavailableException` | `domain/exception` | 503 de CA-2.4.36 y 2.4.52; hereda de `BusinessException` |
| `InvalidAccountEventException` | `domain/exception` | Mensaje con datos que no cumplen el contrato; no es de HTTP y no hereda de `BusinessException` (no tiene `ErrorCode` ni estado); lleva `reason` (`MESSAGE_ID_INVALID`, `USER_ID_INVALID`, `BIRTH_DATE_REQUIRED`, `BIRTH_DATE_IN_THE_FUTURE`) |
| `ReplicateBirthDateCommand`, `RemoveBirthDateCommand` | `application/command` | `record(String messageId, String userId, LocalDate birthDate)` y `record(String messageId, String userId)` |
| `BirthDateReplicaAppService` | `application/service` | Valida, registra en el Inbox y guarda o borra, todo en una transacción (`@Transactional`) |
| `ClockConfig` | `infrastructure/config` | `Clock.systemUTC()` como bean (no existe ninguno) |
| `BirthDateReplicaEntity`, `BirthDateReplicaJpaRepository`, `BirthDateReplicaRepositoryAdapter` | `infrastructure/persistence/*` | Patrón de tres piezas del repo |
| `ProcessedMessageEntity`, `ProcessedMessageJpaRepository`, `ProcessedMessageInboxAdapter` | `infrastructure/persistence/*` | Patrón de tres piezas del repo |
| `AccountCreatedPayloadV1`, `AccountDeletedPayloadV1` | `infrastructure/messaging/payload` | Registros con solo los campos que Perfil usa |
| `AccountEventsRabbitConfig` | `infrastructure/messaging/config` | Topología de la sección 6 |
| `AccountCreatedListener`, `AccountDeletedListener` (reescrito) | `infrastructure/messaging/consumer` | Leen `message_id` de las propiedades y el cuerpo, arman el comando y traducen los inválidos a rechazo sin reintento |
| `AccountEventsRetryConfig` | `infrastructure/messaging/config` | Excluye del reintento los rechazos por contrato (REQ-RF-17) |
| `AccountEventsErrorHandler` | `infrastructure/messaging/consumer` | Registra una vez, con código de causa, los mensajes que no se pueden leer (`PAYLOAD_INVALID_FORMAT`) y los fallos técnicos; decide rechazo sin reintento o reintento |

**Transacciones.** `@Transactional` va en el servicio de aplicación; los adaptadores llevan `@Transactional` en sus métodos, como
`ProfessionalProfileRepositoryAdapter`, y se unen a la transacción del servicio.

**Orden dentro de la transacción.** (1) validar el mensaje (un inválido no toca el Inbox, así se puede reenviar tras corregirlo); (2)
`registerIfAbsent` en el Inbox; si devuelve `false`, terminar (REQ-RF-15); (3) efecto en la réplica (`saveIfAbsent` o `delete`).

**Reutilización.** `FirebaseUid` (tope 128) se usa para el tipo del puerto; `BusinessException`, `ErrorCatalog` y `ApiExceptionHandler`
para el 503 (sin cambiar el manejador); `RabbitConfig` conserva lo ajeno a esta CM. Se crean los dobles `InMemoryBirthDateReplica` e
`InMemoryProcessedMessageInbox` en pruebas (no existe ninguno); CM-274 reutiliza el primero.

## 9. Base de datos (migración `V5__replica_fecha_nacimiento.sql`)

```sql
CREATE TABLE fecha_nacimiento_usuario (
    firebase_uid     VARCHAR(128) NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    CONSTRAINT pk_fecha_nacimiento_usuario PRIMARY KEY (firebase_uid),
    CONSTRAINT ck_fecha_nacimiento_usuario_firebase_uid CHECK (btrim(firebase_uid) <> '')
);

CREATE TABLE evento_procesado (
    message_id   UUID        NOT NULL,
    tipo         VARCHAR(32) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_evento_procesado PRIMARY KEY (message_id),
    CONSTRAINT ck_evento_procesado_tipo CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'))
);
```

- `firebase_uid` mide 128 = `FirebaseUid.MAX_LENGTH` y es el mismo nombre de columna que en `perfil_profesional`. La clave primaria crea el
  único índice que hace falta (lectura y borrado por `firebase_uid`).
- `evento_procesado` no guarda el `usuarioId` ni ningún dato personal. `tipo` mide 32 (el valor más largo, `cuenta.eliminada`, tiene 16);
  el `CHECK` admite solo los dos tipos de esta CM: un tipo nuevo se agrega con su propia migración. `procesado_en` lo pone la aplicación
  con el `Clock` (sin `DEFAULT`, para poder fijarlo en pruebas).
- Sin claves foráneas: los datos son de otro servicio.
- **Número:** `V5`, porque el bloque P1 de esta CM va antes del bloque 5 de CM-274 (decisión P-09). La tarjeta T-P1.1 comprueba en
  `origin/develop` que `V5` sigue libre; si no, toma el siguiente libre y lo anota en el PR.
- Prueba de la migración con filas en `perfil_profesional` (`BirthDateReplicaSchemaIT`).

## 10. Validación por campo del mensaje (R2) y casos borde

| Campo | Caso | Valor literal | Resultado | Código de causa | Prueba |
|---|---|---|---|---|---|
| `message_id` | ausente | sin propiedad | fallidos | `MESSAGE_ID_INVALID` | `accountCreated_shouldGoToDeadLetterQueue_whenMessageIdIsMissing` |
| `message_id` | no es UUID | `"abc"`, `""` | fallidos | `MESSAGE_ID_INVALID` | `recordAccountCreated_shouldReject_whenMessageIdIsNotUuid` (tabla parametrizada) |
| `message_id` | repetido | el mismo UUID 20 veces | 1 fila en la réplica y 1 en el Inbox; 19 `INFO` «mensaje ya procesado» | — | `accountCreated_shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times` |
| cuerpo | no es JSON | `no es json` | fallidos | `PAYLOAD_INVALID_FORMAT` | `accountCreated_shouldGoToDeadLetterQueue_whenBodyIsNotJson` |
| cuerpo | arreglo | `[]` | fallidos | `PAYLOAD_INVALID_FORMAT` | `..._whenBodyIsArray` |
| cuerpo | vacío | `` (0 bytes) | fallidos | `PAYLOAD_INVALID_FORMAT` | `..._whenBodyIsEmpty` |
| cuerpo | campos extra | `email`, `creadaEn`, `rol: "ADMIN"` | se ignoran; fila creada | — | `accountCreated_shouldIgnoreExtraFields_whenPresent` |
| cuerpo | `__TypeId__` = `java.util.HashMap` | encabezado | se procesa como el registro esperado | — | `accountCreated_shouldIgnoreTypeIdHeader_whenPresent` |
| `usuarioId` | ausente / `null` | — | fallidos | `USER_ID_INVALID` | `..._whenUserIdIsMissing`, `..._whenUserIdIsNull` |
| `usuarioId` | vacío / espacios / tabulador | `""`, `"   "`, `"\t"` | fallidos | `USER_ID_INVALID` | tabla parametrizada en `BirthDateReplicaAppServiceTest` |
| `usuarioId` | 128 caracteres | 128 `a` | fila creada | — | `recordAccountCreated_shouldAccept_whenUserIdHas128Characters` |
| `usuarioId` | 129 caracteres | 129 `a` | fallidos | `USER_ID_INVALID` | `..._whenUserIdHas129Characters` |
| `usuarioId` | tipo número | `12345` | **Verificación previa de Backend (V-1):** depende de la coerción de Jackson 3. Si convierte el número a texto, se acepta (`"12345"`, 5 caracteres válidos); si no, va a fallidos con `PAYLOAD_INVALID_FORMAT`. La prueba fija el resultado real y esta fila se corrige en el mismo PR | — / `PAYLOAD_INVALID_FORMAT` | `accountCreated_shouldAcceptNumericUserId_whenSent` |
| `usuarioId` | objeto | `{"a":1}` | fallidos | `PAYLOAD_INVALID_FORMAT` | `..._whenUserIdIsObject` |
| `fechaNacimiento` | ausente / `null` | — | fallidos | `BIRTH_DATE_REQUIRED` | `..._whenBirthDateIsMissing` |
| `fechaNacimiento` | formato | `"15/03/2008"`, `"2008-3-15"`, `"2008-03-15T00:00:00Z"` | fallidos | `PAYLOAD_INVALID_FORMAT` | tabla parametrizada en `BirthDateReplicationIT` |
| `fechaNacimiento` | calendario inválido | `"2008-02-30"`, `"2007-02-29"` | fallidos | `PAYLOAD_INVALID_FORMAT` | ídem |
| `fechaNacimiento` | bisiesto | `"2008-02-29"` | fila creada | — | `accountCreated_shouldStoreLeapDay_whenBornOnFebruary29` |
| `fechaNacimiento` | hoy (reloj fijo 2026-10-09) | `"2026-10-09"` | fila creada (la edad no es regla de Perfil) | — | `recordAccountCreated_shouldAccept_whenBirthDateIsToday` |
| `fechaNacimiento` | mañana | `"2026-10-10"` | fallidos | `BIRTH_DATE_IN_THE_FUTURE` | `recordAccountCreated_shouldReject_whenBirthDateIsTomorrow` |
| `fechaNacimiento` | tipo número | `20080315` | fallidos | `PAYLOAD_INVALID_FORMAT` | `..._whenBirthDateIsNumber` |
| `cuenta.eliminada` `usuarioId` | mismos casos de presencia y longitud | | fallidos | `USER_ID_INVALID` | `accountDeleted_shouldGoToDeadLetterQueue_whenUserIdIsBlank` |
| `cuenta.eliminada` `message_id` | ausente o no UUID | | fallidos | `MESSAGE_ID_INVALID` | `recordAccountDeleted_shouldReject_whenMessageIdIsNotUuid` |
| cuerpo | grande | `usuarioId` de 1 048 576 caracteres | fallidos (longitud) | `USER_ID_INVALID` | `accountCreated_shouldGoToDeadLetterQueue_whenUserIdIsHuge` |

La edad (18 a 110 años) no se vuelve a validar en Perfil: es regla de Cuentas y ya se aplicó al registrarse. Perfil solo protege lo
que haría absurda una regla de HU-2.4 (fecha futura).

**Otros casos borde:**

| Grupo | Caso | Cubierto por |
|---|---|---|
| Duplicados | el mismo mensaje 20 veces (FIA-02); dos mensajes distintos del mismo `usuarioId` con fechas distintas (se conserva la primera; INFO «fecha ya replicada»; el segundo `message_id` queda en el Inbox) | `BirthDateReplicaAppServiceTest`, `BirthDateReplicationIT` |
| Estado | eliminar lo que no existe; el mismo `cuenta.eliminada` dos veces (el segundo no tiene efecto por el Inbox) | REQ-RF-07, 15; `accountDeleted_shouldAck_whenAbsent`, `accountDeleted_shouldHaveNoEffect_whenMessageWasProcessed` |
| Orden | `cuenta.eliminada` antes que `cuenta.creada` del mismo usuario | Riesgo residual documentado en el ADR: el Inbox identifica mensajes, no Usuarios, así que la fila quedaría hasta otra eliminación. Entre los dos eventos pasan días (purga a las 168 h, eliminación a los 30 días) y la cola de fallidos solo se reenvía a mano, con la comprobación previa del ADR. Cerrarlo exigiría guardar el identificador del Usuario eliminado, lo que contradice la minimización de datos |
| Concurrencia | dos consumidores con el mismo mensaje o el mismo `usuarioId` | `ON CONFLICT DO NOTHING` en las dos tablas: `ProcessedMessageInboxAdapterIT.registerIfAbsent_shouldReturnTrueOnce_whenCalledConcurrently` y `BirthDateReplicaRepositoryAdapterIT.saveIfAbsent_shouldInsertOnce_whenCalledConcurrently` (2 hilos con `CountDownLatch`) |
| Falla parcial | base caída al guardar; falla entre el Inbox y la réplica | Una sola transacción: si la réplica falla, el Inbox se revierte y el reintento procesa el mensaje completo. Prueba: `BirthDateReplicaAppServiceIT.recordAccountCreated_shouldRollBackInbox_whenReplicaFails`; `AccountCreatedListenerTest.onMessage_shouldPropagateTechnicalFailure_whenDatabaseFails` |
| Caída entre commit y ack | el consumidor muere tras el commit y antes del `ack` | El broker reentrega; el Inbox lo reconoce y no hay efecto (FIA-03): `BirthDateReplicationIT.accountCreated_shouldHaveNoEffect_whenRedeliveredAfterCommit` |
| Dependencias | broker caído al arrancar | La aplicación arranca y el contenedor de consumidores reintenta la conexión (comportamiento de Spring AMQP); `PerfilApplicationIT` sigue en verde sin broker |
| Propiedad, sin identidad, paginación, `Content-Type` HTTP | No aplican: no hay endpoint nuevo; la identidad del mensaje es la de Cuentas, autenticada por las credenciales del broker |

## 11. Errores

| Código | HTTP | Mensaje | Excepción | Cuándo | Prueba |
|---|---|---|---|---|---|
| `BIRTH_DATE_UNAVAILABLE` | 503 | «Ocurrió un error. Inténtalo de nuevo.» (literal de CA-2.4.36 y 2.4.52, RT-05) | `BirthDateUnavailableException` | La réplica aún no tiene la fecha de un Usuario que agrega una experiencia o formación con fechas | `ApiExceptionHandlerTest.birthDateUnavailable_shouldReturn503_whenThrown` (estado, `application/problem+json;charset=UTF-8`, `code`, `detail`, `requestId`, sin `errors`, registro en `ERROR` sin traza), `ErrorCatalogTest` (fila presente) |

Título del catálogo: «Servicio no disponible». Se registra una vez en `ERROR`, **sin traza** (no hay causa técnica que trazar), con
`code`, `requestId` y `firebaseUid`: el estándar pide `ERROR` para todo 5xx (alguien debe actuar: la réplica no llegó) y Cuentas ya lo
hace con `DEPENDENCY_UNAVAILABLE`. Los rechazos 4xx siguen en `WARN`. `ApiExceptionHandler.reject` elige el nivel por el estado: `>= 500`
→ `ERROR`; si no, `WARN`. Es la decisión D19 de CM-274; la implementa esta CM porque es la que introduce el 503 (CM-274 cambia la
creación del perfil a 409 `PROFILE_CREATION_IN_PROGRESS`, así que tras ella este es el único 503 de negocio de Perfil; si esta CM llega
antes, `PROFILE_CREATION_TIMEOUT` también pasa a `ERROR`, que es lo que pide el estándar).

Rutas al manejador genérico: ninguna nueva (no hay endpoint nuevo). Los fallos del consumidor no llegan a HTTP.

## 12. Seguridad (ASVS N1 y OWASP API Top 10)

- **API3 / datos mínimos:** el registro de la carga no declara `email` ni `creadaEn`; nunca se enlazan ni se guardan. El Inbox no guarda
  datos personales.
- **API10 / consumo inseguro:** se valida todo lo que llega de Cuentas (sección 10) antes de usarlo.
- **Deserialización:** REQ-NF-RF-02.
- **API4:** `prefetch` 10, un consumidor; un mensaje inválido no se reintenta en bucle (REQ-RF-13).
- **Logs:** se corrige el `WARN` que escribía la carga completa (`AccountDeletedListener`).
- **Credenciales:** `SPRING_RABBITMQ_*`; en staging AMQPS (`SPRING_RABBITMQ_SSL_ENABLED=true`, SEG-04) y usuario con permisos solo sobre
  sus colas y exchanges (DevOps).

### 12.1 ASVS 5.0.0 nivel 1 (`cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`)

| ID | Aplica | Qué lo cumple | Prueba |
|---|---|---|---|
| 1.2.4 Consultas parametrizadas | Sí | `insertIfAbsent` de las dos tablas son SQL nativo con `:parámetros`; ninguna concatena (la matriz dice «cero `nativeQuery`»: desactualizada, hallazgo para DevOps vía Vela) | `BirthDateReplicaRepositoryAdapterIT`, `ProcessedMessageInboxAdapterIT` |
| 2.2.1 / 2.2.2 Validación en el servidor | Sí | Todo mensaje se valida (sección 10) antes de usarse: identificador de mensaje, longitud y blanco del uid, fecha real y no futura | Tabla de la sección 10 |
| 1.2.3 Deserialización controlada | Sí | `setAlwaysConvertToInferredType(true)`: el encabezado `__TypeId__` no elige la clase; el registro de la carga solo declara dos campos | `accountCreated_shouldIgnoreTypeIdHeader_whenPresent` |
| 12.2.1 TLS en conexiones externas | Sí | AMQPS en staging por `SPRING_RABBITMQ_SSL_ENABLED` (DevOps) | Pedido a DevOps; `.env.example` |
| 14.2.1 Datos sensibles fuera de la URL | Sí | La fecha solo viaja en el cuerpo del mensaje; no hay ruta nueva | — |
| 15.3.1 Solo los campos necesarios | Sí | La réplica guarda dos columnas y el Inbox, ninguna personal | `BirthDateReplicaSchemaIT.table_shouldHaveOnlyIdentityAndBirthDate`, `inbox_shouldHaveNoPersonalDataColumns` |
| 8.2.2 / 8.3.1 Acceso por dato | No aplica | Sin endpoint nuevo; la lectura de la réplica la hace CM-274 con la identidad del encabezado `X-User-Id` | En CM-274 |

### 12.2 OWASP API Security Top 10

No hay endpoint nuevo (la única respuesta HTTP nueva es el 503 que lanza CM-274). Por riesgo: API1/API5 no aplican (sin recurso expuesto);
API2 sin cambio; API3 el registro de la carga ignora todo campo no declarado (`accountCreated_shouldIgnoreExtraFields_whenPresent`); API4
`prefetch` 10, un consumidor y sin reintento de mensajes inválidos (`retry_shouldInvokeOnce_whenMessageBreaksContract`); API6 el Inbox hace
idempotente el flujo (20 repeticiones); API7 no aplica; API8 credenciales solo por variables y consumo apagable
(`SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP`); API9 el 503 queda en `docs/errores.md`; API10 se valida todo lo que llega de Cuentas
(sección 10). Pruebas negativas de SEG-02: no aplican porque el servicio no expone recursos por dueño en esta CM.

## 18. Riesgos aceptados y límites conocidos

| Riesgo | Decisión |
|---|---|
| `cuenta.eliminada` antes que `cuenta.creada` del mismo Usuario | Documentado en el ADR (sección 10, «Orden») |
| Usuarios cuya cuenta no tiene fecha de nacimiento no tendrán fila y recibirán 503 al agregar fechas | Aceptado: sin fecha no hay qué replicar; el conteo lo pide la parte 3 del pedido a DevOps y la spec de Cuentas (sección 18) |
| Un reenvío manual desde la cola de fallidos es un procedimiento manual | Descrito en el ADR; el Inbox reconoce los duplicados |
| El Inbox crece sin plazo (≈ 2 filas por Usuario) | Aceptado (decisión de Paula del 9-oct) |

## 19. Integración con las otras CM de Perfil

Orden de Perfil, en serie: **CM-279 P1 → CM-279 P2 → CM-274 → CM-54 → CM-66 → CM-67** (detalle en la spec de CM-274, sección 19). Esta CM abre el orden:

| Pieza compartida | Qué hace CM-279 |
|---|---|
| Migraciones | `V5__replica_fecha_nacimiento.sql` (P1). CM-274: V6 y V7; CM-66: V8; CM-67: V9 |
| `ErrorCode`, `ErrorCatalog`, `docs/errores.md` | Agrega `BIRTH_DATE_UNAVAILABLE` (P1). Quien siga agrega los suyos después |
| `ApiExceptionHandler.reject` | Elige `ERROR` o `WARN` por estado (P1); CM-274 y CM-67 ya cuentan con eso |
| `ClockConfig` | Lo crea P1; CM-274 lo reutiliza |
| `RabbitConfig` | P2 quita la cola `cuenta-eliminada` y su enlace y configura el convertidor; CM-67 cambia después el exchange de salida a `perfil.events` |
| Reintento y consumo | La propiedad `spring.rabbitmq.listener.simple.*` y el personalizador de reintento valen para todos los consumidores de Perfil; el Job `profile-events-relay` de CM-67 debe arrancar sin consumidores (`auto-startup: false`) |
| ADR | `0004` (P2); el de CM-274 es `0005` |
| Colección y entorno de Postman | P2 crea `docs/perfil-local.postman_environment.json` (con `base_url`) y la carpeta «Réplica de fecha de nacimiento (local)» |
| `PerfilApplication.main` | No se toca aquí; CM-67 lo extiende para su Job |

**Tamaño.** P1 ≈ 800 y P2 ≈ 850 líneas de código; la spec, el plan y las tarjetas suman ≈ 1100 líneas: van en un **PR 0 de solo
documentos** (como la spec de CM-36), de modo que el stack queda en 3 PR. Decidido por Paula el 9-oct-2026: sí, PR 0 de documentos.

## 20. Línea base verde

En `origin/develop` (con el PR 0 si ya está fusionado), antes de editar; salida pegada en el reporte de la primera tarjeta.

| Comprobación | Comando | Esperado |
|---|---|---|
| Herramientas | `java -version`; `docker version`; `npx newman --version` | JDK 21, Docker encendido, Newman 6 |
| Build y pruebas | `./mvnw.cmd -B clean verify` | `BUILD SUCCESS`, 0 fallos y 0 omitidas |
| Cobertura de partida | `target/site/jacoco/jacoco.csv` | Global anotado (no debe bajar) |
| Migraciones | `git ls-tree --name-only origin/develop src/main/resources/db/migration/` | V1 a V4 |
| Postman | `docker compose down -v`; `docker compose up -d --build`; Newman de la colección completa con las 77 peticiones | 0 fallos |

Trampa: si el puerto 5432 está ocupado, `$env:POSTGRES_PORT='55433'` antes de `docker compose up`.

## 21. Regla → evidencia (R1 a R14)

| Regla | Evidencia prevista | Estado en la spec |
|---|---|---|
| R1 | Regla 1 de las tarjetas; Javadoc, comentarios y logs en español | Cubierta |
| R2 | Sección 10 (cada campo del mensaje, con valores literales) | Cubierta |
| R3 | Sección 11; los fallos del consumidor no llegan a HTTP | Cubierta |
| R4 | Sección 9 y `BirthDateReplicaSchemaIT` | Cubierta |
| R5 | Sección 10 (otros casos borde) | Cubierta |
| R6 | Sección 12 | Cubierta |
| R7 | Sección 8; `ArquitecturaTest` | Cubierta |
| R8 | Javadoc en español, `docs/errores.md`, ADR 0004, `CLAUDE.md` | Cubierta |
| R9 | Secciones 3, 10 y 16 | Cubierta |
| R10 | `jacoco.csv` de las clases de la sección 8 | Cubierta |
| R11 | T-P2.6 y Newman completo | Cubierta |
| R12 | Sección 3 (HT-04 leída con `extraer_hu.py --id HT-04`) | Cubierta |
| R13 | Sección 15 | Cubierta |
| R14 | Sin push, PR ni Jira sin el sí de Paula | Cubierta |

## 22. Lista de verificación de la CM

| # | Comprobación | Cómo | Esperado |
|---|---|---|---|
| V-01 | Cada prueba de la sección 3 existe | `git grep -n "<método>" -- src/test` | Todas existen |
| V-02 | Antes y después | Primera prueba de T-P2.4 sobre P1 y luego sobre P2 | Falla y luego pasa, con la salida pegada |
| V-03 | Suite completa | `./mvnw.cmd -B clean verify` (Surefire y Failsafe) | Verde, 0 omitidas |
| V-04 | Réplica | `accountCreated_shouldStoreBirthDate_whenEventIsValid` | Fila con `2008-03-15` |
| V-05 | Idempotencia | `…shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times` | Una fila y una anotación |
| V-06 | Inválidos | `…shouldGoToDeadLetterQueue_whenPayloadIsInvalid` y `AccountEventsRetryIT` | Cola de fallidos, un solo intento y un solo registro |
| V-07 | Fallo técnico | `retry_shouldAttemptThreeTimes_whenDatabaseFails` | Exactamente 3 invocaciones y luego fallidos |
| V-08 | Eliminación | `accountDeleted_shouldRemoveBirthDate_whenPresent` | 0 filas |
| V-09 | Topología | `AccountEventsTopologyIT` | Nombres del contrato; sin la cola `cuenta-eliminada` |
| V-10 | 503 y niveles de log | `ApiExceptionHandlerTest` | 503 con la forma completa; `ERROR` sin traza para 5xx, `WARN` para 4xx |
| V-11 | Privacidad | `…shouldNotLogBirthDateNorEmail_whenProcessed` | Sin fecha, correo ni cuerpo |
| V-12 | Esquema | `BirthDateReplicaSchemaIT` | Cada restricción rechaza su fila; solo dos columnas |
| V-13 | Cobertura | `jacoco.csv` | ≥ 90 % por clase tocada; global no baja |
| V-14 | Postman | Newman completo con `docs/perfil-local.postman_environment.json` | 0 fallos (77 + las nuevas) |
| V-15 | Comentarios y dependencias | `git grep -n "CM-[0-9]" -- src`; `git diff origin/develop -- pom.xml` | Ninguno; vacío |
| V-16 | Tamaño | `git diff --shortstat <base>...<rama>` | ≤ 1000 por PR |
| V-17 | Revisiones | `/simplify`, `/code-review high`, `/security-review` | Hallazgos corregidos o descartados con razón |
| V-18 | De punta a punta con Cuentas | Sección 7 de las tarjetas de Cuentas | Fila en la base de Perfil |

## 23. Puerta de listo

| Condición | Estado |
|---|---|
| 1. Trazabilidad y atributos | Secciones 3 y 15 |
| 2. Matriz, casos borde, ruta al 500 y plan de seguridad | Secciones 10, 11 y 12 |
| 3. Contraste con `2a54ca7` y ensayo en seco | Hecho el 9-oct-2026: código real, `spring-rabbit-4.1.1.jar`, `spring-boot-amqp-4.1.1.jar`, `spring-core-7.0.9.jar` y la imagen de RabbitMQ; correcciones en `analisis/revision-spec_CM-279.md` |
| 4. Integración | Sección 19 |
| 5. Línea base | Sección 20 |
| 6. Lista de verificación | Sección 22 |
| 7. Dudas | Ninguna. El PR 0 de documentos quedó decidido (Paula, 9-oct) |

**Dictamen: LISTA PARA EJECUTAR.** La aprueba solo Paula.

## 13. Dependencias

| # | Qué | Quién | Bloquea |
|---|---|---|---|
| 1 | Broker de staging con AMQPS y opción A de topología (cada aplicación declara lo suyo) | Juan Diego Gomez, por tarea de Vela (pedido enviado, parte 3) | Prueba en staging |
| 2 | Instancia de Perfil que consuma con CPU siempre asignada (Cloud Run con solicitudes no mantiene CPU fuera de una petición) — pedido 10 | Juan Diego Gomez, por tarea de Vela | Que la réplica se llene en staging sin tráfico HTTP |
| 3 | Productor (`cuenta.creada`) y su JSON Schema | Mitad Cuentas de CM-279 | Prueba de punta a punta; la copia del esquema (REQ-NF-RF-06) se toma de su spec mientras no esté en `develop` |
| 4 | Productor de `cuenta.eliminada` | CM-179 | CA-HT04.4 de punta a punta (aquí se prueba publicando el mensaje literal) |
| 5 | Reglas de HU-2.4 con la réplica | CM-274, bloque 5, apilado sobre P1 | Terminado de HT-04 en staging (422 en CA-2.4.21 y 2.4.32, 503 en 2.4.36 y 2.4.52) |

Mientras no existan, se prueba publicando los mensajes literales del contrato en un RabbitMQ real (Testcontainers y compose).

## 14. Decisiones

| # | Decisión | Por qué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Réplica alimentada por evento | Perfil no depende de Cuentas en línea | Consulta síncrona a Cuentas | Product Owner, 8-oct (backlog v5) |
| P-01 | `usuarioId` es el `firebaseUid` | Es la identidad que Perfil recibe en `X-User-Id` | UUID de `cuenta`: otra columna y sin forma de buscar por el token | Sin alternativa razonable; Paula, 9-oct |
| P-02 | Metadatos en propiedades AMQP; cuerpo con solo los campos del CA | Contrato idéntico al backlog y metadatos donde AMQP los define | Sobre `{metadata, data}`: cambia la carga del backlog | Paula, 9-oct («lo más seguro») |
| P-07 | 3 intentos (1 s, 2 s); `perfil.dlx` con una cola de fallidos por cola | Se sabe qué tipo de evento falló y se reenvía sin mezclar | Una sola `perfil.dlq` | Paula, 9-oct |
| P-08 | Tabla Inbox `evento_procesado` en la misma transacción, sin plazo de borrado | IOP-04 del anexo («consumidores con Inbox; ack posterior al commit») y FIA-02 (cero efectos en 20 repeticiones); sin datos personales | Solo clave natural (no cumple IOP-04); Inbox con borrado a 30 días (sentencia extra por mensaje y no reconoce reenvíos tardíos); Job de limpieza (más infraestructura) | Paula, 9-oct |
| P-09 | El bloque P1 va antes del bloque 5 de CM-274, que usa `BirthDateReplica`, `InMemoryBirthDateReplica` y `BirthDateUnavailableException` | Sin código provisional; P1 no depende de RabbitMQ | CM-274 primero con un adaptador provisional que siempre responde «sin fecha» | Paula, 9-oct |
| P-10 | Código `BIRTH_DATE_UNAVAILABLE` | Un código por causa, como `PROFILE_CREATION_TIMEOUT` en Perfil y `DATABASE_UNAVAILABLE` en CM-290 | `DEPENDENCY_UNAVAILABLE`: ya no hay dependencia llamada | Precedente de CM-271, aceptado por Paula, 9-oct |
| P-11 | Los rechazos de negocio 5xx se registran en `ERROR` sin traza; los 4xx en `WARN` | Regla del estándar para los 5xx; Cuentas ya lo hace; el precedente de `WARN` (el 503 de creación de CM-271) desaparece porque la creación pasa a 409 | `WARN` para todo rechazo previsto | D19 de CM-274 (Paula pidió la mejor opción), 9-oct |
| P-14 | Migración `V5` | Consecuencia de P-09 | — | Paula, 9-oct |
| Idioma | Identificadores, nombres de método de prueba y códigos en inglés; Javadoc, comentarios, `@DisplayName`, logs, mensajes y documentos en español (R1) | Es la regla vigente y la que ya sigue todo el código de Perfil | Comentarios y logs en inglés | Paula, 9-oct |

## 15. Atributos de calidad de CAMEIA (anexo de restricciones y atributos de calidad)

AC-0001 (adecuación funcional de las funciones con IA) no aplica: esta CM no usa IA.

| Atributo | Métrica del anexo | Cómo lo cumple | Prueba o evidencia |
|---|---|---|---|
| AC-0002 Desempeño (DES-03: p95 de operaciones JSON propias ≤ 2 s) | El agregado de HU-2.4 lee la réplica local por clave primaria en lugar de llamar a Cuentas | Índice de la clave primaria; sin llamada síncrona | `BirthDateReplicaSchemaIT.primaryKey_shouldBeTheOnlyIndex`; plan de ejecución `Index Scan` pegado en el PR; la medición del p95 es de CM-274 |
| AC-0003 Seguridad (SEG-04: comunicaciones protegidas y cero datos sensibles en telemetría) | AMQPS en staging; 0 fechas, correos o cuerpos en logs | REQ-NF-RF-01, sección 12 | `BirthDateReplicationIT.accountCreated_shouldNotLogBirthDateNorEmail_whenProcessed`; configuración `SPRING_RABBITMQ_SSL_ENABLED` en el ADR |
| AC-0003 Seguridad (SEG-01: ASVS N1) | Deserialización acotada y entrada validada | REQ-NF-RF-02, sección 10 | `accountCreated_shouldIgnoreTypeIdHeader_whenPresent` y la tabla de la sección 10 |
| AC-0004 Fiabilidad (FIA-01: el fallo conserva el último estado confirmado) | Inválido, caída de la base y reinicio no dejan estado a medias | Una transacción para Inbox y réplica; `ack` tras el commit | `recordAccountCreated_shouldRollBackInbox_whenReplicaFails`, `onMessage_shouldPropagateTechnicalFailure_whenDatabaseFails` |
| AC-0004 Fiabilidad (FIA-02: cero efectos adicionales en 20 repeticiones del mismo evento) | 1 fila en la réplica y 1 en el Inbox tras 20 entregas | Inbox por `message_id` | `accountCreated_shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times` |
| AC-0004 Fiabilidad (FIA-06: recuperación de mensajería, reentrega y DLQ) | Topología recreable por la aplicación; reentrega sin efecto; fallidos sin bloqueo | Declaración idempotente; Inbox; `default-requeue-rejected=false` | `AccountEventsTopologyIT`, `accountCreated_shouldHaveNoEffect_whenRedeliveredAfterCommit`, casos de fallidos |
| AC-0006 Capacidad de interacción (INT-06: la persona identifica el estado y la acción ante un fallo técnico) | Mensaje «Ocurrió un error. Inténtalo de nuevo.» (literal de CA-2.4.36 y 2.4.52) con código estable para Frontend | Código `BIRTH_DATE_UNAVAILABLE` | `ApiExceptionHandlerTest.birthDateUnavailable_shouldReturn503_whenThrown` |
| Registro de los 5xx (estándar §6; DES-04 cuenta los 5xx propios) | 1 línea `ERROR` sin traza por cada 503 de negocio; 0 para los 4xx | `ApiExceptionHandler.reject` por estado | `ApiExceptionHandlerTest.rejection_shouldLogError_whenBusinessStatusIs5xx` y `rejection_shouldLogWarn_whenBusinessStatusIs4xx` |
| MAN-01 (cobertura > 70 %; meta de Backend ≥ 90 % de líneas y ramas en lo nuevo) | ≥ 90 % por clase de la sección 8 | Puertos y adaptadores; consumidores delgados | JaCoCo (`target/site/jacoco/jacoco.csv`) |
| MAN-02 (adaptadores con pruebas de éxito, formato inválido y timeout) | Cada adaptador nuevo con esos casos | Sección 10 y casos borde | `BirthDateReplicaRepositoryAdapterIT`, `ProcessedMessageInboxAdapterIT`, `BirthDateReplicationIT` |
| MAN-03 (cero accesos directos o FK entre bases de contextos) | Sin claves foráneas a datos de Cuentas | Sección 9 | `BirthDateReplicaSchemaIT` |
| IOP-01 (APIs y eventos con OpenAPI, AsyncAPI o JSON Schema versionado) | El consumidor prueba contra el esquema v1 | REQ-NF-RF-06 | `AccountCreatedPayloadV1Test.schemaExample_shouldDeserialize_whenReadWithPayloadRecord` |
| IOP-03 (topología asíncrona del MVP) | Exchange, claves y colas del contrato de HT-04 | REQ-RF-08, 09 | `AccountEventsTopologyIT` |
| IOP-04 (consumidores con Inbox; ack posterior al commit) | 100 % de los consumidores de esta CM | REQ-RF-05, 14, 15 | `BirthDateReplicationIT` (duplicados y reentrega) |

## 16. Pruebas (resumen; detalle en `tasks.md`)

Aplicación: `BirthDateReplicaAppServiceTest`. Consumidores: `AccountCreatedListenerTest`, `AccountDeletedListenerTest`. Contrato:
`AccountCreatedPayloadV1Test`. Persistencia: `BirthDateReplicaSchemaIT`, `BirthDateReplicaRepositoryAdapterIT`,
`ProcessedMessageInboxAdapterIT`, `BirthDateReplicaAppServiceIT`. Mensajería real: `AccountEventsTopologyIT`, `BirthDateReplicationIT`.
Web: `ApiExceptionHandlerTest`, `ErrorCatalogTest`. Arquitectura: `ArquitecturaTest`. En este repositorio Failsafe corre las `*IT` en
`verify`.

## 17. Preguntas respondidas

| # | Respuesta | Quién y cuándo |
|---|---|---|
| P-01, P-02, P-07, P-08, P-09, P-14 e idioma | Sección 14 | Paula, 9-oct-2026 |
| P-10 | Resuelta por el precedente de CM-271 (PR #57 a #74 de `cameia-perfil`) | Aceptado por Paula, 9-oct-2026 |
| P-11 | `ERROR` sin traza para los 5xx de negocio (D19 de CM-274) | 9-oct-2026 |
| Réplica con fecha completa o solo año y mes | Fecha completa: es lo que fija HT-04 («solo `usuarioId` y `fechaNacimiento`») | Revisión de atributos de CM-274, 9-oct-2026 |
| Plazo del Inbox | Sin plazo de borrado | Paula, 9-oct-2026 |

Preguntas abiertas: ninguna. Queda la verificación previa V-1 (sección 10), que fija la prueba.
