# Evidencia antes de la corrección — CM-54 (10-oct-2026)

Aplicación empaquetada de la punta de CM-274 (PR 6b), `PATCH /api/v1/profiles/{id}` con un Usuario sintético y un perfil recién creado.

| Petición | Resultado observado | Defecto |
|---|---|---|
| `{"name":"","summary":"<2001 veces a>"}` | 422 con **un solo** elemento en `errors[]`: `name` / `PROFILE_NAME_REQUIRED` | El resumen de 2001 caracteres no se informa junto con el nombre (R2: todos los errores a la vez) |
| `{"name":"Analista\u0000"}` | 500 `INTERNAL_ERROR` | Carácter de control sin error específico (R3) |
| `{"provenance":"AI_SUGGESTED"}` | 200 y el perfil queda con `provenance` `AI_SUGGESTED` | La procedencia del perfil la cambia el cliente (el MVP no la acepta) |
