# Evidencia antes del cambio — CM-67 (10-oct-2026)

Contra la aplicación empaquetada con el código anterior a esta tarea (`docker compose up -d`, base limpia). Identidad `uid-ev-<aleatorio>`.

| # | Petición | Resultado antes | Después de la tarea |
|---|---|---|---|
| a | `POST …/completion` de un perfil recién creado | 422 `PROFILE_INCOMPLETE` con `missingRequirements` `["NAME","SUMMARY","EDUCATION","SKILLS","TARGET_ROLES"]` y **sin `errors[]`** | La misma lista y un elemento de `errors[]` por requisito (`name`, `summary`, `education`, `skills`, `targetRoles`) |
| b | Guardar un resumen de 2001 caracteres por SQL (`update perfil_profesional set resumen = repeat('a', 2001)`) | `ERROR: value too long for type character varying(2000)`: la columna ya no admite 2001 | El caso no puede darse en la base; la regla de 2000 se defiende en el dominio y se prueba con un perfil reconstruido (`complete_shouldListSummary_whenStoredSummaryHas2001CodePoints`) |
| c | `POST …/completion` con cuerpo `{"x":` y `Content-Type: application/json` | 422 `PROFILE_INCOMPLETE`: el cuerpo se ignora (V1: no responde `REQUEST_BODY_INVALID_FORMAT`) | Igual; se fija en `postCompletion_shouldIgnoreBody_whenBodyIsSent` |
| d | `POST …/review-requests` de un perfil incompleto | 422 `PROFILE_INCOMPLETE` (la ruta existía, oculta en el OpenAPI) | 404 `ROUTE_NOT_FOUND` |
