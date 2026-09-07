# Spec — CM-16: Crear perfil profesional (HU-2.2)

> **Estado:** RETROACTIVO — implementado antes de adoptar SDD (07/09/2026).
> Spec reconstruido desde el código y los tests en develop.

---

## Contexto

Un usuario de CAMEIA se autentica con Firebase y necesita crear su perfil profesional por primera vez. El perfil es el agregado raíz del microservicio `cameia-perfil`. Sin perfil no existe ningún otro dato del candidato.

---

## Requisitos (notación EARS)

**REQ-1** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles` con el encabezado `X-User-Id: <uid>`, el sistema crea un `ProfessionalProfile` con estado `IN_PROGRESS`, `reviewStatus = PENDING_REVIEW` y lo persiste, retornando 201 con el cuerpo del perfil.

**REQ-2** — Unwanted behavior
Si ya existe un perfil con el mismo `firebaseUid`, el sistema rechaza la solicitud con 409 y `ProblemDetail.title = "Perfil ya existe"`.

**REQ-3** — Unwanted behavior
Si la solicitud llega sin el encabezado `X-User-Id`, el sistema retorna 400.

**REQ-4** — State
Mientras el perfil recién creado no haya sido enviado a revisión, el sistema mantiene `status = IN_PROGRESS`.

**REQ-5** — Behavior
Cuando el sistema crea el perfil, genera un UUID v4 como `id` y registra `createdAt` y `updatedAt` con el instante actual.

---

## Modelo de respuesta (`ProfileResponse`)

```json
{
  "id": "<uuid>",
  "status": "IN_PROGRESS",
  "reviewStatus": "PENDING_REVIEW",
  "targetRoles": [],
  "workExperiences": [],
  "educations": [],
  "skills": []
}
```

---

## Invariantes del dominio

- `ProfessionalProfile.create(FirebaseUid)` es el único punto de entrada.
- El perfil nace sin nombre, sin resumen y sin roles objetivo — todos nulos.
- `isComplete()` retorna `false` al crear (nombre, resumen y al menos un rol son requisito).

---

## Endpoints implementados

| Método | Ruta | Código OK |
|---|---|---|
| POST | `/api/v1/profiles` | 201 |
| GET | `/api/v1/profiles/{id}` | 200 |

---

## Pruebas de referencia

- `ProfileControllerTest#postProfiles_returns201WithProfileBody`
- `ProfileControllerTest#postProfiles_returns409WhenProfileAlreadyExists`
- `ProfileControllerTest#postProfiles_returns400WhenXUserIdHeaderIsMissing`
- `ProfessionalProfileTest` — crear perfil, estado inicial
- Postman: carpeta **CM-16** en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`
