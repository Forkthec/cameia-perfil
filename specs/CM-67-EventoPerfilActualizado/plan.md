# Plan — CM-67: evento `perfil.profesional-actualizado` sin `embedding` y CA-2.5.18 de punta a punta

Base: `origin/develop` `504f838` (árbol `1699baf6…`; sobre `9355f1a`, verificado el 11-oct). Rama `CM-67-evento-perfil-actualizado`. Spec: `spec.md` de
esta carpeta. Decisiones de Paula del 13-oct (spec §17): D-16 A, D-17 B, D-18 sin cambio de estado en Jira por ahora, D-19 A.

## 1. Cómo se aborda

1. **Contrato primero.** El esquema JSON v1, su documento y el registro de la carga se escriben y se prueban antes de tocar ningún caso
   de uso: la prueba de contrato fija nombres, tipos, obligatorios y límites contra las constantes del dominio.
2. **Guardar en la misma transacción.** El puerto de la tabla de salida suma `appendProfileUpdated`, que serializa en el adaptador (como
   Cuentas) y exige una transacción abierta (`MANDATORY`).
3. **Un solo punto de anuncio.** `ProfileEventsAppService.announceIfCompleted` decide (solo `COMPLETED`), arma el evento con el reloj y la
   correlación, lo guarda y registra el intento tras el commit con el relevo que ya existe. `ProfileAppService` lo llama desde un
   `saveAndAnnounce` privado en las once escrituras.
4. **Topología.** Perfil declara la cola de Entrevista y su enlace (D-16 = A), para que nada quede devuelto mientras Entrevista no existe.
5. **Integración real.** Un IT con PostgreSQL y RabbitMQ reales recorre finalizar, editar, rechazar, revertir, competir y recuperar.
6. **CA-2.5.18.** Una prueba de que la base rechaza un resumen de 2001 caracteres; la regla de finalización ya tiene su prueba (D-17 = B).
7. **Postman, documentación y cierre** con una verificación única sobre la punta.

## 2. Archivos

| Acción | Ruta (bajo `src/main/java/co/edu/unicauca/cameia/perfil/` salvo que se diga) | Tarjeta |
|---|---|---|
| Crear | `docs/eventos/perfil-profesional-actualizado-v1.schema.json` (raíz del repo) | T-1 |
| Crear | `docs/eventos/perfil-profesional-actualizado-v1.md` (raíz del repo) | T-1 |
| Crear | `domain/event/CorrelationId.java` | T-1 |
| Crear | `domain/event/ProfileUpdated.java` | T-1 |
| Reemplazar | `infrastructure/messaging/payload/ProfileUpdatedPayloadV1.java` | T-1 |
| Modificar | `domain/port/OutboxRepository.java` (método nuevo) | T-2 |
| Modificar | `infrastructure/persistence/repository/OutboxRepositoryAdapter.java` | T-2 |
| Crear | `domain/port/RequestCorrelation.java` | T-3 |
| Crear | `infrastructure/web/HttpRequestCorrelation.java` y `infrastructure/web/package-info.java` (D-19 = A; con B: `infrastructure/config/HttpRequestCorrelation.java`) | T-3 |
| Crear | `application/service/ProfileEventsAppService.java` | T-4 |
| Modificar | `application/service/ProfileAppService.java` (constructor, `saveAndAnnounce`, once escrituras) | T-4 |
| Modificar | `infrastructure/messaging/config/RabbitConfig.java` (D-16 = A) | T-5 |
| Crear (pruebas) | `CorrelationIdTest`, `ProfileUpdatedTest` (`domain/event`); `ProfileUpdatedPayloadV1Test`, `ProfileUpdatedEventContractTest` (`infrastructure/messaging/payload`); `HttpRequestCorrelationTest` (`infrastructure/web`); `ProfileEventsAppServiceTest` (`application/service`); `ProfileEventsEndToEndIT` (`infrastructure/messaging`) | T-1 a T-6 |
| Modificar (pruebas) | `ProfileAppServiceTest`, `OutboxRepositoryAdapterIT`, `ColumnLengthConsistencyIT` | T-2, T-4, T-6 |
| Modificar | `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` (carpeta nueva) | T-7 |
| Crear/Modificar | `docs/adr/0006-evento-perfil-profesional-actualizado.md`, `CLAUDE.md`, `README.md`, `docs/errores.md`, `docs/bitacora-ia/README.md`, `docs/bitacora-ia/02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md`, `specs/CM-67-finalizacion-perfil-v4/tasks.md` (nota del PR 3) | T-8 |
| No se tocan | Migraciones, `pom.xml`, `ErrorCode`, `ApiExceptionHandler`, controladores, DTO HTTP, `RabbitEventPublisher`, `OutboxRelayAppService`, `ProfileEventsRelayJobRunner`, `.env.example` | — |

## 3. Reutilización

| Existente | Uso |
|---|---|
| `OutboxRelayAppService.relayAfterCommit(OutboundEvent)` | El intento tras el commit se entrega a un ejecutor acotado (`profileEventsRelayExecutor`); ejecutor lleno = fila pendiente para el Job |
| `RabbitEventPublisher` | Propiedades AMQP, 500 ms, `mandatory`; sin cambios |
| `OutboxEventJpaRepository.insert` | Lo llama `appendProfileUpdated` |
| `CorrelationId` de Cuentas | Se copia (mismo código, paquete de Perfil): no se importa código de otro servicio |
| `MessagingIntegrationSupport` | Base del IT de eventos (contenedores compartidos) |
| `ApiTestClient`, `ReplicatedBirthDates` | IT de CA-2.5.18 |
| `ProfileAppServiceTest.completeProfile()` y `writeUseCases()` | Modelo de las pruebas parametrizadas nuevas |
| `AccountCreatedPayloadV1Test` | Modelo de la prueba de contrato (Jackson 3, `tools.jackson`) |
| Carpeta `CM-279 — Réplica de fecha de nacimiento (local)` de Postman | Modelo de peticiones a la API de administración de RabbitMQ |

## 4. Decisiones técnicas (detalle en la spec §6 y §17)

- Correlación por un puerto (`RequestCorrelation`), no por los comandos: once firmas intactas y la regla de ≤ 3 parámetros.
- Serialización en el adaptador de la tabla de salida, como Cuentas.
- `ProfileEventsAppService` aparte: `ProfileAppService` gana una dependencia, no tres; el nombre termina en `AppService` (`ArquitecturaTest`).
- `@Transactional(propagation = MANDATORY)` en `announceIfCompleted` y en `appendProfileUpdated`: si alguien lo llama fuera de una
  escritura, falla en vez de publicar un evento sin cambio.
- `updateSalaryExpectation` sigue con `repository.save`: la expectativa salarial no está en la carga.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Entrevista declara su cola con otros argumentos → `PRECONDITION_FAILED` al arrancar Entrevista | Los argumentos quedan en el contrato (`docs/eventos/…md`) y en la dependencia de CM-209 (spec §14) |
| Las pruebas que no levantan RabbitMQ intentan publicar contra `localhost:5672` | El intento falla rápido (conexión rechazada, `connection-timeout` 2 s) y solo deja la fila pendiente; si en la máquina corre el RabbitMQ de compose, se publica allí sin efecto en la prueba. Las pruebas que miran mensajes usan el contenedor de `MessagingIntegrationSupport` |
| Tamaño del PR (≈ 1 100 líneas) | Un PR por CM sin tope (regla vigente); se corrige el `CLAUDE.md` del repo (A-10); commits por capacidad para revisar uno a uno |
| Dos eventos del mismo perfil en el mismo milisegundo | Las escrituras se serializan con el bloqueo de la fila; el segundo lee el reloj después del commit del primero. Riesgo residual aceptado y escrito en el contrato: la carga es el estado completo y la siguiente escritura lo corrige |

## 6. Orden, commits y paralelismo

En serie, un commit por tarjeta (cada uno compila y pasa sus pruebas):

| Commit | Tarjeta | Mensaje |
|---|---|---|
| 1 | T-1 | `CM-67 \| feat(perfil): contrato v1 de perfil.profesional-actualizado sin embedding` |
| 2 | T-2 | `CM-67 \| feat(perfil): la tabla de salida guarda el evento del perfil en la transacción del cambio` |
| 3 | T-3 | `CM-67 \| feat(perfil): correlación del evento con el X-Request-Id de la petición` |
| 4 | T-4 | `CM-67 \| feat(perfil): toda escritura de un perfil activo anuncia el evento` |
| 5 | T-5 | `CM-67 \| feat(perfil): cola de Entrevista ligada a perfil.events y prueba con RabbitMQ real` |
| 6 | T-6 | `CM-67 \| test(perfil): la base rechaza un resumen de 2001 caracteres` |
| 7 | T-7 | `CM-67 \| test(perfil): carpeta de Postman del evento del perfil` |
| 8 | T-8 | `CM-67 \| docs(perfil): ADR del evento, catálogo, CLAUDE.md y bitácora` |

T-0 (base) y T-9 (cierre) no generan commit propio (T-0 agrega `evidencia-antes.md` al commit 8). Nada va en paralelo: cada tarjeta
usa lo de la anterior. Después de la CM: CM-296 tanda 1 y CM-290 salen de `develop` con esta fusionada.

## 7. Estimación

≈ 7 h de ejecución y verificación; ≈ 1 100 líneas: ≈ 450 de producción y contrato, ≈ 400 de pruebas, ≈ 150 de la carpeta de Postman y
≈ 100 de documentación.
