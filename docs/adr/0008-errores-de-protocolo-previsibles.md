# ADR 0008 · Errores de protocolo previsibles

## Estado

Aceptada. Decisión de Backend, 17 de octubre de 2026; aprobada por Paula Andrea Muñoz Delgado el 19 de octubre de 2026.

## Contexto

Varias peticiones mal formadas que un cliente puede enviar terminaban en el 500 genérico o en la forma por defecto de Spring (sin `code` ni `requestId`):

- Un formulario mal codificado (`a=%zz`) en un `PUT`, `PATCH` o `DELETE`, o en un `POST` con cuerpo.
- Un parámetro de consulta mal codificado (`?lang=%zz`, `?status=%zz`, `?lang=%C3`).
- Un `multipart/form-data` sin `boundary`, con un cuerpo que no se puede partir o con una parte de más de 1 MiB (o una petición de más de 10 MiB), que terminaba en el 500 genérico.
- Un `X-User-Id` con tabulación o con otro carácter de control.
- Un `TRACE`.

Perfil no recibe formularios ni multipart, y el 500 genérico es solo para lo que nadie pudo prever.

## Decisión

- **D-1** Código nuevo `REQUEST_QUERY_INVALID_FORMAT` (422, título «Consulta no válida», detalle «Revisa los datos enviados.», sin `errors` y sin repetir el valor recibido) para un parámetro de consulta que no se puede leer.
- **D-2** Un formulario mal codificado en un `POST`, `PUT`, `PATCH` o `DELETE` responde 415 `MEDIA_TYPE_NOT_ALLOWED`, igual que un formulario bien formado: el problema es el tipo de contenido, no la consulta.
- **D-3** Se apaga el filtro de formularios de Spring (`spring.mvc.formcontent.filter.enabled: false`). Es la única forma de evitar la excepción que ocurre dentro del filtro, antes del manejador de errores.
- **D-4** El rechazo de Tomcat se traduce en el manejador general reconociendo la clase de la excepción por su nombre, en ella o en sus causas, para que `presentation` no importe clases del contenedor. Toda otra `IllegalStateException` sigue siendo un fallo imprevisto.
- **D-5** `ContainerErrorController` reemplaza al controlador de errores por defecto de Spring Boot: todo error que el contenedor reporte con `sendError` (por ejemplo un `TRACE`) responde con la forma común. Una petición directa a `/error` responde 404 y la ruta no aparece en OpenAPI.
- **D-6** `FirebaseUid.required` rechaza los caracteres de control (`Character.isISOControl`: TAB, saltos de línea, DEL y los controles C1) con 401 `IDENTITY_REQUIRED`.
- **D-7** Código nuevo `REQUEST_MULTIPART_INVALID_FORMAT` (415, «Tipo de contenido no admitido.») cuando llega una `MultipartException` y el `Content-Type` empieza por `multipart/`. Con otro tipo de contenido sigue siendo un fallo imprevisto.

- **D-8** Un `multipart` que supera los topes (`spring.servlet.multipart.max-file-size`: 1 MiB por parte; `max-request-size`: 10 MiB por petición) responde 413 `REQUEST_BODY_TOO_LARGE` («Cuerpo de la petición demasiado grande.»), con el mismo código, mensaje y forma que Cuentas. El manejador sobrescribe `handleMaxUploadSizeExceededException` de `ResponseEntityExceptionHandler`, así que no pasa por el respaldo de fallos imprevistos: se registra en `WARN`, no en `ERROR`. El detalle no cita el límite ni lo recibido.
- **D-9** El 400 que reporta el contenedor a `/error` (por ejemplo un cuerpo troceado con un trozo mal formado) responde 422 `REQUEST_BODY_INVALID_FORMAT`, y un 413 del contenedor, 413 `REQUEST_BODY_TOO_LARGE`. Una petición se registra una sola vez: el manejador marca la petición como registrada y el controlador del contenedor reutiliza su `requestId`. `instance` conserva la ruta original.
- **D-10** `Accept`, 406 y 415 (con `multipart`) se documentan en las 15 operaciones publicadas de OpenAPI mediante un `OperationCustomizer`, sin repetirlos en cada endpoint.

Lo que Tomcat rechaza antes de que la aplicación vea la petición (400 con página HTML) no se puede cambiar desde el código; se documenta en [errores.md](../errores.md#rechazos-que-ocurren-antes-de-la-aplicación).

## Consecuencias

- Cambio aditivo para Frontend: tres códigos nuevos (`REQUEST_QUERY_INVALID_FORMAT`, `REQUEST_MULTIPART_INVALID_FORMAT` y `REQUEST_BODY_TOO_LARGE`) y casos que pasan de 500 a un código que ya existía. Un `multipart` grande responde 413 en lugar de 500. Se le avisa con el catálogo de errores.
- Los códigos nuevos no tienen excepción de negocio propia: el rechazo es del protocolo y no de una regla del dominio, y el manejador lo produce a partir de la excepción del contenedor, igual que con un cuerpo JSON ilegible.
- Ninguna respuesta ni registro de estos rechazos repite el valor recibido ni el mensaje de la excepción de Tomcat.

## Alternativas descartadas

- **Dejar el multipart de tamaño excesivo como 500 genérico o responderlo 422.** Es un fallo previsible del cliente: el 500 viola la regla de errores visibles y el 422 no distingue «demasiado grande» de «mal formado». Se descartó también registrarlo en `ERROR` por la vía heredada: el cliente no hizo nada que el servicio deba investigar.
- **Reutilizar `REQUEST_INVALID_VALUE` para la consulta mal codificada.** Frontend no podría distinguir una consulta ilegible de un valor rechazado por una regla.
- **Responder `REQUEST_QUERY_INVALID_FORMAT` también para un formulario.** El mensaje «Consulta» confunde cuando el problema es el cuerpo, y 422 `REQUEST_BODY_INVALID_FORMAT` describe un JSON ilegible, no un tipo de contenido.
- **Un filtro propio antes del de Spring que capture la excepción y delegue.** Más código con el mismo resultado, porque Perfil no necesita el filtro.
- **Un `@ExceptionHandler(IllegalStateException.class)`.** Atraparía excepciones ajenas que sí son fallos imprevistos.
- **Dejar `TRACE` como limitación documentada.** Cubrirlo con el controlador de errores también deja la forma común ante cualquier `sendError` futuro.
- **Lista fija de bytes o rechazar todo lo que no sea ASCII en la identidad.** La primera deja pasar controles que no se previeron y la segunda rechazaría identidades legítimas con letras acentuadas.
