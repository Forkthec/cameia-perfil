# CM-335 · Errores de protocolo previsibles en Perfil

| Dato | Valor |
|---|---|
| Tarea Jira | CM-335 (tarea, Sprint 3), responsable Paula Andrea Muñoz Delgado |
| Repositorio | `cameia-perfil`, base `origin/develop` = `bbe74c6` (CM-201 fusionada, PR #100) |
| Depende de | Nada sin fusionar: la lista de perfiles (CM-201) ya está en `develop` (PR #100, `bbe74c6`, 17-oct) |
| Estado | Decisiones aprobadas por Paula Andrea Muñoz Delgado el 19 de octubre de 2026 (ADR 0008 y multipart de tamaño excesivo = 413, opción B) |
| Estimación | 5 a 6 h de Backend (configuración, manejador, controlador de errores del contenedor, identidad, pruebas con petición real, Postman y documentos). Se informa a Vela |
| Servicios hermanos | `cameia-cuentas` y `cameia-gateway` tienen su propia spec de la misma tarea; cada una se entiende sola |

## 1. Objetivo

Que ninguna entrada malformada que un cliente pueda enviar a Perfil termine en el 500 genérico, en la forma por defecto de Spring (`timestamp`, `status`, `error`, `path`, sin `code` ni `requestId`) o en un HTML de Tomcat cuando la aplicación puede evitarlo:

1. Un formulario mal codificado (`a=%zz`) en un `PUT`, `PATCH` o `DELETE` (cualquier ruta) o en un `POST` con cuerpo.
2. Un parámetro de consulta mal codificado (`?lang=%zz`, `?status=%zz`, `?lang=%zz&lang=en`, `?%zz=1`) en un endpoint que lo lee.
3. Un `X-User-Id` con tabulación o con otro carácter de control.
4. Un `TRACE`.

Lo que Tomcat rechaza antes de que la aplicación vea la petición (400 HTML) no se puede cambiar desde el código: se documenta (§12).

## 2. Evidencia (peticiones reales del 17-oct-2026)

Registros en `ejecucion/evidencia/CM-335/` de la carpeta raíz de trabajo: `61` (filtro encendido), `62` (filtro apagado), `63` (`POST` con cuerpo), `65` y `66` (a través del Gateway), `67` (métodos), `68` (filtros activos). Perfil empaquetado desde `develop` `0854e5c`, PostgreSQL 16 desechable.

| # | Petición | Resultado hoy | Causa |
|---|---|---|---|
| E-1 | `PUT`, `PATCH` o `DELETE` con `Content-Type: application/x-www-form-urlencoded` y cuerpo `a=%zz`, en `/api/v1/profiles/{id}`, `/skills/{id}`, `/salary-expectation`, `/professional-roles` y en una ruta inexistente | 500 con la forma por defecto de Spring | `FormContentFilter` lee el cuerpo en el filtro y lanza `HttpMessageNotReadableException` (causa `IllegalArgumentException` de `URLDecoder`) antes del manejador de errores |
| E-2 | Lo mismo con el filtro apagado (`SPRING_MVC_FORMCONTENT_FILTER_ENABLED=false`) | `PUT`/`DELETE`: 405 o 404 con la forma común; `PATCH` con cuerpo JSON: 415 `MEDIA_TYPE_NOT_ALLOWED` | El filtro era la única causa de E-1 |
| E-3 | `POST` con cuerpo y formulario `a=%zz` en `/{id}/work-experiences`, `/educations`, `/skills`, `/target-roles` | 500 `INTERNAL_ERROR`, **con el filtro encendido y con el apagado** | Al leer el `@RequestBody`, Tomcat lanza `InvalidParameterException` (hija de `IllegalStateException`) y el manejador general la trata como imprevista |
| E-4 | `GET /api/v1/profiles/professional-roles?lang=%zz`, `?lang=%zz&lang=en`, `?%zz=1` | 500 `INTERNAL_ERROR` | La misma `InvalidParameterException`, al leer `lang` |
| E-4b | `?lang=%C3` y `?lang=%FF` (escape bien formado cuyos bytes no son UTF-8 válido), y `?status=%C3`/`%FF` | 500 `INTERNAL_ERROR` (medido el 17-oct con el jar empaquetado) | Tomcat decodifica con UTF-8 estricto y lanza la misma `InvalidParameterException` |
| E-5 | `?lang=%00`, `?lang=en`, `GET /{id}?x=%zz` | 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, 200, 404 | Sin problema: un parámetro que nadie lee no se decodifica |
| E-6 | `X-User-Id` con TAB, `0x85` o `0x9f` | Se acepta como identidad (404 `PROFILE_NOT_FOUND`) | `FirebaseUid.required` solo valida nulo, blanco y largo |
| E-7 | `X-User-Id` con `0x07` o `0x7f` | 400 HTML de Tomcat | Tomcat rechaza esos bytes antes de la aplicación (§12) |
| E-8 | `TRACE` | 405 con la forma por defecto de Spring | Tomcat responde 405 con `sendError` y Spring Boot lo muestra con su `BasicErrorController` |
| E-9 | `POST /api/v1/profiles` con formulario `a=%zz` | 201 (crea el perfil) | Ese `POST` no lee cuerpo ni parámetros. No es un fallo de esta tarea; se anota |

Otras rutas de filtro: Perfil no tiene filtros ni interceptores propios. Los filtros activos son `webMvcObservationFilter`, `characterEncodingFilter`, `formContentFilter` y `requestContextFilter` (`68`); solo `formContentFilter` lee el cuerpo.

## 3. Requisitos (EARS)

- **REQ-01** El sistema debe tener `spring.mvc.formcontent.filter.enabled: false` en `application.yml`. Perfil no recibe formularios: ninguna ruta los consume.
- **REQ-02** Cuando una `InvalidParameterException` de Tomcat llegue al manejador general con un método `POST`, `PUT`, `PATCH` o `DELETE` y un `Content-Type` que empiece por `application/x-www-form-urlencoded` (sin distinguir mayúsculas), el sistema debe responder 415 con `code` `MEDIA_TYPE_NOT_ALLOWED`, `title` «Tipo de contenido no admitido» y `detail` «Tipo de contenido no admitido.», igual que un formulario bien formado.
- **REQ-03** Cuando llegue la misma excepción en cualquier otro caso (parámetro de consulta, incluidos los escapes bien formados cuyos bytes no son UTF-8 válido, como `%C3` o `%FF`), el sistema debe responder 422 con `code` `REQUEST_QUERY_INVALID_FORMAT`, `title` «Consulta no válida» y `detail` «Revisa los datos enviados.», sin lista `errors` y sin repetir el valor recibido.
- **REQ-04** El sistema debe reconocer la excepción por el nombre de su clase (`org.apache.tomcat.util.http.InvalidParameterException`) en ella o en su cadena de causas, sin importar clases de Tomcat en `presentation`. Toda otra `IllegalStateException` sigue siendo un fallo imprevisto (500 `INTERNAL_ERROR`).
- **REQ-05** El sistema debe registrar el rechazo de REQ-02 y REQ-03 en `WARN` con `code`, `requestId` y `firebaseUid`, nunca con el valor del parámetro, el cuerpo ni el mensaje de la excepción de Tomcat (trae el nombre y el valor).
- **REQ-06** `FirebaseUid.required` debe responder 401 `IDENTITY_REQUIRED` («Tu sesión expiró. Inicia sesión de nuevo.») cuando el valor contenga algún carácter de control ISO (`Character.isISOControl`: U+0000–U+001F y U+007F–U+009F), además de nulo, en blanco o de más de 128 caracteres.
- **REQ-07** El sistema debe responder todo error que Tomcat reporte con `sendError` y llegue a la ruta `/error` con la forma común: `application/problem+json;charset=UTF-8`, `code`, `requestId` y el encabezado `X-Request-Id`. Mapeo por estado original: 404 → `ROUTE_NOT_FOUND`, 405 → `METHOD_NOT_ALLOWED`, 406 → `MEDIA_TYPE_NOT_ACCEPTABLE`, 415 → `MEDIA_TYPE_NOT_ALLOWED`, 400 → `REQUEST_INVALID_VALUE`, cualquier otro → `INTERNAL_ERROR` con el estado original en el registro `ERROR`. Un `TRACE` responde 405 `METHOD_NOT_ALLOWED`.
- **REQ-08** La ruta `/error` no debe aparecer en OpenAPI ni ser alcanzable como endpoint propio con datos: una petición directa `GET /error` (sin error original del contenedor) responde 404 `ROUTE_NOT_FOUND` con la forma común y se registra en `WARN`, nunca en `ERROR` (decisión de Paula, 17-oct).
- **REQ-09** El sistema debe documentar en `docs/errores.md` el código nuevo, la cobertura de `/error` y los rechazos de Tomcat que ocurren antes de la aplicación (§12), registrar la decisión en un ADR y corregir la fila `GET /api/v1/profiles` de la tabla de endpoints de `CLAUDE.md` (hoy lista solo dos valores de `status`; son cuatro).
- **REQ-10** Con el filtro apagado, el sistema no debe cambiar ninguna respuesta existente: las 4 rutas de E-3 con un formulario bien formado siguen en 415; un `PATCH` con JSON ilegible sigue en 422 `REQUEST_BODY_INVALID_FORMAT`.
- **REQ-11** Cuando llegue una `MultipartException` al manejador y el `Content-Type` de la petición empiece por `multipart/` (sin distinguir mayúsculas), el sistema debe responder 415 con el código nuevo `REQUEST_MULTIPART_INVALID_FORMAT` (título «Tipo de contenido no admitido», `detail` «Tipo de contenido no admitido.») y registrar `WARN` con `code`, `requestId` y `firebaseUid`, sin el mensaje de la excepción. Con otro `Content-Type`, la excepción sigue siendo un fallo imprevisto (500). Decisión de Paula, 17-oct (Q-1, opción 2). Medido: hoy `Content-Type: multipart/form-data` sin `boundary` (o con un cuerpo que Tomcat no puede partir) responde 500 en `POST /api/v1/profiles` y en todas las rutas `POST`.

- **REQ-12** Cuando llegue una `MaxUploadSizeExceededException` (un `multipart` con una parte de más de 1 MiB o una petición de más de 10 MiB; `spring.servlet.multipart.max-file-size` y `max-request-size`), el sistema debe responder 413 con el código nuevo `REQUEST_BODY_TOO_LARGE` (título «Cuerpo demasiado grande», `detail` «Cuerpo de la petición demasiado grande.», el mismo código, mensaje y forma que Cuentas), registrar `WARN` y no `ERROR`, sin pasar por el respaldo de fallos imprevistos, y sin citar el límite ni lo recibido. Decisión de Paula, 19-oct (CM-335-P1, opción B). El JSON no tiene tope: queda documentado en `docs/errores.md` con su destino.
- **REQ-13** Cuando el contenedor reporte a `/error` un 400, el sistema debe responder 422 `REQUEST_BODY_INVALID_FORMAT` y, con un 413, 413 `REQUEST_BODY_TOO_LARGE`; cada petición se registra una sola vez (mismo `requestId` en el manejador y en el controlador del contenedor) e `instance` conserva la ruta original.

## 4. Contrato

Cambio aditivo para Frontend: tres códigos nuevos (`REQUEST_QUERY_INVALID_FORMAT`, `REQUEST_MULTIPART_INVALID_FORMAT` y `REQUEST_BODY_TOO_LARGE`) y casos que pasan de 500 a un código que ya existía.

| Petición | Antes | Después |
|---|---|---|
| `PATCH /api/v1/profiles/{id}` con formulario `a=%zz` | 500 sin `code` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `DELETE`/`PUT` con formulario `a=%zz` en una ruta con ese método | 500 sin `code` | La respuesta normal de la ruta (por ejemplo 404 `PROFILE_NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`) |
| `POST …/skills` con formulario `a=%zz` | 500 `INTERNAL_ERROR` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `GET …/professional-roles?lang=%zz` | 500 `INTERNAL_ERROR` | 422 `REQUEST_QUERY_INVALID_FORMAT` |
| `GET /api/v1/profiles?status=%zz` | 500 `INTERNAL_ERROR` | 422 `REQUEST_QUERY_INVALID_FORMAT` |
| `X-User-Id` con TAB | Acepta la identidad | 401 `IDENTITY_REQUIRED` |
| `TRACE` | 405 sin `code` | 405 `METHOD_NOT_ALLOWED` |
| `POST` con `Content-Type: multipart/form-data` sin `boundary` (cualquier ruta) | 500 `INTERNAL_ERROR` | 415 `REQUEST_MULTIPART_INVALID_FORMAT` |

Cuerpo de ejemplo (422):

```json
HTTP/1.1 422
Content-Type: application/problem+json;charset=UTF-8
X-Request-Id: 3f2b…

{ "type": "about:blank", "title": "Consulta no válida", "status": 422,
  "detail": "Revisa los datos enviados.", "instance": "/api/v1/profiles/professional-roles",
  "code": "REQUEST_QUERY_INVALID_FORMAT", "requestId": "3f2b…" }
```

Aviso a Frontend al abrir el PR, en una línea: «Códigos nuevos `REQUEST_QUERY_INVALID_FORMAT` (422), `REQUEST_MULTIPART_INVALID_FORMAT` (415) y `REQUEST_BODY_TOO_LARGE` (413, multipart de más de 1 MiB por parte o 10 MiB por petición); un formulario mal codificado ya no da 500».

## 5. Validación de entrada y casos de prueba con datos literales

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| Formulario | `PATCH {id}` | `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED` | `ProtocolErrorsEndToEndIT.patch_shouldReturn415_whenFormIsMalformed` |
| | `PUT`, `DELETE` en ruta inexistente | `a=%zz` | 404 `ROUTE_NOT_FOUND` | `…put_shouldReturn404_whenRouteDoesNotExist`, `…delete_…` |
| | `DELETE …/skills/{id}` | `a=%zz` | 404 `PROFILE_NOT_FOUND` (perfil inexistente) | `…delete_shouldReturnNormalAnswer_whenFormIsMalformed` |
| | `POST` en las 4 rutas con cuerpo | `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED` | `…post_shouldReturn415_whenFormIsMalformed` (4 rutas) |
| | `Content-Type` con `charset` y mayúsculas | `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | 415 | `ApiExceptionHandlerTest.invalidParameter_shouldReturn415_whenFormContentTypeHasCharsetAndUppercase` |
| | Formulario bien formado | `a=1` en `PATCH {id}` | 415 (sin cambio) | `…patch_shouldStillReturn415_whenFormIsWellFormed` |
| | JSON ilegible | `{"a":` en `PATCH {id}` | 422 `REQUEST_BODY_INVALID_FORMAT` (sin cambio) | `…patch_shouldStillReturn422_whenJsonIsMalformed` |
| Consulta | valor | `?lang=%zz` | 422 `REQUEST_QUERY_INVALID_FORMAT` | `…get_shouldReturn422_whenLangIsMalformed` |
| | repetido | `?lang=%zz&lang=en` | 422 | `…get_shouldReturn422_whenLangIsRepeatedWithMalformed` |
| | nombre | `?%zz=1` | 422 | `…get_shouldReturn422_whenParameterNameIsMalformed` |
| | `status` | `?status=%zz` en `GET /api/v1/profiles` | 422 `REQUEST_QUERY_INVALID_FORMAT` | `…list_shouldReturn422_whenStatusIsMalformed` |
| | bien formado y desconocido | `?lang=%00`, `?lang=xx`, `?lang=%C3%B1` | 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` (sin cambio) | `…get_shouldKeepLanguageCode_whenLangIsWellFormedButUnknown` |
| | UTF-8 inválido | `?lang=%C3`, `?lang=%FF`, `?status=%FF` | 422 `REQUEST_QUERY_INVALID_FORMAT` (medido: hoy 500) | `…get_shouldReturn422_whenPercentEncodingBytesAreNotUtf8` |
| | no se lee | `GET /{id}?x=%zz` | respuesta normal de la ruta | `…get_shouldNotFail_whenUnreadParameterIsMalformed` |
| | no repite el valor | `?lang=%zz` | el cuerpo no contiene `zz` ni `%` salvo en `instance` si la ruta lo trae | `…get_shouldNotEchoValue` |
| Identidad | control | `"uid\t1"`, `"\u0085"`, `"\u009f"`, `"a\u0000"`, `"\n"` | 401 `IDENTITY_REQUIRED` | `FirebaseUidTest.required_shouldThrow_whenControlCharacter` |
| | válidas | `"uid-123_ABC"`, `"uid-ñ"`, 128 caracteres | se aceptan | `FirebaseUidTest.required_shouldAccept_whenPrintable` |
| | con TAB por HTTP | encabezado `X-User-Id: uid<TAB>x` | 401 | `…get_shouldReturn401_whenIdentityHasTab` |
| Método | `TRACE` | `/api/v1/profiles/professional-roles` | 405 `METHOD_NOT_ALLOWED` con `requestId` | `…trace_shouldReturn405WithCommonShape` |
| `/error` | sin error original | `GET /error` | 404 `ROUTE_NOT_FOUND`, registro `WARN` | `ContainerErrorControllerTest.error_shouldReturn404_whenNoOriginalStatus` |
| Multipart | sin `boundary` | `POST …/skills` y `POST /api/v1/profiles` con `Content-Type: multipart/form-data`, cuerpo `x` | 415 `REQUEST_MULTIPART_INVALID_FORMAT` | `ProtocolErrorsEndToEndIT.post_shouldReturn415_whenMultipartHasNoBoundary` (2 rutas) |
| | otro tipo | `new MultipartException("x")` con `application/json` | 500 `INTERNAL_ERROR` | `ApiExceptionHandlerTest.multipart_shouldReturn500_whenNotMultipartContentType` |
| | con tipo multipart | `new MultipartException("x")` con `multipart/form-data` | 415, sin el mensaje de la excepción | `ApiExceptionHandlerTest.multipart_shouldReturn415_whenMultipartContentType` |
| | estados | 404, 405, 406, 415, 400, 413, 503 | ver REQ-07 | `ContainerErrorControllerTest.error_shouldMapStatus` (7 casos) |
| Manejador | otra `IllegalStateException` | `new IllegalStateException("x")` | 500 `INTERNAL_ERROR` | `ApiExceptionHandlerTest.illegalState_shouldReturn500_whenNotInvalidParameter` |
| | excepción envuelta | `RuntimeException` con causa `InvalidParameterException` | 422 / 415 según REQ-02 y REQ-03 | `BadParameterEncodingTest.matches_shouldFindCause_whenWrapped` |

## 6. Base de datos

Sin migración ni cambio de esquema.

## 7. Casos borde (estándar §C)

| Grupo | Caso | Tratamiento |
|---|---|---|
| Nulos y vacíos | Cuerpo de formulario vacío; `Content-Type` de formulario sin cuerpo | Sin parámetros que decodificar: no hay excepción; respuesta normal de la ruta (415 o la propia) |
| Unicode | `?lang=%C3%B1` (válido), `?lang=%C3` (UTF-8 truncado), `?lang=%FF` | **Medido el 17-oct con el jar empaquetado:** `%C3%B1` → 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` (idioma desconocido); `%C3` y `%FF` → hoy **500** `INTERNAL_ERROR` (Tomcat los rechaza con la misma `InvalidParameterException`) y con este cambio 422 `REQUEST_QUERY_INVALID_FORMAT`. Prueba `…get_shouldReturn422_whenPercentEncodingBytesAreNotUtf8` |
| Límites | Parámetro de 1000 caracteres con `%zz` al final | 422 igual; sin eco |
| Seguridad | Valor hostil `'--`, `<script>` dentro de un parámetro mal codificado | Nunca se repite; solo 422 |
| Idempotencia | Repetir la misma petición mala 20 veces | La misma respuesta, sin escritura. Prueba `…get_shouldReturnSameAnswer_whenRepeated20Times` |
| Concurrencia | 50 peticiones malas en paralelo | Todas 422; ninguna 5xx. Prueba `…get_shouldNotFail_whenManyMalformedRequestsRunInParallel` |
| Registro | Valor malo en el parámetro | El registro no contiene el valor (captura de salida). Prueba `…get_shouldNotLogMalformedValue` |
| No aplica | Fechas y zonas, doble envío con escritura, falla a medias, dependencia caída, colecciones | Ninguno de estos caminos escribe ni llama a otro servicio |

## 8. Seguridad (ASVS nivel 1 y OWASP API Top 10)

| Control | Qué hace | Prueba |
|---|---|---|
| ASVS 4.1.1 / API8 | Todo error nuevo lleva `charset=UTF-8` y la forma común | `ResponseCharsetIT` existente y las pruebas de §5 |
| ASVS 2.2.1 y 2.2.2 / API10 | La entrada mal formada se rechaza en el servidor con un código y ningún valor recibido ni mensaje de librería se refleja (la matriz de ASVS 5.0.0 de `cameia-infra` no trae un requisito propio de «no reflejar el valor»; se cubre con OWASP API10) | `…get_shouldReturn422_whenLangIsMalformed` (sin `zz` en el cuerpo), `…get_shouldNotLogMalformedValue` |
| ASVS 8.2.2 / API2 | Una identidad con caracteres de control no es identidad (evita falsear líneas de registro y comparaciones) | `FirebaseUidTest`, `…get_shouldReturn401_whenIdentityHasTab`, `…get_shouldReturn401_whenIdentityHasC1Control` |
| API7 (configuración) | `/error` ya no muestra la forma por defecto de Spring con `path` ni `trace` | `ContainerErrorControllerTest` |
| API4 | Una petición mal formada se rechaza antes de tocar la base | La excepción ocurre antes del caso de uso; `…get_shouldNotFail_whenManyMalformedRequestsRunInParallel` |

## 9. Atributos de calidad de CAMEIA

La tarea no pertenece a una épica con atributos declarados; se aplican los transversales (anexo `Anexo_Restricciones_Atributos_Calidad`):

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0002 (disponibilidad) | DES-04 | 5xx < 1 % | Toda entrada malformada de §2 responde 4xx | Todas las de §5; recorrido real con Newman y las peticiones de §13 |
| AC-0003 (seguridad) | SEG-04 | Sin datos personales en logs | REQ-05 | `…get_shouldNotLogMalformedValue` |
| AC-0004 (fiabilidad) | FIA-01 | Ningún fallo cambia datos | Rechazo antes del caso de uso; sin escritura | `…get_shouldReturnSameAnswer_whenRepeated20Times` |
| AC-0006 (interoperabilidad) | INT | Error con `code` y mensaje claro | REQ-02, REQ-03, REQ-07 | pruebas de §5 |
| Transversal | MAN-01 | ≥ 90 % de líneas y ramas en lo nuevo o modificado | JaCoCo | `./mvnw.cmd -B -ntp clean verify` |
| Transversal | IOP-01 | OpenAPI completo | Las respuestas 415 de los endpoints con cuerpo ya están documentadas; el 422 `REQUEST_QUERY_INVALID_FORMAT` **no** lo está (medido: el `@ApiResponse` 422 del catálogo de roles solo cita `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, y el de la lista de perfiles solo el estado inválido): T2 lo agrega a esos dos `@ApiResponse`; `/error` no aparece | `OpenApiDocumentIT`: `apiDocs_shouldDocumentQueryInvalidFormat_whenRequested` y `apiDocs_shouldNotListErrorRoute_whenRequested` |

## 10. Errores

| Código | HTTP | Mensaje | Nuevo |
|---|---|---|---|
| `REQUEST_QUERY_INVALID_FORMAT` | 422 | «Revisa los datos enviados.» (título «Consulta no válida») | **Sí**: `ErrorCode`, fila en `ErrorCatalog.RESPONSES`, fila en `docs/errores.md`. Sin excepción de negocio propia: el manejador la produce a partir de la excepción del contenedor (no hay regla de negocio que la lance) |
| `REQUEST_MULTIPART_INVALID_FORMAT` | 415 | «Tipo de contenido no admitido.» (título «Tipo de contenido no admitido») | **Sí** (la pide la tarea CM-306, que Paula ya enlazó con CM-335 en el Sprint 3): `ErrorCode`, fila en `ErrorCatalog.RESPONSES`, fila en `docs/errores.md`. Sin excepción de negocio propia: el manejador la produce a partir de `MultipartException` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | «Tipo de contenido no admitido.» | No (nuevo caso) |
| `METHOD_NOT_ALLOWED` | 405 | «Método no permitido.» | No (nuevo caso: `TRACE`) |
| `IDENTITY_REQUIRED` | 401 | «Tu sesión expiró. Inicia sesión de nuevo.» | No (nuevo caso) |
| `INTERNAL_ERROR` | 500 | genérico | No: solo lo imprevisto |

La regla «todo error previsible tiene excepción de negocio» se cumple con una salvedad documentada: el rechazo de parámetros no es una regla del dominio sino del protocolo; se traduce en el manejador, igual que `HttpMessageNotReadableException`.

## 11. Pruebas, cobertura y comandos

| Capa | Pruebas |
|---|---|
| Dominio | `FirebaseUidTest` (se amplía) |
| Presentación (unitarias) | `BadParameterEncodingTest` (nueva), `ApiExceptionHandlerTest` (se amplía), `ContainerErrorControllerTest` (nueva), `ErrorCatalogTest` y `ErrorCodeDocumentationTest` (existentes, deben seguir verdes) |
| Punta a punta | `ProtocolErrorsEndToEndIT` (nueva, `RANDOM_PORT`, cliente de sockets `RawHttpClient` en `src/test/.../presentation/`) |
| Contrato | `OpenApiDocumentIT` (una prueba nueva: `/error` no aparece) |

Clases a medir: `BadParameterEncoding`, `ContainerErrorController`, `ApiExceptionHandler` (métodos nuevos), `FirebaseUid.required`, `ErrorCatalog`. Comandos: Docker encendido; `./mvnw.cmd -B -ntp clean verify`; `target/site/jacoco/jacoco.csv`; Newman con la carpeta nueva (§13).

## 12. Lo que la aplicación no puede cambiar (se documenta)

Tomcat rechaza con 400 y una página HTML, antes de la aplicación: `%zz`, `%00` o `%2F` en la **ruta** (medido: `GET /api/v1/profiles/%2Fprofessional-roles` → 400 HTML), una URL de unos 9000 bytes o más, un encabezado de unos 9000 bytes o más (el límite por defecto es 8 KB), y un `X-User-Id` con bytes `0x00`–`0x08`, `0x0A`–`0x1F` o `0x7F`. `CONNECT` responde 501 HTML. A través del Gateway, el cliente recibe esa página tal cual cuando el Gateway la deja pasar. Se escribe como nota en `docs/errores.md`, con la razón y con la salida del Gateway (que evita la mayoría porque borra los `X-User-*` del cliente y usa el `uid` del token).

## 13. Postman y Newman

Carpeta nueva «Errores de protocolo» en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, con usuario propio `uid-cm335-a`:

| # | Petición | Espera |
|---|---|---|
| P-01 | `PATCH /api/v1/profiles/{id}` con encabezado `Content-Type: application/x-www-form-urlencoded` y cuerpo crudo `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED`, `requestId`, `charset=UTF-8` |
| P-02 | `GET /api/v1/profiles/professional-roles?lang=%zz` | 422 `REQUEST_QUERY_INVALID_FORMAT`, «Revisa los datos enviados.», sin `errors` |
| P-03 | `POST /api/v1/profiles/{id}/skills` con formulario crudo `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED` |

Se corre con `docs/perfil-local.postman_environment.json` contra el jar empaquetado y la base de `docker compose`, filtrando con `--folder "Errores de protocolo"`. **Trampa:** si Newman reescribe `%zz` como `%25zz`, la petición deja de ser mala y P-02 responde 200; en ese caso se anota la salida real, se prueba la misma petición con `curl` y se informa a Paula (el recorrido real lo cubre `ProtocolErrorsEndToEndIT`).

## 14. Línea base

Antes de editar: `git fetch`, rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop` (con CM-201 fusionada) y `./mvnw.cmd -B -ntp clean verify` en verde con los `*IT` ejecutados. Si falla, detenerse y reportar.

## 15. Decisiones

| # | Decisión | Por qué | Descartado | Quién |
|---|---|---|---|---|
| D-1 | Código nuevo `REQUEST_QUERY_INVALID_FORMAT` (422, «Consulta no válida», «Revisa los datos enviados.», sin `errors`, sin eco) | Una causa propia que Frontend distingue | Reutilizar `REQUEST_INVALID_VALUE` | Paula, 17-oct |
| D-2 | Un formulario mal codificado en un `POST`, `PUT`, `PATCH` o `DELETE` responde 415 | Es lo que ya responde un formulario bien formado; el problema real es el tipo de contenido, no la consulta | Responder `REQUEST_QUERY_INVALID_FORMAT` también para el cuerpo (el mensaje «Consulta» confunde); 422 `REQUEST_BODY_INVALID_FORMAT` | Paula, 17-oct |
| D-3 | Apagar `FormContentFilter` en lugar de capturar su excepción | Perfil no recibe formularios; es la única forma de evitar la excepción que ocurre dentro del filtro, antes del manejador | Un filtro propio antes del de Spring que capture y delegue (más código, mismo resultado) | Backend, aceptado por Paula el 17-oct |
| D-4 | Un solo manejador para `InvalidParameterException`, dentro de `handleUnexpected`, reconocida por el nombre de la clase | `presentation` no importa Tomcat (instrucción de Paula); cubre cualquier parámetro presente o futuro | Un `@ExceptionHandler(IllegalStateException.class)` (atraparía excepciones ajenas); capturar en cada controlador | Backend, aceptado por Paula |
| D-5 | `ContainerErrorController` reemplaza al `BasicErrorController` | Cubre `TRACE` y cualquier `sendError` futuro, y deja la forma común aun si alguien reactiva el filtro | Dejar `TRACE` como limitación documentada | Paula, 17-oct (opción recomendada aceptada) |
| D-6 | `FirebaseUid.required` rechaza `Character.isISOControl` | Cubre TAB, DEL y los controles C1 (`0x80`–`0x9F`) que llegan de un encabezado en ISO-8859-1 | Lista fija de bytes; rechazar todo lo que no sea ASCII (excluye identidades legítimas con letras acentuadas si Firebase las permitiera) | Backend |
| D-7 | La spec se basa en `develop` con CM-201 ya fusionada | Así `?status=%zz` se prueba de verdad | Apilar sobre la rama de CM-201 | Paula, 17-oct |
| D-8 | El Gateway no cambia por esta spec (ver su spec) | Ningún cambio de Perfil depende de él | — | Paula |

## 16. Fuera de alcance

El 400 HTML de Tomcat (§12); el límite de tamaño del cuerpo (un cuerpo JSON de 20 MB llega a Perfil y responde 422; es un asunto de consumo de recursos, API4, que necesita tarea propia); el `POST /api/v1/profiles` que acepta cualquier `Content-Type` (E-9); el 503 `DATABASE_UNAVAILABLE` (CM-290).

## 17. Preguntas

Respondidas el 17-oct: D-1, D-2, D-5, D-7; Gateway = A; revisión de la especificación: **Q-1 = opción 2** (el `multipart` sin `boundary` se arregla aquí con un código nuevo, `REQUEST_MULTIPART_INVALID_FORMAT`, REQ-11; Paula, 17-oct) y **Q-2 = opción 1** (`GET /error` responde 404, REQ-08). Ninguna abierta para Perfil.


## 18. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | REQ-01 | `PATCH` con `a=%zz` por HTTP real | 415, no 500 |
| V-02 | REQ-02 | `POST` a las 4 rutas con `a=%zz` | 415 |
| V-03 | REQ-03 | `?lang=%zz`, `?status=%zz`, `?%zz=1` | 422 `REQUEST_QUERY_INVALID_FORMAT`, sin eco |
| V-04 | REQ-04 | `BadParameterEncodingTest`; `IllegalStateException` ajena | 500 sólo la ajena |
| V-05 | REQ-05 | Captura de salida | Sin valores |
| V-06 | REQ-06 | `FirebaseUidTest` y TAB por HTTP | 401 |
| V-07 | REQ-07 | `TRACE` y `ContainerErrorControllerTest` | 405 `METHOD_NOT_ALLOWED` con `requestId` |
| V-08 | REQ-08 | `OpenApiDocumentIT` y `GET /error` | `/error` fuera del contrato; 404 común |
| V-16 | REQ-11 | `multipart/form-data` sin `boundary` por HTTP real | 415 `REQUEST_MULTIPART_INVALID_FORMAT`, no 500 |
| V-09 | REQ-09 | `ErrorCodeDocumentationTest`, lectura de `docs/errores.md` | Verde; nota de §12 presente |
| V-10 | REQ-10 | Pruebas de «sin cambio» de §5 | Verde |
| V-11 | Catálogo | `ErrorCatalogTest`, `ErrorCodeTest` | Verde |
| V-12 | Cobertura | `jacoco.csv` | ≥ 90 % por clase tocada |
| V-13 | Newman | 3 peticiones | 0 fallos |
| V-14 | Adversarial | `?lang=` de 1000 caracteres con `%zz` al final; `?lang=%C3` y `%FF`; 50 en paralelo; `X-User-Id` con el byte `0x85` **enviado por socket crudo** (`curl` en Windows reescribe ese byte y no sirve); formulario con `charset` | 422, 422, sin 5xx, 401, 415 |
| V-15 | Regresión | Suite completa | Verde |
