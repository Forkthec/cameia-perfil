# Tarjetas · CM-335 Errores de protocolo previsibles en Perfil

**Reglas para todas las tarjetas:** identificadores y nombres de prueba en inglés (`method_shouldX_whenY`) con `@DisplayName` en español; Javadoc en todo lo nuevo; sin `CM-335` en comentarios ni rutas a otros archivos; sin dependencias nuevas; sin tocar migraciones ni `.github/`; nunca registrar valores de parámetros, cuerpos ni mensajes de la excepción de Tomcat. Escapes `\uXXXX` en Java: verificar con `git diff` que no se convirtieron en el carácter real. Si algo no coincide con la tarjeta, detenerse y reportar a Paula con la salida real. Docker Desktop encendido (los `*IT` corren con Failsafe en `verify`). Estado de la spec: **BORRADOR, sin aprobación: no se implementa nada hasta que Paula la apruebe.**

---

## T0 · Preparación

1. `git fetch`; comprobar que `origin/develop` contiene la lista de perfiles (`git grep -n "listProfiles" origin/develop -- src/main`). Si no la contiene, detenerse: CM-201 no se ha fusionado.
2. Rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop`; anotar el SHA.
3. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS` con los `*IT` ejecutados. Si falla, detenerse.
4. Mover CM-335 en Jira a «En curso» solo si Paula lo indica (es una tarea, no subtarea).
5. Comprobar el número libre del ADR (`ls docs/adr`): la spec dice 0008; si ya existe, usar el siguiente y corregir la spec con Paula.

---

## T1 · Filtro de formularios apagado y cliente de pruebas con sockets (REQ-01, REQ-10)

**Reproducción primero (falla antes del arreglo):** crear `src/test/java/co/edu/unicauca/cameia/perfil/presentation/RawHttpClient.java`:

```java
/**
 * Cliente HTTP de pruebas que escribe la petición byte a byte en un socket. Existe porque
 * {@code java.net.URI} rechaza {@code %zz} y {@code HttpClient} normaliza los encabezados:
 * aquí la petición mala llega tal cual al servidor.
 */
final class RawHttpClient {
    /** Respuesta reducida a lo que las pruebas comprueban. */
    record Reply(int status, Map<String, String> headers, String body) { }

    private final int port;
    RawHttpClient(int port) { this.port = port; }

    /**
     * @param method  método HTTP, por ejemplo {@code PATCH}
     * @param target  ruta y consulta tal como se escriben en la línea de petición, sin validar
     * @param headers encabezados completos {@code Nombre: valor}; se escriben en ISO-8859-1
     * @param body    cuerpo o {@code null}
     */
    Reply send(String method, String target, List<String> headers, String body) throws IOException { /* ... */ }
}
```

Esqueleto de `send`: abrir `new Socket("localhost", port)`; escribir `METHOD target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n` + cada encabezado + `Content-Length` (bytes del cuerpo) + `\r\n\r\n` + cuerpo, todo con `StandardCharsets.ISO_8859_1`; leer hasta el cierre; separar línea de estado, encabezados (mapa con clave en minúsculas) y cuerpo. Si la respuesta usa `Transfer-Encoding: chunked`, decodificar los fragmentos (Tomcat lo usa en errores sin `Content-Length`). **Trampa medida:** el largo de cada fragmento va en **bytes** y el cuerpo trae tildes (`«válidos»`): leer toda la respuesta como `ISO_8859_1` para trocear los fragmentos y convertir el cuerpo ya decodificado a UTF-8 con `new String(cuerpo.getBytes(ISO_8859_1), UTF_8)`; si se lee como UTF-8, `Integer.parseInt` falla con `NumberFormatException`.

Prueba `src/test/java/co/edu/unicauca/cameia/perfil/presentation/ProtocolErrorsEndToEndIT.java` (forma de `TargetRolesEndToEndIT`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Testcontainers de PostgreSQL con `@ServiceConnection`). Cada caso lee `code` y `requestId` del cuerpo con `JsonMapper`:

| Prueba | Petición | Esperado antes del arreglo | Esperado después |
|---|---|---|---|
| `patch_shouldReturn415_whenFormIsMalformed` | `PATCH /api/v1/profiles/11111111-1111-1111-1111-111111111111`, `Content-Type: application/x-www-form-urlencoded`, `X-User-Id: uid-cm335-a`, cuerpo `a=%zz` | 500 sin `code` | 415, `code` `MEDIA_TYPE_NOT_ALLOWED`, `requestId` presente |
| `put_shouldReturn404_whenRouteDoesNotExist` | `PUT /ruta-inexistente`, formulario `a=%zz` | 500 | 404 `ROUTE_NOT_FOUND` |
| `delete_shouldReturn404_whenRouteDoesNotExist` | ídem con `DELETE` | 500 | 404 `ROUTE_NOT_FOUND` |
| `delete_shouldReturnNormalAnswer_whenFormIsMalformed` | `DELETE /api/v1/profiles/{uuid}/skills/{uuid}`, `a=%zz` | 500 | 404 `PROFILE_NOT_FOUND` |
| `patch_shouldStillReturn415_whenFormIsWellFormed` | `PATCH {id}`, `a=1` | 415 | 415 |
| `patch_shouldStillReturn422_whenJsonIsMalformed` | `PATCH {id}`, `Content-Type: application/json`, cuerpo `{"a":` | 422 `REQUEST_BODY_INVALID_FORMAT` | igual |

Los cuatro primeros (`patch_…`, las dos de ruta inexistente y `delete_shouldReturnNormalAnswer…`) deben **fallar** con 500 antes del cambio (las dos últimas de la tabla, de control, ya pasan): pegar esa salida en el resultado de la tarjeta.

**Arreglo:** en `src/main/resources/application.yml`, dentro del bloque `spring.mvc` (junto a `problemdetails`), agregar:

```yaml
    # Perfil no recibe formularios. El filtro de Spring los lee antes de que exista un manejador
    # de errores y un formulario mal codificado terminaba en un 500 sin formato.
    formcontent:
      filter:
        enabled: false
```

**Trampas:** la clave es `spring.mvc.formcontent.filter.enabled` (Spring Boot 4); si el 500 persiste, comprobar con `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_WEB_SERVLET=DEBUG` que `formContentFilter` ya no aparece en «Mapping filters» y detenerse. `Connection: close` evita que el socket espere; `Content-Length` debe ser en bytes.

**Comando (en PowerShell los `-D` con punto van entre comillas, o el argumento se parte):** `./mvnw.cmd -B -ntp verify "-Dit.test=ProtocolErrorsEndToEndIT" "-Dtest=NoExiste" "-Dsurefire.failIfNoSpecifiedTests=false"`.

**Commit:** `CM-335 | fix(perfil): apagar el filtro de formularios de Spring y cubrirlo con pruebas de punta a punta`.

---

## T2 · Formulario o consulta mal codificados (REQ-02 a REQ-05)

**Reproducción primero:** agregar a `ProtocolErrorsEndToEndIT` (fallan con 500 antes):

| Prueba | Petición | Esperado |
|---|---|---|
| `post_shouldReturn415_whenFormIsMalformed` (`@ParameterizedTest` con las rutas `/work-experiences`, `/educations`, `/skills`, `/target-roles` bajo `/api/v1/profiles/11111111-1111-1111-1111-111111111111`) | `POST`, formulario `a=%zz`, `X-User-Id: uid-cm335-a` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `get_shouldReturn422_whenLangIsMalformed` | `GET /api/v1/profiles/professional-roles?lang=%zz` | 422 `REQUEST_QUERY_INVALID_FORMAT`, `detail` «Revisa los datos enviados.», sin `errors`, cuerpo sin `zz` |
| `get_shouldReturn422_whenLangIsRepeatedWithMalformed` | `?lang=%zz&lang=en` | 422 igual |
| `get_shouldReturn422_whenParameterNameIsMalformed` | `?%zz=1` | 422 igual |
| `list_shouldReturn422_whenStatusIsMalformed` | `GET /api/v1/profiles?status=%zz` con `X-User-Id: uid-cm335-a` | 422 igual |
| `get_shouldKeepLanguageCode_whenLangIsWellFormedButUnknown` | `?lang=%00` y `?lang=xx` | 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` |
| `get_shouldNotFail_whenUnreadParameterIsMalformed` | `GET /api/v1/profiles/{uuid}?x=%zz` con identidad | 404 `PROFILE_NOT_FOUND` |
| `get_shouldReturn422_whenPercentEncodingBytesAreNotUtf8` (`@ParameterizedTest`: `%C3`, `%FF`) | `GET …/professional-roles?lang=%C3` y `?lang=%FF` | 422 `REQUEST_QUERY_INVALID_FORMAT` (**medido: antes del arreglo dan 500**; después, 422). `?lang=%C3%B1` es un escape válido y da 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` (va en `get_shouldKeepLanguageCode_whenLangIsWellFormedButUnknown`) |
| `get_shouldReturn422_whenParameterIsVeryLong` | `?lang=` + `"a"` × 1000 + `%zz` | 422 `REQUEST_QUERY_INVALID_FORMAT` |
| `get_shouldReturnSameAnswer_whenRepeated20Times` | 20 veces `?lang=%zz` | 20 respuestas 422 con el mismo `code`; ningún dato nuevo en la base |
| `get_shouldNotFail_whenManyMalformedRequestsRunInParallel` | 50 hilos con `?lang=%zz` | 50 × 422 |
| `get_shouldNotLogMalformedValue` | `?lang=%zzSECRETO` capturando la salida estándar con `OutputCaptureExtension` | el registro contiene `REQUEST_QUERY_INVALID_FORMAT` y no contiene `SECRETO` |

**Código nuevo.**

`domain/exception/ErrorCode.java`, después de `REQUEST_BODY_INVALID_FORMAT`:

```java
    /** Un parámetro de la consulta no está bien codificado y no se pudo leer. */
    REQUEST_QUERY_INVALID_FORMAT,
```

`presentation/advice/ErrorCatalog.java`, en `RESPONSES`, tras la fila de `REQUEST_BODY_INVALID_FORMAT`:

```java
            entry(REQUEST_QUERY_INVALID_FORMAT, UNPROCESSABLE_CONTENT, "Consulta no válida",
                    "Revisa los datos enviados."),
```

`presentation/advice/BadParameterEncoding.java` (nueva, paquete-privada):

```java
/**
 * Reconoce el rechazo del contenedor ante un parámetro mal codificado ({@code %zz}).
 *
 * <p>La excepción es de Tomcat; se compara el nombre de su clase para que la capa de presentación
 * no dependa del contenedor. Se busca también en la cadena de causas por si un componente la envolvió.</p>
 */
final class BadParameterEncoding {
    private static final String TOMCAT_CLASS = "org.apache.tomcat.util.http.InvalidParameterException";
    private static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private BadParameterEncoding() { }

    /** @return {@code true} si la excepción o alguna de sus causas es el rechazo de Tomcat */
    static boolean matches(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {   // la cadena es finita; si hay ciclo, parar tras 10 pasos
            if (TOMCAT_CLASS.equals(t.getClass().getName())) return true;
        }
        return false;
    }

    /** @return {@code true} si el parámetro malo venía del cuerpo de un formulario y no de la consulta */
    static boolean fromFormBody(String method, String contentType) {
        return method != null && BODY_METHODS.contains(method.toUpperCase(Locale.ROOT))
                && contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(FORM_MEDIA_TYPE);
    }
}
```

`presentation/advice/ApiExceptionHandler.java`, al inicio de `handleUnexpected` (antes del `log.error`):

```java
        if (BadParameterEncoding.matches(ex)) {
            // Un parámetro mal codificado es un dato del cliente, no un fallo del servicio.
            var servlet = request instanceof ServletWebRequest web ? web.getRequest() : null;
            var form = servlet != null
                    && BadParameterEncoding.fromFormBody(servlet.getMethod(), servlet.getContentType());
            return reject(form ? ErrorCode.MEDIA_TYPE_NOT_ALLOWED : ErrorCode.REQUEST_QUERY_INVALID_FORMAT, null, request);
        }
```

Importar `org.springframework.web.context.request.ServletWebRequest`. Actualizar el Javadoc del método: «Un fallo no controlado responde un mensaje genérico, salvo el rechazo de un parámetro mal codificado». `reject(ErrorCode, String, WebRequest)` ya registra `code`, `requestId` y `firebaseUid` en `WARN` y no incluye el mensaje de la excepción.

**Multipart mal formado (REQ-11).** Reproducción primero en `ProtocolErrorsEndToEndIT` (fallan con 500 antes): `post_shouldReturn415_whenMultipartHasNoBoundary` (`@ParameterizedTest` con `POST /api/v1/profiles/11111111-1111-1111-1111-111111111111/skills` y `POST /api/v1/profiles`; encabezado `Content-Type: multipart/form-data`, `X-User-Id: uid-cm335-a`, cuerpo `x`) → 415 `REQUEST_MULTIPART_INVALID_FORMAT`. Código en `ApiExceptionHandler` (importar `org.springframework.web.multipart.MultipartException`, que es de Spring y no de Tomcat):

```java
    /** Perfil no recibe multipart: uno roto es un tipo de contenido no admitido, no un fallo del servicio. */
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        var servlet = request instanceof ServletWebRequest web ? web.getRequest() : null;
        var type = servlet != null ? servlet.getContentType() : null;
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return reject(ErrorCode.REQUEST_MULTIPART_INVALID_FORMAT, null, request);
        }
        return handleUnexpected(ex, request);
    }
```

Un `MaxUploadSizeExceededException` (hija de `MultipartException`) sigue en su manejador propio de `ResponseEntityExceptionHandler` porque Spring elige la excepción más cercana. Pruebas en `ApiExceptionHandlerTest`: `multipart_shouldReturn415_whenMultipartContentType`, `multipart_shouldReturn500_whenNotMultipartContentType`. **Código nuevo (lo pide CM-306):** en `ErrorCode.java`, tras `REQUEST_QUERY_INVALID_FORMAT`: `/** Un multipart de la petición no se pudo leer (por ejemplo, sin boundary). */ REQUEST_MULTIPART_INVALID_FORMAT,`; en `ErrorCatalog.RESPONSES`: `entry(REQUEST_MULTIPART_INVALID_FORMAT, UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido", "Tipo de contenido no admitido."),`; en `docs/errores.md`, fila nueva (la prueba de documentación exige una por cada valor del enum): `| \`REQUEST_MULTIPART_INVALID_FORMAT\` | 415 | Cualquier ruta con un \`Content-Type: multipart/*\` mal formado | — | «Tipo de contenido no admitido.» | \`ApiExceptionHandler\` (\`MultipartException\`) | \`ApiExceptionHandlerTest.multipart_shouldReturn415_whenMultipartContentType\`, \`ProtocolErrorsEndToEndIT.post_shouldReturn415_whenMultipartHasNoBoundary\` |`. `ErrorCatalogTest` y `ErrorCodeDocumentationTest` deben seguir verdes.

**OpenAPI (R8, IOP-01).**

**Pruebas unitarias.**

`src/test/java/co/edu/unicauca/cameia/perfil/presentation/advice/BadParameterEncodingTest.java`:

| Prueba | Entrada | Esperado |
|---|---|---|
| `matches_shouldBeTrue_whenTomcatException` | `new org.apache.tomcat.util.http.InvalidParameterException("x")` | `true` |
| `matches_shouldFindCause_whenWrapped` | `new RuntimeException("a", new RuntimeException("b", tomcat))` | `true` |
| `matches_shouldBeFalse_whenOtherIllegalState` | `new IllegalStateException("x")` | `false` |
| `matches_shouldBeFalse_whenNull` | `null` | `false` |
| `fromFormBody_shouldBeTrue` (`@CsvSource`) | `POST`/`PUT`/`PATCH`/`DELETE` con `application/x-www-form-urlencoded`, con `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | `true` |
| `fromFormBody_shouldBeFalse` (`@CsvSource`) | `GET` con formulario; `POST` con `application/json`; `POST` con `null`; método `null` | `false` |

`ApiExceptionHandlerTest` (ampliar con el estilo de las pruebas existentes de la clase):

| Prueba | Entrada | Esperado |
|---|---|---|
| `invalidParameter_shouldReturn422QueryInvalid_whenGet` | `GET` con la excepción de Tomcat | 422, `code` `REQUEST_QUERY_INVALID_FORMAT`, `detail` «Revisa los datos enviados.», sin `errors`, `requestId` |
| `invalidParameter_shouldReturn415_whenFormBody` (`@ValueSource` `POST`, `PUT`, `PATCH`, `DELETE`) | método con formulario | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `invalidParameter_shouldReturn415_whenFormContentTypeHasCharsetAndUppercase` | `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | 415 |
| `invalidParameter_shouldNotEchoMessage` | excepción con mensaje `Parameter [a] with value [%zz]` | el cuerpo no contiene `%zz` |
| `illegalState_shouldReturn500_whenNotInvalidParameter` | `new IllegalStateException("x")` | 500 `INTERNAL_ERROR` |

**Trampas:** `InvalidParameterException` tiene constructor público `(String)`; las dependencias de prueba ya traen `tomcat-embed-core`. `presentation` no importa Tomcat en `main`; en pruebas sí se puede. Comprobar que `ArquitecturaTest` sigue en verde.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="BadParameterEncodingTest,ApiExceptionHandlerTest,ErrorCatalogTest,ErrorCodeTest,ErrorCodeDocumentationTest,ArquitecturaTest"` y luego el `*IT` de T1. `ErrorCodeDocumentationTest` falla hasta que T6 agregue la fila del código nuevo a `docs/errores.md`: para dejar el commit compilable y verde, agregar la fila ahora (formato de las filas vecinas):

`| \`REQUEST_QUERY_INVALID_FORMAT\` | 422 | \`GET /api/v1/profiles/professional-roles?lang=\`, \`GET /api/v1/profiles?status=\` | — | «Revisa los datos enviados.» | \`ApiExceptionHandler\` (\`InvalidParameterException\` de un parámetro de consulta mal codificado) | \`ApiExceptionHandlerTest.invalidParameter_shouldReturn422QueryInvalid_whenGet\`, \`ProtocolErrorsEndToEndIT.get_shouldReturn422_whenLangIsMalformed\` |`

**Commit:** `CM-335 | fix(perfil): responder 415 o 422 a un formulario o una consulta mal codificados`.

---

## T3 · Errores del contenedor con la forma común (REQ-07, REQ-08)

**Medir primero (sin cambiar código):** arrancar la aplicación y anotar en el resultado de la tarjeta la respuesta de `TRACE /api/v1/profiles/professional-roles` con `curl -i -X TRACE`: estado, tipo de contenido, cuerpo y si trae `Allow`. **Medido el 17-oct:** 405, `application/json` sin charset, cuerpo por defecto de Spring y `Allow: HEAD, DELETE, POST, GET, OPTIONS, PUT` (la lista genérica de Tomcat, no la de la ruta). Con el controlador nuevo el `Allow` se conserva.

**Reproducción primero:** en `ProtocolErrorsEndToEndIT`:

| Prueba | Petición | Esperado antes | Esperado después |
|---|---|---|---|
| `trace_shouldReturn405WithCommonShape` | `TRACE /api/v1/profiles/professional-roles` | 405 sin `code` | 405, `code` `METHOD_NOT_ALLOWED`, `requestId`, `Content-Type: application/problem+json;charset=UTF-8`, `X-Request-Id` |
| `trace_shouldKeepAllowHeader_whenContainerSentIt` | ídem | — | la respuesta conserva el `Allow` del contenedor (medido: sí lo trae; sin `Assumptions`) |

**Código nuevo** `presentation/advice/ContainerErrorController.java`:

```java
/**
 * Responde con la forma común los errores que el contenedor reporta con {@code sendError} y que
 * Spring Boot mostraría con su cuerpo por defecto ({@code timestamp}, {@code error}, {@code path}):
 * por ejemplo un {@code TRACE}. También es la red de seguridad de cualquier error que ocurra en un
 * filtro, antes de los controladores.
 */
@Hidden
@RestController
class ContainerErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ContainerErrorController.class);

    /** Atiende el despacho de error del contenedor; no es un endpoint del contrato. */
    @RequestMapping("/error")
    ResponseEntity<Object> error(HttpServletRequest request) {
        var original = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        var code = codeFor(original);
        var web = new ServletWebRequest(request);
        var requestId = ProblemResponses.requestId(request.getHeader(ProblemResponses.REQUEST_ID_HEADER));
        // registrar: WARN con code, requestId y firebaseUid (4xx); ERROR con el estado original si cae al genérico
        var problem = ProblemResponses.problem(code, null);
        // instance: la ruta original (RequestDispatcher.ERROR_REQUEST_URI) si existe; conservar Allow si el contenedor lo puso
        return ProblemResponses.respond(problem, requestId);
    }

    /** Estados con código propio; 400 es un dato del cliente; todo lo demás es genérico y se registra. */
    static ErrorCode codeFor(Integer status) { /* null (petición directa a /error, sin error original) ROUTE_NOT_FOUND; 404 ROUTE_NOT_FOUND, 405 METHOD_NOT_ALLOWED, 406 MEDIA_TYPE_NOT_ACCEPTABLE,
        415 MEDIA_TYPE_NOT_ALLOWED, 400 REQUEST_INVALID_VALUE, otro INTERNAL_ERROR */ }
}
```

Completar los comentarios del esqueleto con código real; `problem.setInstance(URI.create(...))` solo si la ruta original es un URI válido (usar `URI.create` con `try/catch (IllegalArgumentException)` y omitir `instance` si no lo es: una ruta puede traer `%zz`). `ProblemResponses` y `REQUEST_ID_HEADER` son paquete-privados: la clase va en el mismo paquete.

**Pruebas unitarias** `ContainerErrorControllerTest` (`MockHttpServletRequest`, atributos `jakarta.servlet.error.status_code` y `jakarta.servlet.error.request_uri`):

| Prueba | Entrada | Esperado |
|---|---|---|
| `error_shouldMapStatus` (`@CsvSource`) | `404,ROUTE_NOT_FOUND`; `405,METHOD_NOT_ALLOWED`; `406,MEDIA_TYPE_NOT_ACCEPTABLE`; `415,MEDIA_TYPE_NOT_ALLOWED`; `400,REQUEST_INVALID_VALUE`; `413,INTERNAL_ERROR`; `503,INTERNAL_ERROR` | `code` y estado del catálogo |
| `error_shouldReturn404_whenNoOriginalStatus` | sin atributos | 404 `ROUTE_NOT_FOUND`, registro `WARN` |
| `error_shouldKeepRequestId_whenHeaderIsValid` | `X-Request-Id: abc-1` | `requestId` `abc-1` |
| `error_shouldGenerateRequestId_whenHeaderIsInvalid` | `X-Request-Id: a b` | UUID nuevo |
| `error_shouldOmitInstance_whenOriginalUriIsMalformed` | `request_uri` `/x/%zz` | sin `instance`, sin excepción |
| `error_shouldLogErrorWithOriginalStatus_whenGeneric` | estado 413 | registro `ERROR` con `413`, sin ruta ni valores |

`OpenApiDocumentIT`: agregar `apiDocs_shouldNotListErrorRoute_whenRequested`: el documento de `/v3/api-docs` no contiene la ruta `/error`.

Prueba de punta a punta `error_shouldReturnCommonShape_whenRouteIsRequestedDirectly`: `GET /error` → 404 `ROUTE_NOT_FOUND` con la forma común.

**Importación (Spring Boot 4.1.1):** `ErrorController` es `org.springframework.boot.webmvc.error.ErrorController` (no `org.springframework.boot.web.servlet.error`). **Trampas:** si el arranque falla con «Ambiguous mapping» para `/error`, dos controladores compiten: `BasicErrorController` no debería crearse; detenerse y reportar. `HttpServlet.doTrace` no interviene: es Tomcat el que responde 405 (`allowTrace=false`). `@Hidden` es de `io.swagger.v3.oas.annotations`; ya es dependencia (springdoc).

**Comando:** `./mvnw.cmd -B -ntp test "-Dtest=ContainerErrorControllerTest,ArquitecturaTest"` y `./mvnw.cmd -B -ntp verify "-Dit.test=ProtocolErrorsEndToEndIT,OpenApiDocumentIT,ResponseCharsetIT" "-Dtest=NoExiste" "-Dsurefire.failIfNoSpecifiedTests=false"`.

**Commit:** `CM-335 | fix(perfil): dar la forma común a los errores del contenedor, como TRACE`.

---

## T4 · Caracteres de control en la identidad (REQ-06)

**Reproducción primero** `src/test/java/.../domain/model/FirebaseUidTest.java` (ampliar; si no existe, crearla):

| Prueba | Entrada | Esperado |
|---|---|---|
| `required_shouldThrow_whenControlCharacter` (`@MethodSource`) | `"uid\t1"`, `"\u0085"`, `"\u009f"`, `"a\u0000"`, `"\n"`, `"\u007f"`, `"\u0001x"` | `IdentityRequiredException` |
| `required_shouldAccept_whenPrintable` | `"uid-123_ABC"`, `"uid-ñ"`, `"a".repeat(128)` | devuelve la identidad |
| `required_shouldThrow_whenNullBlankOrTooLong` | `null`, `""`, `"   "`, `"a".repeat(129)` | `IdentityRequiredException` (sin cambio) |

Punta a punta: `get_shouldReturn401_whenIdentityHasTab` en `ProtocolErrorsEndToEndIT`: `GET /api/v1/profiles/{uuid}` con el encabezado `X-User-Id: uid<TAB>x` → 401 `IDENTITY_REQUIRED`. Además `get_shouldReturn401_whenIdentityHasC1Control`: bytes `0x85` y `0x9f` en el encabezado (con `RawHttpClient`, que escribe ISO-8859-1) → 401.

**Arreglo** `domain/model/FirebaseUid.java`:

```java
    public static FirebaseUid required(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH || hasControlCharacter(raw)) {
            throw new IdentityRequiredException();
        }
        return new FirebaseUid(raw);
    }

    /** Un carácter de control (TAB, saltos de línea, DEL y los controles C1) nunca es parte de un UID de Firebase. */
    private static boolean hasControlCharacter(String raw) {
        return raw.chars().anyMatch(Character::isISOControl);
    }
```

Actualizar el Javadoc de `required` (`@throws … si falta, está en blanco, mide más de 128 caracteres o trae caracteres de control`). El constructor del `record` no cambia: lo usan los adaptadores con valores que ya salieron de la base.

**Trampas:** `String.isBlank` ya rechaza el TAB solo, pero no `"uid\tx"`; por eso se recorre el texto. No usar `Character.isWhitespace` (rechazaría el espacio dentro de un UID, que hoy se acepta).

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="FirebaseUidTest,ProfileAppServiceTest,ProfileControllerTest"`; suite de `ProfileControllerTest` en verde (pruebas existentes de identidad).

**Commit:** `CM-335 | fix(perfil): rechazar caracteres de control en la identidad`.

---

## T5 · Postman (spec §13)

Agregar a `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` la carpeta «Errores de protocolo» con P-01, P-02 y P-03 (cuerpo en modo `raw` con el encabezado `Content-Type: application/x-www-form-urlencoded` escrito a mano; la URL de P-02 con `%zz` literal). Cada petición comprueba estado, `code`, `requestId` y `Content-Type` con `charset=UTF-8`. Una petición previa de la carpeta crea el perfil de `uid-cm335-a` para que P-01 y P-03 usen un `{{profileId}}` real; P-02 no necesita identidad.

Correr: levantar el jar empaquetado y la base de `docker compose`; `newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "Errores de protocolo"`; guardar la salida en `ejecucion/evidencia/CM-335/`. Si P-02 responde 200, aplicar la trampa de la spec §13 y reportar.

**Commit:** `CM-335 | test(perfil): colección de Postman de los errores de protocolo`.

---

## T6 · Documentos (REQ-09)

1. `docs/errores.md`: fila de `REQUEST_QUERY_INVALID_FORMAT` (si T2 no la agregó ya); en la fila de `MEDIA_TYPE_NOT_ALLOWED`, agregar «también un formulario mal codificado en un `POST`»; en la de `METHOD_NOT_ALLOWED`, agregar «incluido `TRACE`»; en la de `IDENTITY_REQUIRED`, agregar «o con caracteres de control»; en «Respaldo para los errores del framework», una línea sobre `/error`. Sección nueva **«Rechazos que ocurren antes de la aplicación»** con el contenido de la spec §12 (tabla: petición, respuesta, quién la produce, qué hace el Gateway).
2. `docs/adr/0008-errores-de-protocolo-previsibles.md` (formato del ADR 0002: Estado, Contexto, Decisión, Consecuencias, Alternativas descartadas) con D-1 a D-6 de la spec.
3. `docs/bitacora-ia/`: entrada `NN_errores-de-protocolo-previsibles_prompt.md` con el siguiente número del `README.md` (solo el prompt armado: rol, contexto, tarea, condiciones, formato) y su fila en la tabla del `README.md`, con salida `../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md`. La decisión humana (HITL) queda `PENDIENTE (Paula)`.
4. `CLAUDE.md` del repo, sección 4, tabla de endpoints: reemplazar la fila de `GET` de la raíz por **este texto exacto** (la versión actual lista solo `IN_PROGRESS` y `COMPLETED`, y el filtro acepta cuatro valores; el segundo código se escribe porque este PR agrega `REQUEST_QUERY_INVALID_FORMAT`):

```text
| `GET` | (la raíz, `/api/v1/profiles`) | Lista los perfiles del usuario, del más antiguo al más reciente, con sus roles objetivo; `?status=PENDING\|IN_PROGRESS\|IN_REVIEW\|COMPLETED` filtra (422 `PROFILE_STATUS_INVALID_VALUE` si el valor no es uno de los cuatro, en mayúsculas y exacto; 422 `REQUEST_QUERY_INVALID_FORMAT` si la consulta está mal codificada); sin paginación y sin caché |
```

   Comprobado el 17-oct: `docs/adr/0007-lista-de-perfiles.md` ya dice los cuatro valores y `docs/errores.md` no los lista (su fila de `PROFILE_STATUS_INVALID_VALUE` no necesita cambio). No hay prueba nueva: es solo documentación y `ProfileStatusFilterTest` ya cubre los cuatro valores. La descripción del PR lleva, en «Hallazgos que aplican a esta tarea»: «Corrige la fila de GET /api/v1/profiles del CLAUDE.md, que listaba solo dos valores de status.»

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="ErrorCodeDocumentationTest"`.

**Commit:** `CM-335 | docs(perfil): catálogo de errores, ADR 0008 y bitácora`.

---

## T7 · Cierre (una vez, sobre la punta)

1. `./mvnw.cmd -B -ntp clean verify` completo; cobertura por clase tocada (≥ 90 % de líneas y ramas) desde `target/site/jacoco/jacoco.csv`; cada línea o rama sin cubrir con su razón.
2. Newman (T5) contra el jar empaquetado y la base real.
3. Peticiones reales a cada fila del §2 de la spec (E-1 a E-9, E-4b) con el jar empaquetado (los bytes `0x85`/`0x9f` de E-6 se envían con un socket crudo, no con `curl`: en Windows `curl` los reescribe y la petición deja de ser mala); guardar la salida en `ejecucion/evidencia/CM-335/`. Ninguna debe dar 500 ni la forma por defecto de Spring; las de §12 deben dar el 400 HTML documentado.
4. Lista de verificación de la spec (V-01 a V-15), fila por fila.
5. `ejecucion/evidencia/CM-335/resumen.md`: lo hecho por tarjeta, cobertura por clase, hallazgos, líneas o ramas sin cubrir, commits y la tabla regla R1–R14 → evidencia con salida real. No marcar «cumple» lo que no se ejecutó.
6. Sin push ni PR: se pregunta a Paula con el resumen.

---

## Resultados de la ejecución (18-oct-2026)

| Tarjeta | Antes del arreglo | Después | Commit |
|---|---|---|---|
| T0 | Línea base `clean verify` en `bbe74c6`: 322 pruebas de integración, 0 fallos, 0 omitidas | — | — |
| T1 | 4 de 6 pruebas de `ProtocolErrorsEndToEndIT` fallan (500 sin `code`); las 2 de control pasan | 6 de 6 | filtro de formularios apagado |
| T2 | 16 de 26 fallan (formulario en `POST`, consultas `%zz`/`%C3`/`%FF`, `status`, parámetro largo, 20 repeticiones, 50 en paralelo, registro, 2 multipart) | 26 de 26; unitarias 97 en verde | 415 o 422 a formulario, consulta y multipart |
| T3 | `trace_…` y `error_…` fallan (el cuerpo es el de Spring por defecto) | `ContainerErrorControllerTest` 13 de 13; `ProtocolErrorsEndToEndIT` 28 de 28; `OpenApiDocumentIT` 12 de 12 | forma común a los errores del contenedor |
| T4 | 7 casos de `FirebaseUidTest` fallan (la identidad con caracteres de control se aceptaba) | `FirebaseUidTest` 23 de 23; TAB, `0x85` y `0x9f` por HTTP: 401 | caracteres de control en la identidad |
| T5 | — | Newman, carpeta «Errores de protocolo» (7 peticiones): 39 aserciones, 0 fallos | colección de Postman |
| T6 | — | `ErrorCodeDocumentationTest` en verde | catálogo, ADR 0008 y bitácora |

Desviaciones de la tarjeta T5: P-01 y P-03 usan un identificador de perfil fijo en vez de crear el perfil de `uid-cm335-a`, porque el 415 se decide antes de ejecutar el caso de uso y así la carpeta no depende de otra; la URL de las peticiones con consulta lleva el arreglo `query` de Postman, sin el cual Newman descarta `?lang=%zz` y responde 200. Se agregaron P-04 (`%C3`), P-05 (multipart sin `boundary`), P-06 (`TRACE`) y P-07 (`GET /error`).
