# CM-201 · Lista de perfiles del usuario (`GET /api/v1/profiles`)

| Dato | Valor |
|---|---|
| Tarea Jira | CM-201 (subtarea de CM-262, HU-4.2), Sprint 3, responsable Paula Andrea Muñoz Delgado |
| Historia | HU-4.2 «Configurar la sesión: Perfil Profesional, Oferta Laboral y Rol Objetivo» (backlog v8.2, hoja HE-04); la consume también HU-2.13 |
| Repositorio | `cameia-perfil`, base `origin/develop` = `0854e5c` |
| Depende de | Nada sin fusionar |
| Estado | **LISTA PARA EJECUTAR. Aprobada por Paula el 17-oct.** La línea base con Docker se corre en T0 |
| Estimación | Jira: 1 h 42 min. Backend: 5 a 6 h (endpoint, consulta sin N+1, pruebas por capa, punta a punta, Postman y documentos). Se informa a Vela |

## 1. Objetivo

Exponer `GET /api/v1/profiles`: devuelve los perfiles del usuario de la sesión, en un objeto con la lista dentro, ordenados del primero creado al último. Cada perfil trae su `id`, `name`, `status` y `targetRoles`. Sin filtro devuelve todos los estados; con `?status=` filtra por uno. Lo usan «Nueva entrevista» (HU-4.2) y «Perfiles» (HU-2.13). No guarda nada, no publica eventos y no necesita migración.

## 2. Fuentes

| Fuente | Qué aporta |
|---|---|
| Backlog v8.2, HU-4.2: CA-4.2.1, 4.2.2, 4.2.3, 4.2.6, 4.2.18, 4.2.19 y 4.2.20 | Comportamiento (las filas con «B» o «F y B» que tocan `GET /api/v1/profiles`) |
| Backlog v8.2, HU-2.13 (notas técnicas) | Orden por `fecha_creacion` ascendente; filtro opcional `?status=COMPLETED`; filtra por el uid del token |
| Aviso al equipo del 17-oct («cambios al backlog v8», punto 3.7) | Forma: objeto con la lista dentro; `id`, `name`, `status`, `targetRoles[professionalRoleId, roleTitle]`; `status` inválido → 422. La copia local del libro aún tiene el texto viejo de CA-4.2.18; manda el aviso, que es el texto L-13 que Backend aplica al libro |
| Decisiones Q19 a Q22 (17-oct) y P14, P15 (17-oct) | Orden, sin paginación (máximo 5 perfiles), `Cache-Control: no-store`, lista resumida, campo `profiles`, valores del filtro |
| Código de `origin/develop` `0854e5c` | `ProfileController`, `ProfileAppService`, `ProfessionalProfileRepository`, `ProfileResponse.TargetRoleItem`, `ErrorCatalog`, `idx_perfil_firebase_uid`, `rol_objetivo` con `@OrderBy("createdAt ASC, id ASC")` |

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA | Lo que pide al Backend | Requisito | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| CA-4.2.1 | Lista con perfiles Activos y en Borrador; la pantalla filtra | REQ-01, REQ-02 | `ProfileListEndToEndIT.list_shouldReturnDraftsAndActive_whenUserHasBoth` | L-02, L-03 |
| CA-4.2.2 | Un perfil Activo | REQ-01 | `…list_shouldReturnSingleActive_whenOnlyOne` | L-04 |
| CA-4.2.3 | Sin perfiles → 200 con lista vacía | REQ-03 | `…list_shouldReturnEmptyList_whenUserHasNoProfiles`, `ProfileControllerTest.listProfiles_shouldReturnEmptyArray_whenNoProfiles` | L-01 |
| CA-4.2.6 | Solo borradores → 200, distinguible de la lista vacía | REQ-01 | `…list_shouldReturnOnlyDrafts_whenNoneActive` | L-02 |
| CA-4.2.18 | Todos los perfiles con nombre, estado y roles objetivo | REQ-01, REQ-02, REQ-04 | `ProfileControllerTest.listProfiles_shouldReturnContractShape`, `ProfileSummariesQueryIT.findSummaries_shouldOrderByCreationThenId` | L-02 |
| CA-4.2.19 | Un identificador ajeno en la consulta se ignora; solo los perfiles propios | REQ-06 | `…list_shouldIgnoreForeignUidParameter`, `ProfileSummariesQueryIT.findSummaries_shouldReturnOnlyOwnerProfiles` | L-08 |
| CA-4.2.20 | `roles_objetivo` de la lista se cruzan con el catálogo | REQ-04 | `…list_shouldReturnTargetRolesWithCatalogIdAndTitle` | L-02 |
| HU-2.13 (notas) | Orden ascendente por creación; `?status=COMPLETED` | REQ-02, REQ-05 | `ProfileSummariesQueryIT.findSummaries_shouldFilterByStatus_whenGiven`, `…list_shouldFilterCompleted` | L-05 |
| Aviso 3.7 | `status` inválido → 422 | REQ-07 | `ProfileStatusFilterTest` (9 casos), `ProfileControllerTest.listProfiles_shouldReturn422_whenStatusInvalid` | L-06, L-07 |
| RT-02 / RT-03 | Sin identidad → 401 | REQ-08 | `ProfileControllerTest.listProfiles_shouldReturn401_whenIdentityMissing`, `…_whenIdentityTooLong` | L-09, L-10 |

Las filas de HU-4.2 que son solo «F» (4.2.4, 4.2.7 a 4.2.13, 4.2.15, 4.2.16) y las que pertenecen a otra ruta (4.2.5, 4.2.14 y 4.2.17, catálogo de roles; 4.2.9 a 4.2.12, creación de la sesión en Entrevista) no tocan este endpoint.

## 4. Requisitos (EARS)

- **REQ-01** Cuando llegue `GET /api/v1/profiles` con una identidad válida y sin `status`, el sistema debe responder 200 con `{"profiles": [...]}`: todos los perfiles cuyo `firebase_uid` es el de `X-User-Id`, en cualquier estado.
- **REQ-02** El sistema debe ordenar `profiles` por `creado_en` ascendente y, a igual instante, por `id` ascendente.
- **REQ-03** Si el usuario no tiene perfiles (o ninguno con el estado pedido), el sistema debe responder 200 con `{"profiles": []}`.
- **REQ-04** Cada elemento debe tener exactamente `id` (UUID), `name` (texto o `null` si el perfil aún no tiene nombre), `status` (`PENDING`, `IN_PROGRESS`, `IN_REVIEW` o `COMPLETED`) y `targetRoles`. Cada rol debe tener exactamente `professionalRoleId` (UUID del catálogo) y `roleTitle` (el nombre guardado en el perfil, el mismo de `GET /profiles/{id}`), en el orden en que se agregaron (`creado_en`, `id`). Un perfil sin roles trae `[]`.
- **REQ-05** Cuando llegue `status` con uno de `PENDING`, `IN_PROGRESS`, `IN_REVIEW` o `COMPLETED` (exacto, en mayúsculas), el sistema debe devolver solo los perfiles con ese estado, con el mismo orden (P15).
- **REQ-06** El sistema debe ignorar cualquier otro parámetro de consulta (por ejemplo `firebaseUid`, `uid`, `ownerId`) y filtrar siempre por la identidad de `X-User-Id` (CA-4.2.19, RT-03-CA04).
- **REQ-07** Si `status` llega vacío, solo con espacios, en minúsculas, con un valor desconocido, repetido (`?status=A&status=B`) o con caracteres extra, el sistema debe responder 422 con `code` `PROFILE_STATUS_INVALID_VALUE`, `title` «Estado no válido» y `detail` «Selecciona una opción.», sin lista `errors` y sin repetir el valor recibido (P15).
- **REQ-08** Si `X-User-Id` falta, viene vacío, solo con espacios o con más de 128 caracteres, el sistema debe responder 401 `IDENTITY_REQUIRED` «Tu sesión expiró. Inicia sesión de nuevo.» (código existente), antes de revisar `status`. Un `X-User-Id` con caracteres de control (por ejemplo ``) no llega a la aplicación: lo rechaza Tomcat con un 400 sin cuerpo JSON, y antes lo rechaza el Gateway; endurecer `FirebaseUid` para responder 401 también a esos caracteres queda fuera de esta tarea.
- **REQ-09** Toda respuesta 200 debe llevar `Cache-Control: no-store` (Q21) y `Content-Type: application/json` con `charset=UTF-8`.
- **REQ-10** El sistema debe obtener la lista con **una sola consulta** SQL (perfiles con sus roles), sin cargar experiencia, formación ni habilidades.
- **REQ-11** No hay paginación: el plan Premium permite como máximo 5 perfiles y el Free 1 (Q20). Si por datos viejos hubiera más, se devuelven todos.

## 5. Contrato

`GET /api/v1/profiles` · `GET /api/v1/profiles?status=COMPLETED`

```json
HTTP/1.1 200
Cache-Control: no-store
Content-Type: application/json;charset=UTF-8

{
  "profiles": [
    {
      "id": "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10",
      "name": "Analista de datos",
      "status": "COMPLETED",
      "targetRoles": [
        { "professionalRoleId": "a0000002-0000-0000-0000-000000000002", "roleTitle": "Analista de Datos" }
      ]
    },
    { "id": "7b1d…", "name": null, "status": "IN_PROGRESS", "targetRoles": [] }
  ]
}
```

| Estado | Cuándo | Cuerpo |
|---|---|---|
| 200 | REQ-01 a REQ-05 | arriba |
| 401 | REQ-08 | `IDENTITY_REQUIRED` |
| 406 | `Accept` sin JSON | `MEDIA_TYPE_NOT_ACCEPTABLE` (existente) |
| 422 | REQ-07 | `PROFILE_STATUS_INVALID_VALUE` |
| 500 | Imprevisto | `INTERNAL_ERROR` (existente) |

Cambio aditivo: ruta nueva, ningún campo existente cambia. Frontend ya recibió la forma el 17-oct (punto 3.7); al abrir el PR se le avisa en una línea: el nombre `profiles` (P14), los valores de `status` (P15) y que **todo estado distinto de `COMPLETED` se muestra como «Borrador»** (HU-2.13 pide nunca mostrar el código técnico; hoy el código solo produce `IN_PROGRESS` y `COMPLETED`, pero el contrato admite `PENDING` e `IN_REVIEW`).

## 6. Validación de entrada

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| `status` | ausente | — | sin filtro | `ProfileStatusFilterTest.parse_shouldReturnEmpty_whenNull` |
| | cada válido | `PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED` | filtra | `…parse_shouldReturnStatus_whenExact` (4) |
| | vacío | `""` | 422 | `…parse_shouldThrow_whenInvalid` |
| | espacios | `"   "` | 422 | ídem |
| | minúsculas | `completed` | 422 | ídem |
| | desconocido | `ARCHIVED` | 422 | ídem |
| | repetido | `COMPLETED,IN_PROGRESS` (lo que Spring entrega con `?status=COMPLETED&status=IN_PROGRESS`) | 422 | ídem y `ProfileListEndToEndIT.list_shouldReturn422_whenStatusRepeated` |
| | espacio final | `COMPLETED ` | 422 | ídem |
| | hostil | `COMPLETED'--` | 422; el cuerpo no contiene `'--` | ídem y `…list_shouldNotEchoStatus_whenHostile` |
| | 1000 caracteres | `"A".repeat(1000)` | 422 | ídem |
| `X-User-Id` | ausente, vacío, espacios, 128 (válido), 129 | — | 200 con 128; 401 en los demás | `ProfileControllerTest.listProfiles_shouldReturn401_*` (2), `ProfileListEndToEndIT#list_shouldAcceptIdentityOf128Characters_andRejectOf129` + las pruebas existentes de `FirebaseUid` |
| `X-User-Id` | carácter de control | — | rechazo de Tomcat (400 sin cuerpo JSON); fuera de esta tarea | sin prueba aquí |
| | ausente y `status` inválido a la vez | — | 401 (la identidad se revisa primero) | `ProfileAppServiceTest.listProfiles_shouldCheckIdentityBeforeStatus` |
| Otros parámetros | `firebaseUid=uid-otro`, `ownerId=…` | — | se ignoran | `…list_shouldIgnoreForeignUidParameter` |

## 7. Base de datos

Sin migración. Se usan `perfil_profesional` (`firebase_uid`, `estado`, `creado_en`, `nombre`) y `rol_objetivo` (`perfil_id`, `rol_profesional_id`, `nombre_rol`, `creado_en`). Índices existentes: `idx_perfil_firebase_uid` (filtro) y `uq_rol_objetivo_perfil_rol_profesional` (`perfil_id`, `rol_profesional_id`) de V5_1, que sirve de índice para la unión por `perfil_id`. El orden por `creado_en` se resuelve sobre a lo sumo 5 filas por usuario; no se agrega índice compuesto (se justificaría solo con paginación). Los nombres `idx_` en lugar de `ix_` son anteriores y no se renombran (hallazgo para CM-296).

## 8. Casos borde (R5)

| Grupo | Caso | Prueba |
|---|---|---|
| Colecciones | 0, 1, 2 y 5 perfiles; perfil sin roles; perfil con 5 roles | `ProfileSummariesQueryIT` (datos insertados con `JdbcTemplate`) |
| Orden | Tres perfiles creados en `2026-10-01T10:00Z`, `2026-10-02T10:00Z` y `2026-09-30T10:00Z` | `…shouldOrderByCreationThenId` → 30-sep, 1-oct, 2-oct |
| Orden | Dos perfiles con el mismo `creado_en` | ordenados por `id` ascendente |
| Orden | Roles agregados en distinto orden | `…shouldOrderTargetRolesByCreation` |
| Texto | `name` nulo; con tildes y emoji (`"Análisis 😀"`); con `<script>alert(1)</script>` | se devuelve tal cual, como texto JSON |
| Propiedad | Dos usuarios con perfiles; `?firebaseUid=` ajeno | `…shouldReturnOnlyOwnerProfiles`, `…list_shouldIgnoreForeignUidParameter` |
| Estado | Rol del catálogo desactivado después de agregarlo | sigue en la lista (la pantalla lo filtra, CA-4.2.20) — `…list_shouldKeepRole_whenCatalogRoleInactive` |
| Rendimiento | 5 perfiles con 5 roles | una sola sentencia preparada (estadísticas de Hibernate) — `…findSummaries_shouldUseSingleQuery` |
| Carga | `Accept: application/xml` | 406 (prueba existente del manejador; petición L-11) |
| No aplica | Fechas, concurrencia, doble envío, falla parcial, dependencias | Lectura pura, sin escritura ni llamadas externas |
| No aplica | Paginación | Q20 |

## 9. Seguridad

| ASVS / OWASP | Qué hace | Prueba |
|---|---|---|
| 8.2.2 / API1 | Filtra por `X-User-Id`; ningún parámetro cambia el dueño | `…shouldReturnOnlyOwnerProfiles`, `…list_shouldIgnoreForeignUidParameter` |
| API2 | Identidad solo del Gateway; 401 si falta | `…listProfiles_shouldReturn401_*` |
| 15.3.1 / API3 | DTO con 4 campos; sin resumen, experiencia ni datos personales de más (Q22) | `ProfileControllerTest.listProfiles_shouldReturnContractShape` (exactamente esas claves) |
| API4 | A lo sumo 5 perfiles por el cupo; `status` cerrado | REQ-07 |
| 1.2.4 | JPQL con parámetros | revisión |
| 4.1.1 | `charset=UTF-8` en 200 y errores | `ProfileListEndToEndIT.list_shouldDeclareCharsetAndNoStore` |
| 14.2.1 | Ningún dato sensible en la URL (`status` no lo es) | — |
| API8 | `no-store` | ídem |
| SEG-02 | Dos usuarios: A no ve los de B | ídem |

## 10. Atributos de calidad

HU-4.2 pertenece a la épica HE-04, que según el anexo (cuadro 9) declara AC-0003, AC-0004 y AC-0006. Se agregan AC-0002 (lectura propia) y los controles transversales:

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0002 | DES-02 | p95 ≤ 2 s con **100 observaciones válidas después de calentar** (protocolo del anexo) | Una consulta con índice | `…shouldUseSingleQuery`; carpeta «DES-02 · HU-4.2» (§13, L-12) corrida con `-n 100` y p95 calculado del reporte JSON de Newman; local, con la base de compose; se declara el ambiente |
| AC-0002 | DES-04 | 5xx < 1 % | Todo error previsible es 401 o 422 | pruebas de §6 |
| AC-0003 | SEG-01, SEG-02 | 100 % | §9 | ídem |
| AC-0003 | SEG-04 | Sin datos personales en logs | El rechazo registra `code`, `requestId` y `firebaseUid`, nunca `status` ni nombres | `ProfileListEndToEndIT.list_shouldNotLogStatusValue` (captura de salida) |
| AC-0004 | FIA-01 | Todo fallo conserva el último estado confirmado | Lectura pura con `@Transactional(readOnly = true)`: ningún fallo (base caída, filtro inválido, identidad ausente) puede cambiar datos | `ProfileAppServiceTest.listProfiles_shouldThrowStatusInvalid_whenUnknown` y `…_shouldCheckIdentityBeforeStatus` (sin llamar al repositorio); `ProfileSummariesQueryIT.findSummaries_shouldNotWrite` (cuenta de inserciones, actualizaciones y borrados de Hibernate en 0) |
| AC-0004 | FIA-02 | Cero efectos al repetir | Repetir la petición 20 veces da la misma lista y no escribe | `ProfileListEndToEndIT.list_shouldReturnSameResult_whenRepeated20Times` |
| AC-0006 | INT | Error con `code` y mensaje claro | REQ-07 | `…shouldReturn422_whenStatusInvalid` |
| Transversal | MAN-01 | ≥ 90 % líneas y ramas | JaCoCo (§12) | `./mvnw.cmd clean verify` |
| Transversal | IOP-01 | OpenAPI completo | `@Operation` con 200, 401, 406, 422 | `OpenApiDocumentIT.apiDocs_shouldDescribeListProfiles_whenRequested` |
| Restricción | REST-0003 | Datos mínimos | Lista resumida (Q22) | ídem API3 |

## 11. Errores

| Código | HTTP | Mensaje | Nuevo |
|---|---|---|---|
| `PROFILE_STATUS_INVALID_VALUE` | 422 | «Selecciona una opción.» (título «Estado no válido») | **Sí**: `ProfileStatusInvalidException`, fila en `ErrorCatalog.RESPONSES`, fila en `docs/errores.md` |
| `IDENTITY_REQUIRED` | 401 | «Tu sesión expiró. Inicia sesión de nuevo.» | No |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | existente | No |
| `INTERNAL_ERROR` | 500 | existente | No |

Rutas al 500 genérico: solo lo imprevisto. Una base caída responde hoy 500 en todo Perfil; el 503 `DATABASE_UNAVAILABLE` llega a Perfil con CM-290 (D-4) y no entra aquí.

## 12. Pruebas, cobertura y comandos

| Capa | Pruebas |
|---|---|
| Dominio | `ProfileStatusFilterTest` (nueva) |
| Aplicación | `ProfileAppServiceTest`: `listProfiles_shouldReturnRepositoryResult`, `…_shouldPassStatus_whenGiven`, `…_shouldCheckIdentityBeforeStatus`, `…_shouldThrowStatusInvalid_whenUnknown` |
| Web | `ProfileControllerTest`: `listProfiles_shouldReturnContractShape`, `…_shouldReturnEmptyArray_whenNoProfiles`, `…_shouldSendNoStore`, `…_shouldReturn422_whenStatusInvalid`, `…_shouldReturn401_whenIdentityMissing`, `…_shouldReturn401_whenIdentityTooLong`, `…_shouldIgnoreUnknownParameters` |
| Persistencia | `ProfileSummariesQueryIT` (nueva, Testcontainers, Failsafe) |
| Punta a punta | `ProfileListEndToEndIT` (nueva) |
| Contrato | `OpenApiDocumentIT` (se agrega una prueba), `ErrorCatalogTest` y `ErrorCodeDocumentationTest` (existentes, deben seguir verdes con el código nuevo) |

Clases a medir: `ProfileStatusFilter`, `ProfileSummary`, `ProfileStatusInvalidException`, `ProfileAppService` (método nuevo), `ProfessionalProfileRepositoryAdapter` y `ProfessionalProfileMapping` (métodos nuevos), `ProfileController` (método nuevo), `ProfileListResponse`.

Comandos: Docker encendido; `./mvnw.cmd -B -ntp clean verify`; `target/site/jacoco/jacoco.csv`; Newman con la carpeta nueva (§13).

## 13. Postman y Newman

Carpeta nueva «HU-4.2 · Lista de perfiles» en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, con un usuario propio `uid-cm201-a` (y `uid-cm201-b` para el aislamiento):

| # | Petición | Espera |
|---|---|---|
| L-01 | `GET /profiles` de `uid-cm201-a` sin perfiles | 200, `profiles = []`, `Cache-Control: no-store` |
| L-02 | `POST /profiles` y luego `GET /profiles` | 200, 1 elemento `IN_PROGRESS`, `name = null`, `targetRoles = []`, exactamente 4 claves; tiempo < 2000 ms |
| L-03 | Agregar nombre, resumen, formación, habilidad y rol; `POST …/completion`; `GET /profiles` | 1 elemento `COMPLETED` con 1 rol (`professionalRoleId`, `roleTitle`) |
| L-04 | `GET /profiles?status=COMPLETED` | 1 elemento |
| L-05 | `GET /profiles?status=IN_PROGRESS` | `[]` |
| L-06 | `GET /profiles?status=completed` | 422 `PROFILE_STATUS_INVALID_VALUE`, «Selecciona una opción.», sin `errors` |
| L-07 | `GET /profiles?status=` | 422 igual |
| L-08 | `GET /profiles?firebaseUid=uid-cm201-a` con `X-User-Id: uid-cm201-b` | 200, `[]` |
| L-09 | `GET /profiles` sin `X-User-Id` | 401 `IDENTITY_REQUIRED` |
| L-10 | `X-User-Id` de 129 caracteres | 401 |
| L-11 | `Accept: application/xml` | 406 |
| L-12 | Carpeta aparte «DES-02 · HU-4.2»: `GET /profiles` de `uid-cm201-a`, como las carpetas DES-02 existentes | 200; se corre con `-n 100` después de 10 de calentamiento; p95 ≤ 2000 ms |

Cada petición comprueba estado, `code`, mensaje, `requestId` y `Content-Type` con `charset=UTF-8`. Se corre con la colección y `docs/perfil-local.postman_environment.json` contra el jar empaquetado y la base de `docker compose` (Q6), filtrando con `--folder "HU-4.2 · Lista de perfiles"`.

## 14. Línea base

No se pudo correr el 17-oct: Docker Desktop estaba apagado y las pruebas `*IT` de Perfil lo necesitan. La tarjeta T0 la corre: `./mvnw.cmd -B -ntp clean verify` sobre `origin/develop` `0854e5c` debe pasar antes de tocar nada.

## 15. Decisiones

| # | Decisión | Por qué | Descartado | Quién |
|---|---|---|---|---|
| Q19 | Orden por creación ascendente | Libro (HU-2.13) | Más reciente primero | Libro, 17-oct |
| Q20 | Sin paginación | Máximo 5 perfiles | Paginación | Paula, 17-oct |
| Q21 | `Cache-Control: no-store` | Igual que `GET /users/me` | — | Paula, 17-oct |
| Q22 | Lista resumida | El backlog manda; datos mínimos | Perfil completo; se pide a Vela ajustar el título de Jira | Paula, 17-oct |
| P14 | Campo `profiles` | Nombre del recurso | `items` | Paula, 17-oct |
| P15 | `status` acepta los 4 estados del código; lo demás 422 sin `errors` | Igual que `lang` del catálogo de roles | Solo `COMPLETED` | Paula, 17-oct |
| I-1 | Consulta propia con los roles (sin cargar el agregado entero) | REQ-10: evita N+1 sobre 4 colecciones | Reutilizar `findById` por perfil | Inferencia de Backend |
| I-2 | Dos métodos de consulta (con y sin estado) en vez de `:status is null` | Hibernate no infiere el tipo de un parámetro enumerado nulo | Una consulta con `is null` | Inferencia de Backend |
| R-1 | CA-4.2.18 ya tiene el texto L-13 en el libro local (Paula, 17-oct). Falta corregir las notas técnicas de HU-2.13 («roles_objetivo [id, codigo, nombre]» → «targetRoles [professionalRoleId, roleTitle]»; el código de rol ya no existe desde la v8) | Sin eso la spec contradice el texto literal del libro (R12) | Implementar con el libro viejo | Paula (dueña de las historias de Backend) |
| D-9 | La entrada PP-013 ya está en el repo (PR #96); esta CM corrige su enlace «Salida asociada» para que apunte a `../../specs/CM-54-informacion-general-resumen/spec.md` | El enlace actual apunta a un archivo que no existe en el repo | Volver a copiar la entrada | Paula (D-9) |

## 16. Fuera de alcance

Paginación; perfiles archivados (HU-2.14, sin sprint); `DATABASE_UNAVAILABLE` en Perfil (CM-290); renombrar índices `idx_` (CM-296); cambiar el título de Jira (Vela).

## 17. Preguntas

Ninguna abierta. Q19 a Q22, P14 y P15 respondidas el 17-oct.

## 18. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | CA-4.2.1, 4.2.6, 4.2.18 | `ProfileListEndToEndIT` | verde |
| V-02 | CA-4.2.3 | L-01 | `[]` |
| V-03 | CA-4.2.19 | L-08 y su IT | `[]` para B |
| V-04 | Orden | `…shouldOrderByCreationThenId` | 30-sep, 1-oct, 2-oct |
| V-05 | Filtro | L-04, L-05 | — |
| V-06 | 422 | L-06, L-07 y 9 casos de dominio | sin `errors`, sin eco |
| V-07 | 401 | L-09, L-10 | — |
| V-08 | Una sola consulta | `…shouldUseSingleQuery` | 1 sentencia |
| V-09 | `no-store` y `charset` | L-01, IT | — |
| V-10 | OpenAPI | `OpenApiDocumentIT` | 200, 401, 406, 422 documentados |
| V-11 | Catálogo | `ErrorCatalogTest`, `ErrorCodeDocumentationTest` | verde |
| V-12 | Cobertura | `jacoco.csv` | ≥ 90 % por clase |
| V-13 | Newman | 11 peticiones + DES-02 (100) | 0 fallos; p95 ≤ 2000 ms |
| V-14 | Bitácora | enlace de la entrada 02 | abre la spec de CM-54 |
| V-15 | Adversarial | `?status=COMPLETED'--`, `?status=` de 1000, `?firebaseUid=` ajeno | 422, 422, ignorado |
