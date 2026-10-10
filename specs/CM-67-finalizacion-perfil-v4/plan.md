# Plan — CM-67 (cameia-perfil)

Base: `origin/develop` con CM-274, CM-54 y CM-66 fusionados (última CM del orden de Perfil: CM-279 P1 → CM-279 P2 → CM-274 → CM-54 → CM-66 → **CM-67**; spec §19). Estado: pendiente de aprobación de Paula.

| PR | Rama | Base | Tarjetas | Líneas | Horas | Requisito previo |
|---|---|---|---|---|---|---|
| 0 | `CM-67-finalizacion-perfil-v4` (la de la spec, rebasada) | `develop` | — (spec, plan y tarjetas) | ≈ 650, solo documentos | 0,2 | Aprobación de Paula |
| 1 | `CM-67-finalizacion-perfil` | `develop` con CM-66 y el PR 0 | T-0 a T-6 | ≈ 760 (código ≈ 150, pruebas ≈ 330, Postman ≈ 280) | 3,5 | — |
| 2 | `CM-67-tabla-salida-eventos` | PR 1 | T-7 a T-9 | ≈ 640 | 3,3 | — |
| 3 | `CM-67-evento-perfil-actualizado` | PR 2 | T-10 a T-12 | ≈ 510 | 3 | **BLOQUEADO** solo por Q-67-1 (Vela, `embedding`); Q-67-2 resuelta (Paula, 9-oct) |

Si al empezar el PR 3 las dos respuestas no han llegado, los PR 1 y 2 se publican solos: el PR 2 deja la tabla, el relevo y el Job funcionando sin ningún productor (nada se publica todavía), lo que no rompe nada.

## Diseño

1. **Dominio.** `CompletionRequirement` (`enum` en el orden del CA). `ProfessionalProfile.getMissingRequirements()` devuelve `List<CompletionRequirement>`: `NAME` si el nombre es `null` o en blanco; `SUMMARY` si el resumen es `null`, en blanco o de más de `ProfessionalSummary.MAX_LENGTH` puntos de código; `EDUCATION`, `SKILLS`, `TARGET_ROLES` si la colección está vacía. `complete()` comprueba `COMPLETED` (409) antes que los requisitos (422).
2. **Presentación.** `ErrorCatalog.COMPLETION_REQUIREMENTS`: requisito → `field`, `code` y rótulo. `ApiExceptionHandler.handleBusiness`, para `IncompleteProfileException`, agrega `missingRequirements` (nombres del `enum`) y `errors` (desde la tabla) en el mismo orden.
3. **Bloqueo.** `completeProfile` ya usa `loadForUserForUpdate` (CM-274); aquí se prueba CA-2.5.22.
4. **Falla real.** `CompletionEndToEndIT` crea un disparador temporal que lanza una excepción al actualizar el estado de un perfil concreto y comprueba 500 y rollback.
5. **Evento (PR 2 y 3).** Tabla `evento_saliente` (V9) en la misma transacción que el cambio; `ProfileAppService.saveAndAnnounce` es el único punto que guarda un perfil y, si queda `COMPLETED`, agrega el evento; `OutboxRelayService` publica tras el commit (500 ms) y en un Job cada 5 min con `FOR UPDATE SKIP LOCKED`; el Job corre con el perfil `profile-events-relay` sin puerto HTTP ni consumidores; `RabbitEventPublisher` con confirmación. Detalle en la spec §18.

## Archivos

| PR | Crear | Modificar | No tocar |
|---|---|---|---|
| 1 | `domain/model/CompletionRequirement`; prueba `CompletionEndToEndIT` | `ProfessionalProfile`, `IncompleteProfileException`, `ApiExceptionHandler`, `ErrorCatalog`, `ErrorCode`, `ProfileController` (OpenAPI; se borra `requestReview`), `ProfileAppService` (se borra `requestReview`), `ApiErrorResponse`, `ErrorCatalogTest`, `ProfessionalProfileTest`, `ProfileControllerTest`, `ProfileWriteLockIT`, `docs/errores.md`, colección de Postman | migraciones, `.github/**` |
| 2 | `V9__evento_saliente.sql`, `domain/event/OutboundEvent`, `domain/port/{OutboxRepository, EventPublisher}`, `OutboxEventEntity`, `OutboxEventJpaRepository`, `OutboxRepositoryAdapter`, `RabbitEventPublisher`, `OutboxRelayService`, `ProfileEventsRelayJobRunner`, `application-profile-events-relay.yml`; pruebas `OutboxRepositoryAdapterIT`, `RabbitEventPublisherTest`, `OutboxRelayServiceTest`, `ProfileEventsRelayJobRunnerTest` | `RabbitConfig` (exchange `perfil.events`; sin la cola del consumidor), `application.yml` (confirmaciones), `PerfilApplication` (salida del Job); se borra `ProfileEventPublisher` | Las colas que consume Perfil (las maneja CM-279) |
| 3 | `docs/eventos/perfil-profesional-actualizado-v1.{schema.json,md}`, `domain/event/{ProfileUpdated, CorrelationId}`; pruebas `ProfileUpdatedEventContractTest`, `CorrelationIdTest`, `ProfileEventsEndToEndIT` | `ProfileUpdatedPayloadV1`, `ProfileAppService` (`saveAndAnnounce` en las once escrituras) | — |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El disparador de prueba queda en la base de otra prueba | Nombre único, borrado en `@AfterEach`; la condición filtra por el `firebase_uid` de la prueba |
| `cameia-web` usa `errors[].field` con nombres exactos | Los cinco `field` se prueban literal (`name`, `summary`, `education`, `skills`, `targetRoles`) |
| El Job consume las colas de Perfil o abre un puerto | `application-profile-events-relay.yml` con `web-application-type: none` y `auto-startup: false`, probado en `ProfileEventsRelayJobRunnerTest` |
| El contrato del evento cambia después de publicarlo | PR 3 no se ejecuta sin Q-67-1; versión 1 en `x-event-version` y en el nombre del esquema |

## Orden

PR 0 → PR 1 → PR 2 → PR 3 (con las dos respuestas), en serie después de CM-66.
