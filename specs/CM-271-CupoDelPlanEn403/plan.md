# Plan — CM-271: el cupo de Perfiles Profesionales del plan responde 403 `PLAN_LIMIT`

## 1. Enfoque

Retirar `PROFILE_LIMIT_REACHED` y agregar `PLAN_LIMIT` (403) en el mismo cambio: enumerado, catálogo, excepción, OpenAPI, `docs/errores.md`
(fila nueva y «Códigos retirados»), ADR 0003, Postman y pruebas. La lógica de creación no se toca.

## 2. Archivos

| Crear | Modificar | No tocar |
|---|---|---|
| `infrastructure/persistence/PlanLimitIT.java` | `domain/exception/ErrorCode.java`, `domain/exception/ProfileLimitReachedException.java`, `presentation/advice/ErrorCatalog.java`, `presentation/controller/ProfileController.java` (ejemplo y `@ApiResponse` del `POST`), `docs/errores.md`, `docs/adr/0003-bloqueo-de-creacion-de-perfil.md`, `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`; pruebas `ApiExceptionHandlerTest`, `ProfileControllerTest`, `ErrorCodeTest` si nombra el código | `ProfileCreationAppService` (solo su Javadoc `@throws` si cita el código), `ProfessionalProfileRepositoryAdapter`, migraciones |

## 3. Integración

Base: punta de CM-73. La sección «Códigos retirados» de `docs/errores.md` y el 409 `PROFILE_CREATION_IN_PROGRESS` del `POST /api/v1/profiles` los
trae CM-274 PR 1. ADR 0003: CM-274 PR 1 ya cambió la espera (2 s, 409); aquí solo el paso 6 y la consecuencia del cupo.

## 4. Riesgos

`cameia-web` resuelve hoy el cupo por estado (403 o 409): no se rompe. El aviso pide mapear `PLAN_LIMIT` por `code`.

## 5. Estimación

≈ 2 h de ejecución, más el envío del aviso a Frontend (Paula).
