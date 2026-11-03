# Spec — CM-174: Verificación de propiedad en endpoints de perfil (anti-IDOR)

**Tarea Jira:** CM-174  
**Tipo:** Bug de seguridad (OWASP ASVS 5.0.0 §8.2.2 — IDOR/BOLA)  
**Fecha spec:** 2026-09-18  
**Autor:** Ana Sofía Arango  
**Fuente:** Hallazgo de Paula Muñoz — `18092026_arquitectura_hallazgo-idor-perfil.md`

---

## Contexto

`ProfileController` tiene 16 endpoints. Solo `POST /profiles` (crear perfil) lee `X-User-Id`
para asociar el perfil al usuario. Los otros 15 reciben el `{id}` del perfil por URL y operan
sobre él sin verificar que pertenezca al usuario autenticado. Un usuario con token válido puede
leer o modificar el perfil de cualquier otra persona si conoce su UUID.

El dato necesario para la corrección (`X-User-Id` = Firebase UID del usuario autenticado)
ya llega desde el Gateway en todos los requests de Caso A. No se necesita ningún cambio
en el Gateway.

---

## Requisitos (formato EARS)

### Comportamiento normal (ownership correcto)

**EARS-1 — Lectura propia:**
WHEN un usuario autenticado hace `GET /api/v1/profiles/{id}` con su propio `{id}`,
THE SYSTEM SHALL responder `200 OK` con el cuerpo del perfil, sin cambio de comportamiento.

**EARS-2 — Escritura propia:**
WHEN un usuario autenticado hace cualquier `PATCH`, `POST` o `DELETE` sobre sub-recursos
(`/skills`, `/target-roles`, `/educations`, `/work-experiences`, `/completion`,
`/review-requests`, `/salary-expectation`) de su propio perfil,
THE SYSTEM SHALL procesar la operación normalmente y responder con el código ya documentado
para ese endpoint (201, 200, etc.).

### Comportamiento de rechazo (perfil ajeno)

**EARS-3 — Acceso a perfil ajeno:**
WHEN un usuario autenticado hace cualquier request sobre `GET /api/v1/profiles/{id}`,
`PATCH /api/v1/profiles/{id}`, o cualquier sub-recurso de un perfil cuyo `firebaseUid`
no coincide con el `X-User-Id` del request,
THE SYSTEM SHALL responder `403 Forbidden` con `ProblemDetail` (título: `"Acceso denegado"`)
y NO modificar ni retornar datos del perfil ajeno.

**EARS-4 — Header ausente en endpoint con ownership:**
WHEN llega un request a cualquier endpoint que requiere ownership y el header `X-User-Id`
está ausente (la petición no pasó por el Gateway),
THE SYSTEM SHALL responder `401 Unauthorized` con `ProblemDetail`
(título: `"Identidad requerida"`).

> Nota: EARS-4 aplica también a los 15 endpoints afectados, no solo a `createProfile`.
> En producción el Gateway garantiza que `X-User-Id` siempre esté presente en Caso A,
> pero el microservicio debe rechazar peticiones directas que lo omitan.

### Invariantes

**EARS-5 — Sin cambio en `createProfile`:**
THE SYSTEM SHALL mantener el comportamiento actual de `POST /api/v1/profiles` (EARS-1 a EARS-4
no aplican a este endpoint porque ya usa `X-User-Id` para crear, no para verificar ownership).

**EARS-6 — Sin fuga de información:**
THE SYSTEM SHALL responder `403` (no `404`) cuando el perfil existe pero es ajeno, para que
el comportamiento sea consistente y no permita inferir si un UUID pertenece a otro usuario.

> Decisión: 403 sobre 404. Paula acepta ambos (§4 del hallazgo); se elige 403 porque
> en CAMEIA el UUID del perfil no es secreto — se comparte entre microservicios — y responder
> 404 crearía inconsistencias cuando el mismo UUID se use en llamadas internas.

---

## Endpoints afectados (15)

| Método | Ruta | Motivo |
|--------|------|--------|
| `GET` | `/api/v1/profiles/{id}` | Lee datos sensibles |
| `PATCH` | `/api/v1/profiles/{id}` | Modifica datos |
| `POST` | `/api/v1/profiles/{id}/work-experiences` | Modifica sub-recurso |
| `DELETE` | `/api/v1/profiles/{id}/work-experiences/{expId}` | Modifica sub-recurso |
| `POST` | `/api/v1/profiles/{id}/educations` | Modifica sub-recurso |
| `DELETE` | `/api/v1/profiles/{id}/educations/{eduId}` | Modifica sub-recurso |
| `PATCH` | `/api/v1/profiles/{id}/salary-expectation` | Modifica sub-recurso |
| `POST` | `/api/v1/profiles/{id}/skills` | Modifica sub-recurso |
| `DELETE` | `/api/v1/profiles/{id}/skills/{skillId}` | Modifica sub-recurso |
| `POST` | `/api/v1/profiles/{id}/review-requests` | Cambia estado |
| `POST` | `/api/v1/profiles/{id}/target-roles` | Modifica sub-recurso |
| `PATCH` | `/api/v1/profiles/{id}/target-roles/{roleId}` | Modifica sub-recurso |
| `DELETE` | `/api/v1/profiles/{id}/target-roles/{roleId}` | Modifica sub-recurso |
| `POST` | `/api/v1/profiles/{id}/completion` | Cambia estado |

---

## Archivos que cambian

| Archivo | Cambio |
|---------|--------|
| `domain/exception/ProfileAccessDeniedException.java` | **Nuevo** — excepción de dominio para 403 |
| `domain/exception/IdentityRequiredException.java` | **Nuevo** — excepción para 401 sin X-User-Id |
| `application/service/ProfileAppService.java` | Agregar `loadForUser(UUID, String)` y actualizar los 14 métodos afectados para recibir y verificar `uid` |
| `presentation/controller/ProfileController.java` | Agregar `@RequestHeader("X-User-Id") String uid` a los 15 endpoints afectados y pasarlo al service |
| `presentation/advice/ApiExceptionHandler.java` | Handlers para `ProfileAccessDeniedException → 403` e `IdentityRequiredException → 401` |
| `presentation/controller/ProfileControllerTest.java` | Tests nuevos: 403 en perfil ajeno para `GET` y `PATCH`; 401 sin header en al menos 2 endpoints |

---

## Fuera de alcance

- No se toca el Gateway (ya funciona correctamente).
- No se cambia la lógica de negocio de ningún endpoint.
- No se agrega autenticación OIDC entre microservicios (queda pendiente en specs/CM-104-correcciones-OIDC).
- No se agrega paginación ni filtros a ningún endpoint existente.
