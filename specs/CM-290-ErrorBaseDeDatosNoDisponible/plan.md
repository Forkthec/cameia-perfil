# Plan · CM-290 503 `DATABASE_UNAVAILABLE` en Perfil

Estado: **APROBADO por Paula el 19-oct-2026.** Todo lo que depende de una decisión pendiente (spec §17) se construye con la opción recomendada; si Paula elige otra, solo cambian las tarjetas indicadas.

## 1. Enfoque

Un clasificador paquete-privado, `DatabaseUnavailability`, recorre la excepción, sus causas y sus excepciones suprimidas y dice si es una indisponibilidad de la base (spec REQ-02). El manejador general (`ApiExceptionHandler.handleUnexpected`) lo consulta después del parámetro mal codificado y antes del 500: si coincide, responde 503 `DATABASE_UNAVAILABLE`. El pool de conexiones espera 5 s y no 30 s. Nada más cambia.

## 2. Reutilizar → extender → crear

| Pieza | Decisión | Por qué |
|---|---|---|
| `ApiExceptionHandler.handleUnexpected`, `requestId`, `markLogged`, `firebaseUid`, `problem`, `respond` | Se **extienden** (una rama nueva) | Ya arman la forma común, el `requestId` y el registro único |
| `ErrorCatalog` | Se **extiende** con una entrada (`DATABASE_UNAVAILABLE`, 503, «Servicio no disponible», «Ocurrió un error. Inténtalo de nuevo.») | Un solo lugar para título, estado y mensaje; `ErrorCatalogTest` lo vigila |
| `ErrorCode` | Se **extiende** (junto a `BIRTH_DATE_UNAVAILABLE`) | Catálogo cerrado; `ErrorCodeDocumentationTest` lo vigila |
| `RedactedException.of` | Se **reutiliza** para la traza del registro | Ya descarta los mensajes de la cadena |
| `RedactedException.sqlState` | **No sirve** para este caso: solo sigue `getCause()` y la causa puede venir en `getSuppressed()` (H-1) | El registro del 503 usa `DatabaseUnavailability.sqlState`; no se modifica `RedactedException` (otros 500 no cambian) |
| `BadParameterEncoding` | **Modelo de estilo** del clasificador nuevo | Mismo problema: reconocer una excepción de una librería sin acoplar la capa |
| `OpenApiConfig.protocolErrorResponses` y `protocolError` | Se **extienden** con el 503 (con unión si la operación ya trae 503) | Un solo customizer cubre las 15 operaciones |
| `RawHttpClient`, `OpenApiDocumentIT` | **Modelo** de las pruebas de punta a punta y de OpenAPI | Ya existen |
| `DatabaseUnavailability` | Se **crea** | Ningún componente recorre `getSuppressed()` |

## 3. Archivos

| Acción | Ruta |
|---|---|
| Crear | `src/main/java/co/edu/unicauca/cameia/perfil/presentation/advice/DatabaseUnavailability.java` |
| Modificar | `…/presentation/advice/ApiExceptionHandler.java`, `…/presentation/advice/ErrorCatalog.java` |
| Modificar | `…/domain/exception/ErrorCode.java`, `…/infrastructure/config/OpenApiConfig.java` |
| Modificar | `src/main/resources/application.yml` |
| Modificar | `docs/errores.md`, `CLAUDE.md` (sección 4, una línea), `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` |
| Crear | `docs/adr/0009-base-de-datos-no-disponible.md` |
| Crear (pruebas) | `src/test/java/…/presentation/advice/DatabaseUnavailabilityTest.java`, `…/presentation/DatabaseUnavailableEndToEndIT.java`, `…/infrastructure/config/DatabasePoolConfigurationIT.java` |
| Ampliar (pruebas) | `ApiExceptionHandlerTest`, `ErrorCatalogTest`, `ProfileControllerTest`, `OpenApiDocumentIT` |
| No tocar | Migraciones, `.github/`, `pom.xml`, `docker-compose.yml`, `.env.example`, `RedactedException` |

## 4. Orden y commits (un PR, un commit por capacidad)

| Commit | Tarjetas | Capacidad |
|---|---|---|
| 1 | T1 | Pool de conexiones con espera de 5 s y su prueba |
| 2 | T2 | Clasificador de indisponibilidad y sus pruebas unitarias |
| 3 | T3 | Código, entrada de catálogo, rama del manejador y sus pruebas (incluida la de `ProfileControllerTest`) |
| 4 | T4 | Pruebas de punta a punta con PostgreSQL real |
| 5 | T5 | OpenAPI: el 503 en las 15 operaciones |
| 6 | T6 | Postman, ADR, `docs/errores.md` y `CLAUDE.md` |

T1 y T2 son independientes; T3 depende de T2; T4 de T1 y T3; T5 de T3; T6 al final. T7 (verificación final, una vez sobre la punta) no es commit.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un falso 503 sobre un fallo que sí es defecto | Lista cerrada (REQ-02) y negativos medidos (23502, 42601, 55P03) |
| 5 s son pocos en el arranque en frío de Cloud Run | D-3 lo deja a decisión de Paula; la variable estándar permite subirlo en el despliegue sin tocar código |
| `ErrorCodeDocumentationTest` falla entre T3 y T6 | Las tarjetas T3 a T5 corren solo sus pruebas; la suite completa se corre tras T6 |
| Las `*IT` solo corren con Failsafe | Se ejecutan con `./mvnw.cmd -B -ntp verify -Dit.test=…` y se comprueba en la salida que no fueron omitidas |
| Pausar el contenedor deja el pool roto para otras pruebas | La clase usa su propio contexto y contenedor; `@AfterEach` reanuda |
| Los dos 503 (base y fecha de nacimiento) se confunden en OpenAPI | La unión en el customizer los muestra como dos ejemplos con su `code` |
