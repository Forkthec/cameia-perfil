# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores). Esta página lista lo que el servicio emite hoy.

## Códigos que el servicio emite

Todavía no emite el campo `code`; se adopta en la primera tarea de código del servicio (ver [ADR 0001](adr/0001-codigo-de-error-y-request-id.md)).

## Respuestas sin código

Casi todas las produce `ApiExceptionHandler` como `ProblemDetail` con `Content-Type: application/problem+json`; el `detail` es el mensaje de la excepción de negocio. Las excepciones estándar de Spring MVC que no figuran aquí (método no permitido, tipo de contenido no soportado, parámetro ausente) las responde la clase base `ResponseEntityExceptionHandler` con sus títulos estándar.

| HTTP | Título | Cuándo |
|---|---|---|
| 400 | Solicitud inválida | Un campo del cuerpo incumple Bean Validation; el `detail` lista cada campo con su mensaje |
| 400 | Parámetro inválido | El parámetro `lang` del catálogo de roles no es `es` ni `en` (lo arma `ProfessionalRoleController`, no el manejador) |
| 401 | Identidad requerida | La petición llega sin `X-User-Id` o con él en blanco |
| 403 | Acceso denegado | El perfil pertenece a otro usuario |
| 404 | Perfil no encontrado | El usuario no tiene perfil |
| 404 | Rol profesional no encontrado | El rol indicado no existe en el catálogo |
| 409 | Perfil ya existe | El usuario intenta crear un segundo perfil |
| 409 | Perfil ya completado | Se intenta finalizar un perfil ya finalizado |
| 409 | Habilidad duplicada | La habilidad ya está en el perfil |
| 409 | Rol objetivo duplicado | El rol ya es un rol objetivo del perfil |
| 422 | Valor no válido | Un objeto de valor del dominio rechaza el dato (`IllegalArgumentException`) |
| 422 | Máximo de roles objetivo alcanzado | El perfil ya tiene el máximo de roles objetivo |
| 422 | No se puede eliminar el último rol objetivo | Se intenta quitar el único rol objetivo de un perfil ya finalizado |
| 422 | Finalización incompleta | Finalizar el perfil sin cumplir los requisitos; el cuerpo no es un `ProblemDetail` sino `CompletionErrorResponse`: `{"missingRequirements": [...]}` con cada requisito faltante |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.
