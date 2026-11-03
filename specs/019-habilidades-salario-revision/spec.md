# Spec — CM-19: Habilidades, salario y solicitud de revisión (HU-2.5)

> **Estado:** RETROACTIVO — implementado antes de adoptar SDD (07/09/2026).
> Spec reconstruido desde el código y los tests en develop.

---

## Contexto

Un candidato necesita tres capacidades independientes para completar su perfil antes de enviarlo a revisión: registrar sus habilidades técnicas, declarar su expectativa salarial y, cuando considere que el perfil está completo, solicitar que el equipo CAMEIA lo revise. Esta última acción dispara una transición de estado irreversible hasta que el revisor lo devuelva.

---

## Requisitos — Habilidades (notación EARS)

**REQ-1** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles/{id}/skills` con `skillName`, `level` y `provenance`, el sistema agrega la habilidad a la colección del perfil y retorna 201 con el perfil completo.

**REQ-2** — Behavior
Cuando el sistema recibe `DELETE /api/v1/profiles/{id}/skills/{skillId}`, el sistema elimina la habilidad con ese ID y retorna 200.

**REQ-3** — Unwanted behavior
Si el perfil con `{id}` no existe, el sistema retorna 404.

---

## Campos — `AddSkillRequest`

| Campo | Tipo | Valores |
|---|---|---|
| `skillName` | `String` | Nombre libre de la habilidad |
| `level` | `String` | `BASIC` / `INTERMEDIATE` / `ADVANCED` / `EXPERT` |
| `provenance` | `String` | `MANUAL` / `AI` |

---

## Requisitos — Expectativa salarial (notación EARS)

**REQ-4** — Behavior
Cuando el sistema recibe `PATCH /api/v1/profiles/{id}/salary-expectation` con `amount` en `BigDecimal`, el sistema actualiza o establece la expectativa salarial del perfil y retorna 200.

**REQ-5** — Optional feature
Donde `amount` sea nulo, el sistema borra la expectativa salarial existente.

---

## Requisitos — Solicitud de revisión (notación EARS)

**REQ-6** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles/{id}/review-requests` y el perfil es completo (`name ≠ null AND summary ≠ null AND targetRoles ≠ vacío`), el sistema transiciona `status → IN_REVIEW` y retorna 201.

**REQ-7** — Unwanted behavior
Si el perfil no cumple `isComplete()`, el sistema rechaza la solicitud con 422 y `ProblemDetail.title = "Perfil incompleto"`.

**REQ-8** — State
Mientras el perfil está en `IN_REVIEW`, el sistema impide modificaciones de estado adicionales hasta que el revisor lo devuelva (`returnForRevision()`) o lo apruebe (`markAsReviewed()`).

---

## Invariantes del dominio

```
isComplete() := name != null AND summary != null AND !targetRoles.isEmpty()
```

- `requestReview()` lanza `IncompleteProfileException` si `isComplete()` es `false`.
- La transición `IN_REVIEW → COMPLETED` la realiza el revisor, no el candidato.

---

## Endpoints implementados

| Método | Ruta | Código OK |
|---|---|---|
| POST | `/api/v1/profiles/{id}/skills` | 201 |
| DELETE | `/api/v1/profiles/{id}/skills/{skillId}` | 200 |
| PATCH | `/api/v1/profiles/{id}/salary-expectation` | 200 |
| POST | `/api/v1/profiles/{id}/review-requests` | 201 |

---

## Pruebas de referencia

- `ProfileControllerTest#postSkills_returns201`
- `ProfileControllerTest#deleteSkill_returns200`
- `ProfileControllerTest#patchSalaryExpectation_returns200`
- `ProfileControllerTest#postReviewRequests_returns201`
- `ProfileControllerTest#postReviewRequests_returns422WhenProfileIncomplete`
- `ProfessionalProfileTest` — requestReview con perfil incompleto
- Postman: carpeta **CM-19** en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`
