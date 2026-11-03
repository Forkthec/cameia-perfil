# Plan · CM-335 Errores de protocolo previsibles en Perfil

Spec: `spec.md` (misma carpeta). Rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop` con CM-201 ya fusionada. Un PR; sin dependencias salvo esa fusión.

## 1. Enfoque

Cuatro piezas, de la causa más grave a la menos grave, cada una un commit:

1. **Configuración:** apagar `FormContentFilter` (elimina la excepción que ocurre antes del manejador).
2. **Manejador:** `InvalidParameterException` → 415 si venía de un formulario, 422 `REQUEST_QUERY_INVALID_FORMAT` si venía de la consulta. La detección por nombre de clase vive en una clase pequeña, `BadParameterEncoding`.
3. **Contenedor:** `ContainerErrorController` da la forma común a lo que Tomcat reporta con `sendError` (caso `TRACE`) y es la red de seguridad si el filtro se reactiva.
4. **Identidad:** `FirebaseUid.required` rechaza caracteres de control.
5. **Pruebas con petición real, Postman y documentos.**

## 2. Reutilización

| Se reutiliza | Por qué |
|---|---|
| `ApiExceptionHandler.handleUnexpected`, `reject`, `problem`, `respond` | Ya arman la forma común y registran con `code`, `requestId` y `firebaseUid` |
| `ErrorCatalog.RESPONSES`, `ErrorCode` | Un solo catálogo; solo se agrega un código |
| `ProblemResponses` (`requestId`, `firebaseUid`, `respond`) | El controlador de errores del contenedor vive en el mismo paquete para usarlos |
| `IdentityRequiredException` | Ya produce el 401 |
| `ApiTestClient` | **No** sirve para las pruebas nuevas: usa `java.net.URI`, que rechaza `%zz` y no envía bytes crudos. Por eso se crea `RawHttpClient` (sockets), una clase de prueba de unos 60 líneas |

No se crea excepción de negocio nueva: el rechazo de parámetros es una regla del protocolo, no del dominio (spec §10).

## 3. Archivos

Bajo `src/main/java/co/edu/unicauca/cameia/perfil/` salvo indicación.

| Acción | Ruta |
|---|---|
| modificar | `src/main/resources/application.yml` (`spring.mvc.formcontent.filter.enabled: false`) |
| crear | `presentation/advice/BadParameterEncoding.java` |
| modificar | `presentation/advice/ApiExceptionHandler.java` (`handleUnexpected`; manejador nuevo de `MultipartException`) |
| crear | `presentation/advice/ContainerErrorController.java` |
| modificar | `presentation/controller/ProfessionalRoleController.java` y `ProfileController.java` (solo el texto del `@ApiResponse` 422 de `listAll` y de la lista de perfiles: agregar `REQUEST_QUERY_INVALID_FORMAT`) |
| modificar | `domain/exception/ErrorCode.java` (+`REQUEST_QUERY_INVALID_FORMAT`, +`REQUEST_MULTIPART_INVALID_FORMAT`) |
| modificar | `presentation/advice/ErrorCatalog.java` (+2 filas) |
| modificar | `domain/model/FirebaseUid.java` (`required`) |
| pruebas | ver `tasks.md` |
| modificar | `docs/errores.md`, `CLAUDE.md` (fila de `GET /api/v1/profiles`), `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, `docs/bitacora-ia/README.md` |
| crear | `docs/adr/0008-errores-de-protocolo-previsibles.md`, entrada de la bitácora de IA |

No se tocan: migraciones, eventos, `.github/`, DTO ni la lógica de los controladores (solo la descripción de sus respuestas OpenAPI).

## 4. Commits (uno por capacidad, en orden de lectura)

| Tarjeta | Commit |
|---|---|
| T1 | `CM-335 \| fix(perfil): apagar el filtro de formularios de Spring y cubrirlo con pruebas de punta a punta` |
| T2 | `CM-335 \| fix(perfil): responder 415 o 422 a un formulario o una consulta mal codificados` |
| T3 | `CM-335 \| fix(perfil): dar la forma común a los errores del contenedor, como TRACE` |
| T4 | `CM-335 \| fix(perfil): rechazar caracteres de control en la identidad` |
| T5 | `CM-335 \| test(perfil): colección de Postman de los errores de protocolo` |
| T6 | `CM-335 \| docs(perfil): catálogo de errores, ADR 0008 y bitácora` |

La prueba que reproduce el defecto se escribe antes del arreglo dentro de cada tarjeta y entra en el mismo commit.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Dos `ErrorController` (el de Spring Boot y el nuestro) | Spring Boot solo crea el suyo si no existe otro `ErrorController`; T3 comprueba el arranque y que `GET /error` responde la forma común |
| `Allow` se pierde en el despacho de error de Tomcat | Medido el 17-oct: no se pierde (se conserva la lista genérica de Tomcat). T3 lo vigila con `trace_shouldKeepAllowHeader_whenContainerSentIt` |
| `Content-Type` de formulario con `charset` o mayúsculas | La comparación es por prefijo sin distinguir mayúsculas; hay prueba |
| Newman reescribe `%zz` | Spec §13: se anota y se prueba con `curl`; la cobertura real la da el `*IT` |
| Pruebas `*IT` sin Docker | Docker encendido; un `*IT` omitido no cuenta como verde |
| Algún `@RequestBody` futuro sin `consumes` | El manejador general lo cubre (415) |
