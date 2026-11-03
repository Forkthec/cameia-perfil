# Tasks — CM-24: Limpieza de contrato OpenAPI

- **HU asociada:** CM-24
- **Fecha:** 11/09/2026
- **Estado:** completado

## Bloque 1 — Validación de campos (BE-10)

- [x] DTOs de entrada (`AddWorkExperienceRequest`, `AddSkillRequest`, `AddEducationRequest`, `AddTargetRoleRequest`): agregar `@NotNull` / `@NotBlank` en campos obligatorios
- [x] `ApiExceptionHandler.java`: manejar `MethodArgumentNotValidException` → 400 con `ProblemDetail`

## Bloque 2 — Contrato OpenAPI (BE-09)

- [x] `ProfileController.java`: cambiar `@ResponseStatus` a 201 en todos los POST que crean
- [x] `ProfileController.java`: agregar `produces = MediaType.APPLICATION_JSON_VALUE` en todos los métodos
- [x] `ProfileController.java`: anotar `PATCH /salary-expectation` con `@Hidden` (excluir del spec)
- [x] `OpenApiConfig.java`: registrar `ProblemDetail` como schema global de errores

## Bloque 3 — Verificación

- [x] Verificar que Swagger UI en `/swagger-ui.html` muestra 201 en los POST
- [x] Verificar que `/salary-expectation` no aparece en el spec publicado
- [x] Verificar que ningún schema incluye `seniority`
