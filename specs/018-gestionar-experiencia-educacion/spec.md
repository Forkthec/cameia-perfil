# Spec — CM-18: Gestionar experiencia laboral y educación (HU-2.4)

> **Estado:** RETROACTIVO — implementado antes de adoptar SDD (07/09/2026).
> Spec reconstruido desde el código y los tests en develop.

---

## Contexto

Un candidato necesita registrar y eliminar sus experiencias laborales y estudios. Ambas colecciones son ilimitadas. La eliminación es por ID y no requiere confirmación de negocio — simplemente remueve el elemento.

---

## Requisitos — Experiencia laboral (notación EARS)

**REQ-1** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles/{id}/work-experiences` con los datos de la experiencia, el sistema agrega la experiencia a la colección del perfil y retorna 201 con el perfil completo.

**REQ-2** — Unwanted behavior
Si el perfil con `{id}` no existe, el sistema retorna 404.

**REQ-3** — Optional feature
Donde `employmentStatus` sea `ENDED`, el sistema exige que `endDate` esté presente; si falta, retorna 422.

**REQ-4** — Behavior
Cuando el sistema recibe `DELETE /api/v1/profiles/{id}/work-experiences/{expId}`, el sistema elimina la experiencia con ese ID y retorna 200 con el perfil actualizado.

---

## Campos — `AddWorkExperienceRequest`

| Campo | Tipo | Requerido |
|---|---|---|
| `company` | `String` | Sí |
| `position` | `String` | Sí |
| `description` | `String` | No |
| `startDate` | `String` | Sí |
| `endDate` | `String` | Solo si `employmentStatus = ENDED` |
| `employmentStatus` | `String` | `ACTIVE` / `ENDED` |
| `seniority` | `String` | `JUNIOR` / `SEMI_SENIOR` / `SENIOR` / `LEAD` |
| `provenance` | `String` | `MANUAL` / `AI` |

---

## Requisitos — Educación (notación EARS)

**REQ-5** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles/{id}/educations` con los datos del estudio, el sistema agrega la entrada educativa y retorna 201 con el perfil actualizado.

**REQ-6** — Optional feature
Donde `inProgress = true`, el sistema acepta `endDate = null` y marca el registro como en curso.

**REQ-7** — Behavior
Cuando `startDate` contiene solo el año (formato `"YYYY"`), el sistema lo normaliza internamente sin rechazar la solicitud.

**REQ-8** — Behavior
Cuando el sistema recibe `DELETE /api/v1/profiles/{id}/educations/{eduId}`, el sistema elimina la entrada educativa y retorna 200.

---

## Campos — `AddEducationRequest`

| Campo | Tipo | Requerido |
|---|---|---|
| `institution` | `String` | Sí |
| `degree` | `String` | No |
| `fieldOfStudy` | `String` | No |
| `level` | `String` | `BACHELOR` / `MASTER` / `DOCTORATE` / `TECHNICAL` / `OTHER` |
| `startDate` | `String` | Sí (`YYYY` o `YYYY-MM`) |
| `endDate` | `String` | Solo si `inProgress = false` |
| `inProgress` | `Boolean` | No (default `false`) |
| `provenance` | `String` | `MANUAL` / `AI` |

---

## Invariantes del dominio

- `WorkExperience` y `Education` son entidades de valor dentro del agregado — no tienen repositorio propio.
- Cada entrada recibe un UUID generado al crearla.

---

## Endpoints implementados

| Método | Ruta | Código OK |
|---|---|---|
| POST | `/api/v1/profiles/{id}/work-experiences` | 201 |
| DELETE | `/api/v1/profiles/{id}/work-experiences/{expId}` | 200 |
| POST | `/api/v1/profiles/{id}/educations` | 201 |
| DELETE | `/api/v1/profiles/{id}/educations/{eduId}` | 200 |

---

## Pruebas de referencia

- `WorkExperienceTest` — validación de fechas, estado ENDED sin endDate → 422
- `ProfileAppServiceTest` — agregar/remover experiencia y educación
- Postman: carpeta **CM-18** en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`
