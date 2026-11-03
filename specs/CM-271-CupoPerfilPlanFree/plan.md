# Plan — CM-271 (cameia-perfil)

- **Spec:** `spec.md` de esta carpeta, aprobada por Paula el 7-oct-2026.
- **Estado del plan:** pendiente de aprobación de Paula.
- **Base:** `origin/develop` (`5b2f6b7` al escribir este plan).
- **Tarjetas:** `tasks.md`.

Paquete base: `src/main/java/co/edu/unicauca/cameia/perfil/`; pruebas en `src/test/java/co/edu/unicauca/cameia/perfil/`.

## 1. Cómo se entrega

Tres PR apilados, cada uno de menos de 1.000 líneas. Solo el primero apunta a `develop`:

```
develop ← PR 0 (Testcontainers + spec) ← PR A (formato de error) ← PR B (cupo y creación sin duplicados)
```

| PR | Rama | Qué existe hoy | Qué falta | Estimación |
|---|---|---|---|---|
| 0 | `CM-271-testcontainers-perfil` | `1693767`: Testcontainers con una configuración compartida; las `*IT` solo corren con `-Dtest` | Failsafe en `clean verify`, `@Testcontainers` por clase, servicio `verify` de compose, `README`, la spec y el plan, y la carpeta renombrada a `specs/CM-271-CupoPerfilPlanFree` | ≈ 1 h |
| A | `CM-271-formato-error-perfil` | 5 commits (824 líneas): catálogo, excepción base, manejador con `code`, `requestId`, 422 y 500, OpenAPI parcial, `charset` en errores e identidad en blanco → 401 | Rebase, identidad en 401 (ausente y > 128), fecha mal escrita, errores del framework, finalización e idioma con la forma común, `firebaseUid` en el log, `charset` en éxito, pruebas de los códigos de campo, Javadoc y limpieza, documentación | ≈ 4,5 h |
| B | `CM-271-cupo-perfil-plan-free` (se rehace desde A) | Nada de código | Todo (REQ-PE-20 a 28) | ≈ 3 h |

Si el PR A pasa de 1.000 líneas al terminar, se parte en **A1** (lo que ya existe, el rebase, la división del manejador, la identidad y la fecha) y **A2** (lo demás). Se mide antes de decidir.

**Puntos de parada.** Al terminar cada PR, en local, se le muestra a Paula:
- qué cambió;
- la salida real de `clean verify`;
- la cobertura;
- qué archivos revisar.

No hay `push` ni PR sin que Paula diga que sí, cada vez.

## 2. Cómo se aborda

**PR 0.**
1. Se rebasa sobre `develop` y se quita `[IA-ASISTIDO]` del mensaje del commit (va solo en el título del PR).
2. Se agrega `maven-failsafe-plugin` para que las `*IT` corran en `verify`.
3. Cada `*IT` declara su contenedor con `@Testcontainers`, `@Container` y `@ServiceConnection`.
4. El servicio `verify` de `docker-compose.yml` monta el socket de Docker para que Testcontainers funcione dentro del contenedor.
5. La spec, el plan y las tarjetas entran en este PR, para que quien revise vea primero qué se va a hacer.

**PR A.**
1. **Rebase de los 5 commits.** Se resuelve el único conflicto (`docs/errores.md`) y se quita `[IA-ASISTIDO]` de los mensajes.
2. **División del manejador.** `ApiExceptionHandler` ya mide 200 líneas, el máximo por clase del repositorio, así que se separa en dos:
   - `ErrorCatalog`: las tablas de estados, títulos, códigos y mensajes de campo, y los códigos de los identificadores de ruta;
   - `ApiExceptionHandler`: los métodos que atrapan cada excepción y un solo método que arma la respuesta.
3. Se agregan los requisitos que faltan, uno por tarjeta, cada uno con sus pruebas.

**PR B.**
1. El puerto `ProfessionalProfileRepository` cambia `existsByFirebaseUid` por tres métodos: `countByFirebaseUid`, `lockCreationFor` y `findLatestByFirebaseUid`.
2. El adaptador ejecuta el bloqueo con `EntityManager`, una consulta nativa que no tiene las trampas de un `@Query` con retorno `void`.
3. `ProfileAppService.createProfile` aplica la regla de REQ-PE-26 en una transacción.
4. **Prueba de integración.** Con PostgreSQL real comprueba la concurrencia, Usuarios distintos y la creación que se deshace.
5. **Prueba manual.** El script de la carrera se corre con la app levantada.

## 3. Archivos

| PR | Acción | Archivo |
|---|---|---|
| 0 | Modificar | `pom.xml`, `docker-compose.yml`, `README.md`, `ProfessionalProfileRepositoryAdapterIT` |
| 0 | Quitar | `PostgresTestConfiguration` (cada `*IT` declara su contenedor) |
| 0 | Mover | `specs/CM-271-cupo-perfil-plan-free/` → `specs/CM-271-CupoPerfilPlanFree/` (`spec.md`, `plan.md`, `tasks.md`; `HANDOFF.md` y `HALLAZGOS.md` no se publican: son notas de trabajo y se archivan fuera del repositorio) |
| A | Crear | `presentation/advice/ErrorCatalog.java`, `domain/exception/UnsupportedLanguageException.java`, `docs/adr/<siguiente número>-validacion-en-422.md` |
| A | Modificar | `presentation/advice/ApiExceptionHandler.java`, `domain/exception/*` (Javadoc), `domain/exception/ErrorCode.java`, `application/service/ProfileAppService.java` (identidad), `application/command/CreateProfileCommand.java`, `presentation/controller/ProfileController.java` (OpenAPI), `presentation/controller/ProfessionalRoleController.java`, `src/main/resources/application.yml` (`charset`), `docs/errores.md` (incluye las dos causas nuevas `ALREADY_COMPLETED` e `INCOMPLETE`, D9) |
| A | Quitar | `presentation/dto/CompletionErrorResponse.java` (el 422 de finalización pasa a la forma común) |
| A | Pruebas | `ApiExceptionHandlerTest`, `ErrorCatalogTest` (nueva), `ProfileControllerTest`, `ProfessionalRoleControllerTest` (o la que exista), `ProfileAppServiceTest`, `ResponseCharsetIT` (nueva, servidor real en puerto aleatorio) |
| B | Renombrar y modificar | `domain/exception/ProfileAlreadyExistsException.java` → `ProfileLimitReachedException.java` |
| B | Modificar | `domain/port/ProfessionalProfileRepository.java`, `infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java`, `ProfessionalProfileRepositoryAdapter.java`, `application/service/ProfileAppService.java`, `ProfileController.java` (OpenAPI del 201 y del 409), `docs/errores.md`, `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` |
| B | Crear | `infrastructure/persistence/ProfileCreationConcurrencyIT.java`, `docs/adr/<siguiente número>-bloqueo-de-creacion-de-perfil.md` |
| B | Pruebas | `ProfileAppServiceTest`, `ProfileControllerTest` |
| — | No se tocan | `.github/**`, migraciones, `cameia-web`, `docs/estandar-backend.md` (es común a los cuatro repositorios con la misma suma; las dos causas nuevas se llevan al estándar común en CM-283) |

## 4. Reutiliza

- `ProblemDetail` y `ResponseEntityExceptionHandler` de Spring.
- El manejador, el catálogo y la excepción base que ya existen en la rama del PR A.
- `ProfileControllerTest` en modo `standaloneSetup` y `ProfileAppServiceTest` con dobles de los puertos.
- `ProfessionalProfileRepositoryAdapterIT`.
- El índice `idx_perfil_firebase_uid` que ya existe.
- El script `prueba_carrera_doble_envio.py` de la carpeta de respaldo.

Se crean `ErrorCatalog`, porque el manejador llegó al límite de líneas, y `UnsupportedLanguageException`, porque el catálogo de roles no tiene excepción para el idioma.

## 5. Decisiones técnicas

| # | Decisión | Porqué | Descartado |
|---|---|---|---|
| T1 | El estado HTTP por código vive en la capa de presentación (`ErrorCatalog`) | `domain` no puede importar `HttpStatus` (`ArquitecturaTest`) | Estado dentro de la excepción |
| T2 | Un solo `@ExceptionHandler(BusinessException.class)` y una prueba que recorre `ErrorCode.values()` | Una excepción nueva solo agrega su fila; la prueba falla si falta | Un método por excepción |
| T3 | `hashtextextended(uid, 0)` (64 bits) para el bloqueo | Menos colisiones que `hashtext` (32 bits); una colisión solo hace esperar | `hashtext` |
| T4 | El bloqueo se ejecuta con `EntityManager.createNativeQuery(...).getSingleResult()` en el adaptador | Evita la trampa de un `@Query` nativo con retorno `void` en Spring Data | `@Query` + `@Modifying` |
| T5 | La identidad se valida en un solo método del servicio de aplicación (`requireIdentity`), que devuelve `FirebaseUid` o lanza `IdentityRequiredException` | Hoy hay dos comprobaciones distintas y un `X-User-Id` de 129 caracteres da 422 en la creación y 403 en las demás rutas | Validar en el controlador; dejar que `FirebaseUid` lance `IllegalArgumentException` |
| T6 | `charset` de éxito con `server.servlet.encoding.force-response: true`, comprobado con una prueba de servidor real | Una línea de configuración cubre todas las respuestas; `MockMvc` en `standaloneSetup` no pasa por el filtro de codificación | `produces` en cada método |
| T7 | Las `*IT` declaran su propio contenedor con `@Testcontainers` | Lo pide el estándar (P2-09); cada clase es autosuficiente | Configuración compartida con `@Import` |

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Testcontainers no funciona dentro del servicio `verify` en Docker Desktop de Windows | Montar `/var/run/docker.sock` y fijar `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`; si aun así falla, detenerse y reportar con la salida real |
| `force-response` no agrega `charset` a `application/json` en el servidor embebido | La prueba `ResponseCharsetIT` lo detecta; si falla, detenerse y reportar (alternativa: un filtro propio de una clase) |
| El rebase del PR A rompe pruebas por cambios de CM-283 en `develop` | Correr la suite completa después del rebase, antes de tocar nada más |
| El PR A supera 1.000 líneas | Partir en A1 y A2 (sección 1) |
| `cameia-web` espera 400 en la validación de dos formularios | Ya comunicado el 6-oct (fila 12 del documento a Frontend) |
| El texto del 409 en pantalla depende de Frontend | Borrador para Frontend redactado, pendiente de envío por Paula |

## 7. Matriz de pruebas

Los casos 1 a 31 de la sección 9 de la spec, en las tarjetas que los nombran. Cobertura de lo nuevo o modificado ≥ 90 % de líneas y de ramas con JaCoCo.

## 8. Orden y estimación

PR 0 (≈ 1 h) → PR A (≈ 4,5 h) → PR B (≈ 3 h). Total ≈ 8,5 h. La HU estima 2 h para el ajuste de HU-2.2. El resto es el formato de error común y la infraestructura de pruebas que Paula decidió incluir (D6).
