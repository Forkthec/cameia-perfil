# ADR 0007 · Lista de perfiles del usuario

## Estado

Aceptada. Decisiones de Paula Andrea Muñoz Delgado, 17 de octubre de 2026.

## Contexto

La pantalla «Nueva entrevista» (HU-4.2) y la pantalla «Perfiles» (HU-2.13) necesitan los perfiles del Usuario con su nombre, su estado y sus roles objetivo. Hasta ahora Perfil solo leía un perfil por su identificador. Un Usuario tiene como máximo 5 perfiles (Premium) y el plan Free permite 1.

## Decisión

- **`GET /api/v1/profiles`** devuelve `{"profiles": [...]}`: un objeto con la lista dentro, para poder agregar campos al objeto sin romper a quien consume.
- **Orden:** por creación ascendente y, a igual instante, por `id` ascendente. Los roles de cada perfil salen en el orden en que se agregaron.
- **Vista resumida:** cada perfil trae exactamente `id`, `name` (`null` si aún no tiene), `status` y `targetRoles`, y cada rol trae exactamente `professionalRoleId` y `roleTitle`. No viajan el resumen profesional, la experiencia, la formación ni las habilidades.
- **Filtro `?status=`** con los cuatro estados del código (`PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`), en mayúsculas exactas. Vacío, repetido, en minúsculas o desconocido responde 422 `PROFILE_STATUS_INVALID_VALUE`, sin lista `errors` y sin repetir el valor recibido.
- **Cualquier otro parámetro se ignora:** el dueño sale siempre de `X-User-Id`.
- **`Cache-Control: no-store`** en toda respuesta 200, igual que `GET /users/me` en Cuentas.
- **Sin paginación:** el cupo del plan limita la lista a 5 perfiles.
- **Una sola consulta** con los roles unidos, sin cargar el agregado completo de cada perfil.

## Consecuencias

- Cambio aditivo: ruta nueva, ningún campo existente cambia. Frontend ya recibió la forma el 17 de octubre.
- Frontend debe mostrar como «Borrador» todo estado distinto de `COMPLETED`: el contrato admite `PENDING` e `IN_REVIEW` aunque hoy el código solo produce `IN_PROGRESS` y `COMPLETED`.
- Si el plan permitiera muchos más perfiles, habría que paginar y agregar un índice compuesto por usuario y fecha de creación.

## Alternativas descartadas

- **Arreglo en la raíz:** impide agregar campos sin romper el contrato.
- **Paginación:** no se justifica con un máximo de 5 perfiles.
- **Perfil completo en la lista:** envía datos personales que la pantalla no usa (datos mínimos).
- **Filtro solo con `COMPLETED`:** el estado del perfil ya admite cuatro valores y Frontend decide qué muestra.
