# Plan — CM-271 (cameia-perfil)

Base: `origin/develop` `42320d9`. Estado: pendiente de aprobación de Paula. Dos PR: **A** (formato de error, ≈ 3,5 h, ≈ 550 líneas) y **B** (cupo y concurrencia, ≈ 2 h, ≈ 300 líneas). Si se aprueba la pregunta 1, antes va un PR corto de Testcontainers (P2-09 adelantado, ≈ 1 h, ≈ 120 líneas), que sirve también a CM-274, CM-66 y CM-67.

Paquete base: `src/main/java/co/edu/unicauca/cameia/perfil/`; pruebas en `src/test/java/co/edu/unicauca/cameia/perfil/`.

## 1. Cómo se aborda

**PR A — formato de error.**
1. `domain/exception/ErrorCode.java` (enumerado con todos los códigos de la spec, sección 5: los 15 de operación y los 8 de campo) y `domain/exception/BusinessException.java` (abstracta, extiende `RuntimeException`, guarda `ErrorCode` y el `HttpStatus` **no**: el estado lo decide el manejador por código, para que `domain` no importe Spring).
2. Las 11 excepciones existentes pasan a extender `BusinessException` con su código; sus textos no cambian (salvo la del cupo, en el PR B).
3. `ApiExceptionHandler`: un único `@ExceptionHandler(BusinessException.class)` que toma el código, busca el estado en un `Map<ErrorCode, HttpStatus>` y arma el `ProblemDetail`; `IncompleteProfileException` conserva su cuerpo `missingRequirements` (lo cambia CM-67) más `code`. Validación (`handleMethodArgumentNotValid`) → 422 con `errors[]` y tabla `campo.restricción → ErrorCode`; cuerpo ilegible → 422; `IllegalArgumentException` → 422 genérico; `Exception` → 500 genérico.
4. `requestId`: método privado del manejador que lee `X-Request-Id` del `HttpServletRequest`, lo valida con `^[A-Za-z0-9._-]{1,64}$` o genera `UUID.randomUUID()`, lo pone en el cuerpo y en el encabezado de la respuesta.
5. Log: `WARN` sin traza para negocio y validación, `ERROR` con traza para el 500; siempre con `code` y `requestId`.

**PR B — cupo.**
1. `ProfileAlreadyExistsException` → `ProfileLimitReachedException` con «Tu Plan Free permite 1 Perfil Profesional.» y `PROFILE_LIMIT_REACHED`.
2. Puerto `ProfessionalProfileRepository`: `long countByFirebaseUid(FirebaseUid)` y `void lockCreationFor(FirebaseUid)`; se quita `existsByFirebaseUid` (único llamador: `createProfile`).
3. Adaptador: `lockCreationFor` ejecuta `select pg_advisory_xact_lock(hashtextextended(:uid, 0))` con una consulta nativa del `JpaRepository`; `countByFirebaseUid` derivado de Spring Data.
4. `ProfileAppService.createProfile`: `lockCreationFor(uid)` → `count` → si `count >= FREE_PLAN_MAX_PROFILES` (1) lanza → crea y guarda. La constante vive en el servicio de aplicación con su Javadoc (el cupo Premium la convertirá en una política).

## 2. Archivos

| PR | Acción | Archivo |
|---|---|---|
| A | Crear | `domain/exception/ErrorCode.java`, `domain/exception/BusinessException.java` |
| A | Modificar | Las 11 clases de `domain/exception/` (constructor con código) |
| A | Modificar | `presentation/advice/ApiExceptionHandler.java` |
| A | Modificar | `presentation/controller/ProfileController.java` (solo OpenAPI de `POST /api/v1/profiles`) |
| A | Crear | `src/test/.../presentation/advice/ApiExceptionHandlerTest.java` |
| A | Modificar | `ProfileControllerTest` (las afirmaciones de 400 de validación pasan a 422 y agregan `code`) |
| B | Renombrar y modificar | `domain/exception/ProfileAlreadyExistsException.java` → `ProfileLimitReachedException.java` |
| B | Modificar | `domain/port/ProfessionalProfileRepository.java`, `infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java` y `ProfessionalProfileRepositoryAdapter.java`, `application/service/ProfileAppService.java` |
| B | Modificar | `ProfileAppServiceTest`, `ProfileControllerTest` |
| B | Crear | `src/test/.../infrastructure/persistence/ProfileCreationConcurrencyIT.java` |
| — | No se tocan | `ProfessionalRoleController` (pregunta 4), migraciones, `pom.xml` (salvo el PR de Testcontainers), `.github/**` |

## 3. Reutiliza

`ProblemDetail` de Spring (ya habilitado en `application.yml`), `ResponseEntityExceptionHandler` (ya extendido), `ProfileControllerTest` en modo `standaloneSetup` con el manejador real, `ProfileAppServiceTest` con dobles de los puertos, `ProfessionalProfileRepositoryAdapterIT`. Se crean `ErrorCode` y `BusinessException` porque Perfil no tiene ningún catálogo ni raíz de excepciones.

## 4. Decisiones técnicas

| # | Decisión | Porqué | Descartado |
|---|---|---|---|
| T1 | El estado HTTP por código vive en el manejador (`Map<ErrorCode, HttpStatus>`) | `domain` no puede importar `HttpStatus` (`ArquitecturaTest`) | Estado dentro de la excepción |
| T2 | Un solo `@ExceptionHandler(BusinessException.class)` | Una excepción nueva no exige tocar el manejador más que su fila en el mapa; una prueba recorre `ErrorCode.values()` y falla si falta una fila | Un método por excepción (como hoy) |
| T3 | `hashtextextended(uid, 0)` (64 bits) | Menos colisiones que `hashtext` (32 bits); una colisión solo serializa dos Usuarios, no da un resultado incorrecto | `hashtext` |

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Frontend espera 400 en validación | Aviso en el documento a Frontend; el cambio lo pide RT-01-CA05 (pregunta 2) |
| `ProfileControllerTest` tiene afirmaciones sobre `title` y 400 | La tarjeta lista cuáles cambian; los `title` se conservan |
| Sin Testcontainers no hay prueba automática de concurrencia en el CI | Pregunta 1; si se rechaza, la prueba `*IT` se ejecuta a mano con `docker compose run --rm verify` y se adjunta la salida al PR |

## 6. Matriz de pruebas

| Prueba | Capa | Requisito |
|---|---|---|
| Cada `ErrorCode` tiene estado en el mapa del manejador y cumple `^[A-Z]+(_[A-Z]+)+$` | unidad | REQ-PE-01, 08 |
| Cada excepción de negocio responde su estado y su `code` | manejador (`standaloneSetup` con un controlador de prueba) | REQ-PE-01, 08, 09 |
| `X-Request-Id: abc-123` → cuerpo y encabezado `abc-123`; `<script>`, 65 caracteres y ausente → UUID v4 | manejador | REQ-PE-02 |
| `POST /work-experiences` sin `company` → 422, `errors[0].field` `company`, `errors[0].code` `VALIDATION_FAILED` del campo (ver tarjeta) | controlador | REQ-PE-03 |
| JSON mal formado en `PATCH /api/v1/profiles/{id}` → 422 `REQUEST_BODY_INVALID_FORMAT`, `detail` sin «JSON» ni «Jackson» | controlador | REQ-PE-04 |
| `IllegalArgumentException("co.edu.unicauca… No enum constant")` → 422 `REQUEST_INVALID_VALUE`, `detail` sin `co.edu` | manejador | REQ-PE-05 |
| `RuntimeException("SQL secreto")` → 500 `INTERNAL_ERROR`, sin «SQL» | manejador | REQ-PE-06 |
| Segundo perfil → 409 `PROFILE_LIMIT_REACHED` con el texto literal; nada se guarda | servicio y controlador | REQ-PE-20, 24 |
| El servicio llama a `lockCreationFor` antes de `countByFirebaseUid` (`InOrder`) | servicio | REQ-PE-21 |
| Cinco hilos crean a la vez para `uid-concurrencia` → un perfil en la base, un 201 y cuatro 409 | integración con base real | REQ-PE-21 |
| Usuario B crea aunque A tenga perfil | servicio | REQ-PE-23 |
| `GET` del perfil recién creado: `name` y `summary` nulos, colecciones vacías, `IN_PROGRESS` | controlador | REQ-PE-22 |

## 7. Orden

(Testcontainers, si se aprueba) → A → B. B depende de A (usa `BusinessException`). CM-54, CM-274, CM-66 y CM-67 dependen de A.

## 8. Estimación

≈ 5,5 h (A 3,5 h, B 2 h) más ≈ 1 h de Testcontainers. La HU estima 2 h para el ajuste de HU-2.2; el formato de error es trabajo del estándar (H-14 de CM-283) que esta tarea absorbe.
