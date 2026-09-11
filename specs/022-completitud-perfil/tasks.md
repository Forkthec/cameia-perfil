# Tasks — CM-22: Completitud del perfil

- **HU asociada:** CM-22
- **Fecha:** 11/09/2026
- **Estado:** completado

## Bloque 1 — Dominio

- [x] `IncompleteProfileException.java`: agregar `List<String> missingRequirements` al constructor
- [x] Crear `ProfileAlreadyCompletedException.java`
- [x] `ProfessionalProfile.java`: agregar `getMissingRequirements()` con los 5 requisitos (REQ-3)
- [x] `ProfessionalProfile.java`: agregar `complete()` que lanza `ProfileAlreadyCompletedException` si ya está COMPLETED o `IncompleteProfileException` con la lista si no cumple (REQ-1, REQ-2, REQ-4)
- [x] `ProfessionalProfile.java`: actualizar `removeTargetRole()` para solo bloquear cuando `status == COMPLETED` (REQ-5, REQ-6)
- [x] `ProfessionalProfile.java`: actualizar `isComplete()` para usar `getMissingRequirements().isEmpty()`

## Bloque 2 — Aplicación

- [x] `ProfileAppService.java`: agregar `completeProfile(UUID profileId)`

## Bloque 3 — Presentación

- [x] Crear `CompletionErrorResponse.java` — `record(List<String> missingRequirements)`
- [x] `ProfileController.java`: agregar `POST /{id}/completion`
- [x] `ApiExceptionHandler.java`: manejar `ProfileAlreadyCompletedException` → 409; actualizar `IncompleteProfileException` → 422 con lista
