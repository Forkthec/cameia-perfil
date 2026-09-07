# Spec — CM-17: Actualizar información general del perfil (HU-2.3)

> **Estado:** RETROACTIVO — implementado antes de adoptar SDD (07/09/2026).
> Spec reconstruido desde el código y los tests en develop.

---

## Contexto

Un candidato autenticado necesita completar o corregir su información general: nombre, resumen profesional y modalidad preferida de trabajo. Todos los campos son opcionales en cada llamada — `null` significa "no modificar ese campo".

---

## Requisitos (notación EARS)

**REQ-1** — Behavior
Cuando el sistema recibe `PATCH /api/v1/profiles/{id}` con un cuerpo JSON parcial, el sistema aplica únicamente los campos presentes (no nulos) y retorna 200 con el perfil actualizado.

**REQ-2** — Unwanted behavior
Si el perfil con `{id}` no existe, el sistema retorna 404 con `ProblemDetail`.

**REQ-3** — State
Mientras se actualiza al menos un campo, el sistema actualiza `updatedAt` al instante de la operación.

**REQ-4** — Optional feature
Donde se proporcione `name`, el sistema valida que no sea cadena vacía antes de persistir.

**REQ-5** — Optional feature
Donde se proporcione `provenance`, el sistema registra el origen del dato (`MANUAL` o `AI`).

---

## Campos del body (`UpdateProfileInfoRequest`)

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | `String` | Nombre completo del candidato |
| `summary` | `String` | Resumen profesional (antes `headline` — eliminado por SM) |
| `preferredModality` | `String` | `REMOTE`, `ON_SITE`, `HYBRID` |
| `provenance` | `String` | `MANUAL` o `AI` |

> **Nota:** El campo `headline` fue eliminado del modelo durante este sprint por decisión del SM. El atributo `summary` lo reemplaza.

---

## Invariantes del dominio

- Los métodos `updateName`, `updateSummary`, `updatePreferredModality`, `updateProvenance` del agregado son independientes — se pueden llamar por separado.
- `updateName(null)` es válido y borra el nombre (campo nullable).

---

## Endpoints implementados

| Método | Ruta | Código OK |
|---|---|---|
| PATCH | `/api/v1/profiles/{id}` | 200 |

---

## Pruebas de referencia

- `ProfileControllerTest#patchProfile_returns200WithUpdatedProfile`
- `ProfileAppServiceTest` — actualizar nombre, resumen
- Postman: carpeta **CM-17** en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`
