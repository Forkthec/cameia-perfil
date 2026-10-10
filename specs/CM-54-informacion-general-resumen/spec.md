# Spec — CM-54: información general y resumen profesional del Perfil (HU-2.3)

- **Tarea:** CM-54 · subtarea «HU-2.3 – Backend: información general y resumen profesional» · padre CM-17 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Código de referencia: `origin/develop` `2a54ca7` (9-oct-2026).
- **Requisitos:** backlog `09102026_01_Backlog_v6.xlsx`, hoja HE-02, HU-2.3 (CA-2.3.1 a 2.3.14) y RT-01, RT-02, RT-03, RT-05, RT-06. Los CA son la única fuente de requisitos y de textos.
- **Defecto relacionado:** CM-247 (vaciar el resumen y guardar muestra confirmación, pero al recargar reaparece el anterior), de Juan José Arias Chacua para la parte de la interfaz.
- **Estado:** pendiente de aprobación de Paula. Puerta de listo: sección 23. Decisiones en la sección 17; no quedan preguntas abiertas (sección 18).
- **Depende de:** CM-274 fusionado en `develop` (su PR 1 aporta `SingleLineText`, `@CodePointSize`, `@DomainRule`, `FieldValues`, el acumulador y el bloqueo; su PR 2, `ColumnLengthConsistencyIT` y `WorkHistoryEndToEndIT` como modelo). Orden de Perfil en la sección 19.

## 1. Objetivo

Que `PATCH /api/v1/profiles/{id}` guarde el nombre y el resumen del perfil con los límites, textos y códigos de HU-2.3, contando caracteres como el cliente (puntos de código en NFC tras recortar), informando todos los campos inválidos a la vez, sin 500 por ninguna entrada, y sin dejar que el cuerpo cambie la procedencia del perfil.

## 2. Estado real del código y causa raíz

| # | Situación en `2a54ca7` | Causa raíz comprobada | Reproducción antes | Después |
|---|---|---|---|---|
| E1 | **CM-247 ya corregido en el servidor:** `"summary": ""` en Borrador → 200 con `summary` `null`; en `COMPLETED` → 422 `SUMMARY_NOT_ALLOWED` | `ProfileAppService.updateProfileInfo` (`cmd.summary().isBlank() ? null : …`) y `ProfessionalProfile.updateSummary` (CM-271) | Postman `OK PATCH /profiles/{id} — resumen vacío lo borra en Borrador (CA-2.3.4)` y `ERR PATCH … 422 borrar el resumen de un perfil activo (CA-2.3.12)` | Se conservan; se agrega la prueba E2E con base real |
| D1 | El nombre y el resumen no se recortan ni se normalizan y se miden en unidades UTF-16 | `ProfileName` y `ProfessionalSummary` usan `value.length()` sin recorte ni NFC | `name` = 254 `a` + `"😀"` (255 caracteres) → hoy 422 `PROFILE_NAME_TOO_LONG` (cuenta 256); `"  Analista  "` se guarda con espacios | 200 y se guarda `"Analista"` |
| D2 | Con nombre y resumen inválidos a la vez solo se informa el nombre | `updateProfileInfo` construye `new ProfileName(…)` antes que el resumen; la primera `InvalidFieldsException` corta | `{"name":"","summary":"<2001 a>"}` → hoy un solo elemento en `errors` | Dos elementos |
| D3 | `provenance` del cuerpo cambia la procedencia de todo el perfil | `updateProfileInfo` llama `p.updateProvenance(provenance(cmd.provenance()))` | `{"provenance":"AI_SUGGESTED"}` → hoy 200 y `provenance` del perfil `AI_SUGGESTED` | Se ignora (decisión de Paula del 6-oct-2026) |
| D4 | La respuesta no trae `summaryProvenance` | No existe en `ProfileResponse` | `GET` sin el campo | `"summaryProvenance":"MANUAL"` con resumen, `null` sin él (decisión de Paula del 6-oct-2026) |
| D5 | Un carácter de control en el nombre o el resumen produce 500 | PostgreSQL rechaza U+0000 (`SQLState 22021`) y la excepción llega a `ApiExceptionHandler.handleUnexpected` | `{"name":"Analista\u0000"}` → 500 `INTERNAL_ERROR` | 422 `PROFILE_NAME_INVALID_CHARACTERS` (D8) |
| D6 | Sin bloqueo: vaciar el resumen mientras otra pestaña finaliza puede dejar un perfil Activo sin resumen | `loadForUser` sin `FOR UPDATE` | Carrera en `ProfileWriteLockIT` (ver §9) | Lo resuelve CM-274 PR 1; aquí se prueba el caso |
| D7 | Los DTO no tienen `@Schema` | `UpdateProfileInfoRequest` sin anotaciones | `GET /v3/api-docs` sin `maxLength` | `@Schema` completo |
| D8 | `ProfessionalSummary("   ")` lanza `IllegalArgumentException` (500 si alguien lo llama sin pasar por el servicio) | Constructor del `record` | Prueba de dominio | El constructor normaliza y la ausencia se representa con `null`; el servicio decide antes |

## 3. Bloques de PR

| PR | Rama | Base | Contenido | Líneas | Horas |
|---|---|---|---|---|---|
| 0 | `CM-54-informacion-general-resumen-spec` | `develop` | `spec.md`, `plan.md`, `tasks.md` aprobados (solo documentos, como la spec de CM-36, #38) | ≈ 600 | 0,2 |
| 1 | `CM-54-informacion-general-resumen` | `develop` con CM-274 y el PR 0 | Normalización, restricciones del borde, errores a la vez, `provenance` ignorado, `summaryProvenance`, OpenAPI, pruebas | ≈ 550 | 3 |
| 2 | `CM-54-postman-informacion-general` | PR 1 | Peticiones de Postman de HU-2.3 y corrida de Newman | ≈ 450 | 1,5 |

Total ≈ 4,7 h (el backlog estima 2 h). La spec va en un PR 0 porque spec, plan y tarjetas (≈ 600 líneas) más el PR 1 pasarían de 1000.

## 4. Trazabilidad CA → requisito → prueba → Postman

Carpeta de Postman: `HU-2.3 — Información general` / `PATCH profiles/{id}`.

| CA | Exige | REQ | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| 2.3.1 | 200; nombre «Analista de datos» y resumen de 300; `IN_PROGRESS`; `summaryProvenance` `MANUAL` | REQ-IG-01, 05, 15 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldSaveNameAndSummary_whenDraft` | `200 nombre y resumen de 300 (CA-2.3.1)` |
| 2.3.2 | N/A en el MVP (HU-2.8) | — | — | — |
| 2.3.3 | 422 en `summary` «El resumen no puede superar los 2000 caracteres.» | REQ-IG-08 | `ProfileControllerTest.updateProfileInfo_shouldReturnTooLong_whenTextExceedsLimit` (fila `summary` 2001) | `422 resumen de 2001 (CA-2.3.3)` |
| 2.3.4 | 200; `summary` y `summaryProvenance` `null`; sigue en Borrador; al recargar, vacío | REQ-IG-06, 15 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldClearSummary_whenDraftAndSummaryIsBlank` (`""` y `"   "`, con `GET` posterior) | `200 resumen vacío lo borra (CA-2.3.4)` (existe) y `GET` posterior |
| 2.3.5 | 200; nombre nuevo; sigue Activo | REQ-IG-01 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldRenameAndKeepActive_whenProfileIsCompleted` | `200 renombrar un perfil Activo (CA-2.3.5)` |
| 2.3.6 | N/A en el MVP (HU-2.14) | — | — | — |
| 2.3.7 | 200 con resumen de 2000 | REQ-IG-05 | `ProfileControllerTest.updateProfileInfo_shouldAccept_whenTextIsAtLimit` (fila `summary` 2000) | `200 resumen de 2000 (CA-2.3.7)` |
| 2.3.8 | 422 en `name` «Ingresa un nombre para el perfil.» con vacío o espacios | REQ-IG-02 | `ProfileControllerTest.updateProfileInfo_shouldReturnNameRequired_whenNameIsBlank` (`""`, `"   "`, `"\t\n"`, `" "`) | `422 nombre vacío (CA-2.3.8)`, `422 nombre solo espacios` |
| 2.3.9 | 200 con nombre de 255 | REQ-IG-01 | `ProfileControllerTest.updateProfileInfo_shouldAccept_whenTextIsAtLimit` (fila `name` 255) | `200 nombre de 255 (CA-2.3.9)` |
| 2.3.10 | 422 en `name` «El nombre no puede superar los 255 caracteres.» | REQ-IG-03 | `ProfileControllerTest.updateProfileInfo_shouldReturnTooLong_whenTextExceedsLimit` (fila `name` 256) | `422 nombre de 256 (CA-2.3.10)` |
| 2.3.11 | 403 «No encontramos lo que buscabas.», sin cambios | REQ-IG-11 | `ProfileControllerTest.updateProfileInfo_shouldReturn403_whenProfileBelongsToAnotherUser` · `GeneralInfoEndToEndIT.updateProfileInfo_shouldNotChangeOtherProfile_whenUserIsNotOwner` | `403 perfil de otro Usuario (CA-2.3.11)` |
| 2.3.12 | 422 en `summary` «No puedes quedarte sin resumen profesional con el perfil activo.»; conserva resumen y estado | REQ-IG-07 | `ProfessionalProfileTest.updateSummary_shouldRejectClear_whenProfileIsCompleted` (existe) · `GeneralInfoEndToEndIT.updateProfileInfo_shouldKeepSummary_whenActiveProfileClearsIt` | `422 borrar el resumen de un perfil Activo (CA-2.3.12)` (existe) |
| 2.3.13 | 200 (mismo `PATCH`) | REQ-IG-01 | Cubierto por 2.3.5 | Cubierto por 2.3.5 |
| 2.3.14 | Solo Frontend | — | — | — |
| RT-01-CA04 | Varios campos a la vez | REQ-IG-10 | `ProfileControllerTest.updateProfileInfo_shouldReturnBothFields_whenNameAndSummaryAreInvalid` | `422 nombre vacío y resumen de 2001` |
| RT-01-CA06 | Campos no esperados ignorados | REQ-IG-14 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldIgnoreUnexpectedFields_whenBodyHasThem` | `200 ignora status, provenance, firebaseUid e id` |
| RT-01-CA08 | `<script>` literal | REQ-IG-12 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldReturnTextLiterally_whenSummaryHasMarkup` | `200 resumen con <script>` |

## 5. Requisitos (EARS)

- **REQ-IG-01.** Cuando el `PATCH` traiga `name` con texto, Perfil debe recortarlo (espacios de `trim()` de JavaScript), normalizarlo a NFC y guardarlo si tiene de 1 a 255 puntos de código; los espacios internos se conservan.
- **REQ-IG-02 (CA-2.3.8).** Cuando `name` llegue `""` o solo con espacios, Perfil debe responder 422 con `{field:"name", code:"PROFILE_NAME_REQUIRED", message:"Ingresa un nombre para el perfil."}` y no guardar nada de la petición.
- **REQ-IG-03 (CA-2.3.10).** Cuando `name` supere 255 puntos de código, 422 con `{field:"name", code:"PROFILE_NAME_TOO_LONG", message:"El nombre no puede superar los 255 caracteres."}`.
- **REQ-IG-04.** Cuando `name` no venga o venga `null`, Perfil no debe cambiar el nombre (decisión de Paula del 6-oct-2026).
- **REQ-IG-05 (CA-2.3.1, 2.3.7).** Cuando `summary` traiga texto, Perfil debe recortar sus extremos, normalizarlo a NFC, conservar los saltos de línea internos y guardarlo si tiene de 1 a 2000 puntos de código.
- **REQ-IG-06 (CA-2.3.4).** Cuando `summary` llegue `""` o solo con espacios y el perfil no esté `COMPLETED`, Perfil debe guardar el resumen como `null`, responder 200 con `summary` y `summaryProvenance` `null` y mantener el estado.
- **REQ-IG-07 (CA-2.3.12).** Cuando `summary` llegue `""` o solo con espacios y el perfil esté `COMPLETED`, Perfil debe responder 422 con `{field:"summary", code:"SUMMARY_NOT_ALLOWED", message:"No puedes quedarte sin resumen profesional con el perfil activo."}` y no guardar nada de la petición.
- **REQ-IG-08 (CA-2.3.3).** Cuando `summary` supere 2000 puntos de código, 422 con `{field:"summary", code:"SUMMARY_TOO_LONG", message:"El resumen no puede superar los 2000 caracteres."}`.
- **REQ-IG-09.** Cuando `summary` no venga o venga `null`, Perfil no debe cambiar el resumen.
- **REQ-IG-10 (RT-01-CA04).** Cuando `name`, `summary` y `preferredModality` fallen en la forma a la vez, Perfil debe responder un elemento por campo en la misma respuesta. `SUMMARY_NOT_ALLOWED` (regla de estado) se informa cuando la forma pasó.
- **REQ-IG-11 (CA-2.3.11).** Perfil ajeno → 403 `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.» sin cambios; inexistente → 404 `PROFILE_NOT_FOUND`.
- **REQ-IG-12 (RT-01-CA08).** El texto se guarda y se devuelve tal como quedó tras REQ-IG-01/05; nunca se interpreta como HTML ni se sanea. La nota técnica de HU-2.3 «Sanitización XSS en el backend (RT-01-CA08)» remite a RT-01-CA08, que fija la regla efectiva: «guardar el texto tal como se escribió y mostrarlo como texto literal». La protección contra XSS es la salida JSON con `Content-Type` y la presentación como texto en `cameia-web`.
- **REQ-IG-13.** La comprobación de REQ-IG-07 y el guardado ocurren con el perfil bloqueado (bloqueo de CM-274 PR 1), para que una finalización simultánea no deje un perfil Activo sin resumen.
- **REQ-IG-14 (RT-01-CA06, API3).** `provenance`, `status`, `reviewStatus`, `firebaseUid`, `id` y cualquier otro campo no listado en §6 se ignoran: `provenance` sale del DTO y del comando (decisión de Paula del 6-oct-2026).
- **REQ-IG-15 (CA-2.3.1, 2.3.4).** La respuesta del perfil incluye `summaryProvenance`: `"MANUAL"` si hay resumen y `null` si no, derivado sin columna (decisión de Paula del 6-oct-2026). El «origen de ejecución de IA» no se agrega: CA-2.3.4 se cumple porque no hay origen; el campo lo define HU-2.8, fuera del MVP (D7).
- **REQ-IG-16 (caracteres de control).** Un carácter de control en `name` (cualquiera) o en `summary` (salvo `\n`, `\r`, `\t`) se rechaza con `PROFILE_NAME_INVALID_CHARACTERS` «El nombre tiene caracteres no permitidos.» / `SUMMARY_INVALID_CHARACTERS` «El resumen tiene caracteres no permitidos.» (D8; mismo criterio que D11 de CM-274: solo la categoría Cc).

## 6. Matriz de validación por campo (R2) — `PATCH /api/v1/profiles/{id}`

Fase B = borde (Bean Validation sobre el `record` ya normalizado); D = dominio.

| Campo | Causa (en orden) | Valores literales | Fase | Estado · `code` | Mensaje |
|---|---|---|---|---|---|
| `name` | ausente o `null` | sin clave; `"name": null` | — | 200, no cambia | — |
| `name` | vacío tras recortar | `""`, `"   "`, `"\t\n"`, `" "`, `"﻿"` | B (`@Size(min = 1)` sobre el texto normalizado) | 422 `PROFILE_NAME_REQUIRED` | «Ingresa un nombre para el perfil.» |
| `name` | carácter de control | `"Analista\u0000"`, `"A\u0007"`, `"A\nB"` | B (`@DomainRule(Rule.PROFILE_NAME)`) | 422 `PROFILE_NAME_INVALID_CHARACTERS` | «El nombre tiene caracteres no permitidos.» |
| `name` | más de 255 | 256 `a`; 255 `a` + `"😀"`; 256 `ñ` | B (`@CodePointSize(max = 255)`) | 422 `PROFILE_NAME_TOO_LONG` | «El nombre no puede superar los 255 caracteres.» |
| `name` | válido | 254 y 255 `a`; 254 `a` + `"😀"`; `"é"` × 255 (se guarda `"é"` × 255); `"  Analista  "` → `"Analista"`; `"Analista  de datos"` conserva el doble espacio | — | 200 | — |
| `summary` | ausente o `null` | — | — | 200, no cambia | — |
| `summary` | vacío tras recortar, perfil no `COMPLETED` | `""`, `"   "`, `"\n\n"` | — | 200 con `summary` `null` | — |
| `summary` | vacío tras recortar, perfil `COMPLETED` | `""`, `"   "` | D | 422 `SUMMARY_NOT_ALLOWED` | «No puedes quedarte sin resumen profesional con el perfil activo.» |
| `summary` | carácter de control distinto de `\n`, `\r`, `\t` | `"Hola\u0000"` | B (`@DomainRule(Rule.SUMMARY)`) | 422 `SUMMARY_INVALID_CHARACTERS` | «El resumen tiene caracteres no permitidos.» |
| `summary` | más de 2000 | 2001 `a`; 1999 `a` + `"😀😀"` | B (`@CodePointSize(max = 2000)`) | 422 `SUMMARY_TOO_LONG` | «El resumen no puede superar los 2000 caracteres.» |
| `summary` | válido | 1, 300, 1999, 2000 `a`; 2000 `"😀"`; 2000 con saltos de línea internos; texto en NFD igual al NFC; `"<script>alert(1)</script>"`; `"100 % _x_ \"y\""` | — | 200 | — |
| `preferredModality` | ausente o `null` | — | — | 200, no cambia | — |
| `preferredModality` | fuera del conjunto o vacío | `"HYBRID "` (tras recortar es válido), `"hybrid"`, `"PRESENCIAL"`, `""` | B (`@DomainRule(Rule.PREFERRED_MODALITY)` sobre el texto normalizado y `@Size(min = 1)` para `""`, que no es una opción: D6) | 422 `PREFERRED_MODALITY_INVALID_VALUE` | «Selecciona una opción.» |
| `provenance` | cualquier valor | `"AI_SUGGESTED"`, `"x"` | — | se ignora | — |
| cuerpo | `{}` | — | — | 200 sin cambios | — |
| cuerpo | JSON mal formado, arreglo, objeto en un texto (`"name": {}`) | — | Jackson | 422 `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» |
| cuerpo | número o booleano en un texto (`"name": 123`, `"name": true`) | — | — | se convierte en `"123"` o `"true"` (V1 de CM-274) | — |
| cuerpo | un campo con varias reglas rotas a la vez (por ejemplo, un nombre con carácter de control y de 300 caracteres) | — | B | 422 con un solo elemento para el campo, siempre el mismo: obligatorio, luego caracteres o valor, luego largo (D9) | el del código elegido |
| cuerpo | `Content-Type: text/plain` | — | Spring | 415 `MEDIA_TYPE_NOT_ALLOWED` | «Tipo de contenido no admitido.» |
| ruta | `{id}` no UUID / inexistente | `"x"` / `00000000-0000-0000-0000-000000000000` | — | 422 `PROFILE_ID_INVALID_FORMAT` / 404 `PROFILE_NOT_FOUND` | «El identificador del perfil no es válido.» / «No encontramos lo que buscabas.» |
| encabezado | `X-User-Id` ausente, en blanco, 129 caracteres | — | — | 401 `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» |

Los valores de `WorkModality` se leen del `enum` en el código (`git show origin/develop:…/WorkModality.java`); la tarjeta los copia de ahí.

## 7. Base de datos (R4)

Sin migración. `perfil_profesional.nombre VARCHAR(255)` y `resumen VARCHAR(2000)` ya coinciden con los límites; PostgreSQL cuenta caracteres. Se agregan a `ColumnLengthConsistencyIT` (creada en CM-274 PR 2) las filas `nombre` = `ProfileName.MAX_LENGTH` y `resumen` = `ProfessionalSummary.MAX_LENGTH` (constantes que pasan a `public static final`). La nota técnica del CA menciona `ck_contexto_resumen_origen` (resumen, procedencia y origen nulos en conjunto): en el MVP la procedencia del resumen es derivada y no hay columna de origen, así que no hay nada que restringir; la restricción llega con HU-2.8. `@Column(length)` de `ProfessionalProfileEntity.name` y `summary` se comprueban (255 y 2000).

## 8. Errores (R3)

| `code` | HTTP | Origen | Mensaje | Estado |
|---|---|---|---|---|
| `PROFILE_NAME_REQUIRED` | 422 (campo) | Borde (`UpdateProfileInfoRequest.name.Size`) y `ProfileName` | «Ingresa un nombre para el perfil.» | Existe; pasa también al borde |
| `PROFILE_NAME_TOO_LONG` | 422 (campo) | Borde (`…name.CodePointSize`) y `ProfileName` | «El nombre no puede superar los 255 caracteres.» | Existe; cambia a puntos de código |
| `SUMMARY_TOO_LONG` | 422 (campo) | Borde (`…summary.CodePointSize`) y `ProfessionalSummary` | «El resumen no puede superar los 2000 caracteres.» | Existe |
| `SUMMARY_NOT_ALLOWED` | 422 (campo) | `ProfessionalProfile.updateSummary` | «No puedes quedarte sin resumen profesional con el perfil activo.» | Existe |
| `PREFERRED_MODALITY_INVALID_VALUE` | 422 (campo) | Borde (`@DomainRule`, que ejecuta `FieldValues.option`, y `…preferredModality.Size` para `""`) | «Selecciona una opción.» | Existe |
| `PROFILE_NAME_INVALID_CHARACTERS`, `SUMMARY_INVALID_CHARACTERS` | 422 (campo) | Borde (`@DomainRule`) y objetos de valor (constantes `ProfileName.CHARACTERS` y `ProfessionalSummary.CHARACTERS`) | «El nombre tiene caracteres no permitidos.» / «El resumen tiene caracteres no permitidos.» | Nuevos |
| `PROFILE_UPDATE_IN_PROGRESS` | 409 | CM-274 PR 1 | «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.» | De CM-274 |

Rutas que hoy llegan al 500 y su destino: U+0000 en `name` o `summary` (→ 422, D5); `new ProfessionalSummary("   ")` desde código (→ ya no lanza: el `record` normaliza y el servicio convierte en `null` antes). Cada rechazo 4xx se registra una vez en `WARN` con `code`, `requestId` y `firebaseUid`; nunca el nombre ni el resumen. El 409 de perfil ocupado también va en `WARN`.

## 9. Casos borde (R5)

| Grupo | Caso | Cómo |
|---|---|---|
| Presencia | ausente, `null`, `""`, espacios, `\t\n`, U+00A0, U+FEFF en `name` y `summary` | §6 |
| Texto | 254/255/256, 1999/2000/2001, `ñ`, emoji, NFD, saltos internos, `<script>`, comillas, `%`, `_` (sin búsquedas `LIKE`: texto literal) | §6 |
| Números, fechas, colecciones | No hay campos de ese tipo | Justificado |
| Enumerados | `preferredModality` válido, desconocido, minúsculas, vacío | §6 |
| Duplicados | No hay unicidad del nombre del perfil (un Usuario tiene un perfil en el Plan Free) | Justificado |
| Estado | vaciar el resumen en `IN_PROGRESS`, `IN_REVIEW` (200) y `COMPLETED` (422); renombrar en `COMPLETED` (200) | `ProfessionalProfileTest`, E2E |
| Propiedad | ajeno 403; inexistente 404; `firebaseUid` en el cuerpo ignorado; perfil ajeno con cuerpo inválido: 422, porque la validación del borde corre antes de comprobar el dueño y su mensaje describe solo el cuerpo enviado, sin revelar nada del perfil | §4; `ProfileControllerTest.updateProfileInfo_shouldReturn422_whenProfileIsForeignAndBodyIsInvalid` |
| Concurrencia | vaciar el resumen mientras otra pestaña finaliza: con el bloqueo, o la finalización ve el resumen y luego el `PATCH` recibe 422, o el `PATCH` borra primero y la finalización recibe 422 `PROFILE_INCOMPLETE` | `ProfileWriteLockIT.updateProfileInfo_shouldNeverLeaveActiveProfileWithoutSummary_whenCompletionRunsAtTheSameTime` |
| Falla parcial | Una sola transacción, sin efectos externos | Justificado |
| Dependencias | Ninguna externa | Justificado |
| Carga | `summary` de 1 MiB → 422 `SUMMARY_TOO_LONG` sin 500; JSON mal formado; `text/plain`; campos extra | `GeneralInfoEndToEndIT.updateProfileInfo_shouldReturn422_whenSummaryIsOneMebibyte` |

## 10. Seguridad (R6)

### 10.1 ASVS 5.0.0 nivel 1 (`cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`, `4c46e89`)

| ID | Aplica | Qué lo cumple | Prueba |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | `ProfileResponse` por Jackson | `ProfileControllerTest` |
| 1.3.1 / 1.3.2 HTML de usuario y evaluación dinámica | 1.3.1 no (no se renderiza HTML); 1.3.2 sí | El resumen se guarda literal (RT-01-CA08); el mensaje de `@DomainRule` se escapa antes de la plantilla | `GeneralInfoEndToEndIT.updateProfileInfo_shouldReturnTextLiterally_whenSummaryHasMarkup`; `DomainRuleValidatorTest` |
| 2.2.1 Validación con lista permitida | Sí | Largos en puntos de código, control rechazado, modalidad exacta | Matriz de la sección 6 |
| 2.2.2 Validación en el servidor | Sí | Borde y dominio | `GeneralInfoEndToEndIT` |
| 2.3.1 Flujo de negocio en orden | Sí | Un perfil Activo no queda sin resumen, ni con una finalización simultánea | `ProfileWriteLockIT.updateProfileInfo_shouldNeverLeaveActiveProfileWithoutSummary_whenCompletionRunsAtTheSameTime` |
| 4.1.1 `charset` | Sí | Forzado por `server.servlet.encoding.force-response` | `ProfileControllerTest`; Newman `expectProblem` |
| 8.2.2 / 8.3.1 BOLA, autorización en el servidor | Sí | `loadForUserForUpdate` | `ProfileControllerTest.updateProfileInfo_shouldReturn403_whenProfileBelongsToAnotherUser`; `GeneralInfoEndToEndIT.updateProfileInfo_shouldNotChangeOtherProfile_whenUserIsNotOwner` |
| 15.3.1 Respuesta con lo necesario | Sí | `summaryProvenance` derivado; sin campos internos | `ProfileControllerTest.getProfile_shouldReturnManualSummaryProvenance_whenSummaryExists` |

### 10.2 OWASP API Security Top 10 — `PATCH /api/v1/profiles/{id}`

| Riesgo | Cómo | Prueba |
|---|---|---|
| API1 BOLA | Dueño por `X-User-Id` con la fila bloqueada | Las de 8.2.2 |
| API2 Autenticación | `X-User-Id` ausente, en blanco o de 129 → 401; `firebaseUid` del cuerpo ignorado | `ProfileControllerTest` (401); `GeneralInfoEndToEndIT.updateProfileInfo_shouldIgnoreUnexpectedFields_whenBodyHasThem` |
| API3 Propiedades del objeto | `provenance`, `status`, `id` y `firebaseUid` no se enlazan | La misma prueba de campos no esperados |
| API4 Consumo de recursos | 255 y 2000 puntos de código; 1 MiB → 422 sin 500; el tamaño del cuerpo es la tarea aparte pedida con CM-36 | `GeneralInfoEndToEndIT.updateProfileInfo_shouldReturn422_whenSummaryIsOneMebibyte` |
| API5 Autorización por función | Solo el dueño | Las de API1 |
| API6 Flujos sensibles | Borrar el resumen de un perfil Activo se rechaza con la fila bloqueada | `ProfileWriteLockIT` (carrera) |
| API7 SSRF | No aplica: ninguna URL sale de la entrada | — |
| API8 Configuración | Sin traza ni nombres de clase | V-08 de la lista de verificación |
| API9 Inventario | La ruta queda en OpenAPI con todos sus estados | `OpenApiDocumentIT` |
| API10 Consumo inseguro | No aplica: no consume otro servicio | — |

Logs sin nombre ni resumen (SEG-04).

## 11. Código y patrones (R7)

- **Reutiliza:** `SingleLineText` (incluido `hasControlCharacter`), `@CodePointSize`, `@DomainRule` (se le agregan las reglas `PROFILE_NAME`, `SUMMARY` y `PREFERRED_MODALITY`), `loadForUserForUpdate`, `InvalidFieldsException` (todo de CM-274 PR 1); `ErrorCatalog`; `ProfileControllerTest`; `ProfileWriteLockIT`.
- **Modifica:** `UpdateProfileInfoRequest` (normalización y restricciones; sin `provenance`), `UpdateProfileInfoCommand` (sin `provenance`), `ProfileName` y `ProfessionalSummary` (normalizan, miden en puntos de código, rechazan control; constantes públicas), `ProfileAppService.updateProfileInfo` (sin `updateProvenance`), `ProfessionalProfile` (se borra `updateProvenance` si queda sin uso), `ProfileResponse` (`summaryProvenance`), `ProfileController` (OpenAPI), `ErrorCode`, `ErrorCatalog`, `docs/errores.md`.
- **Crea:** `GeneralInfoEndToEndIT`.
- **Por qué `@Size(min = 1)` para el nombre vacío:** `@NotBlank` rechazaría también el `null`, que aquí significa «no cambiar»; tras la normalización un nombre de solo espacios es `""` y `@Size(min = 1)` lo rechaza sin tocar el `null`. Un comentario de línea lo explica en el `record`.
- **Idioma (R1):** identificadores y nombres de prueba en inglés; Javadoc, comentarios, `@DisplayName`, OpenAPI y logs en español.

## 12. Documentación (R8)

`@Schema` en `UpdateProfileInfoRequest` (`name`: `minLength = 1`, `maxLength = 255`, `nullable`; `summary`: `maxLength = 2000`, `nullable`, «vacío lo borra salvo en un perfil Activo»; `preferredModality`: `allowableValues`) y en `ProfileResponse.summaryProvenance` (`allowableValues = {"MANUAL"}`, `nullable`). `@ApiResponse` del `PATCH` con 200, 401, 403, 404, 415, 422 (lista completa de códigos), 409 `PROFILE_UPDATE_IN_PROGRESS`. `docs/errores.md` actualizado. Javadoc en español en todo lo tocado.

## 13. Pruebas (R9) y cobertura (R10)

- Dominio: `ProfileValueRulesTest` (o `ProfileNameTest`/`ProfessionalSummaryTest` si se separan) con límites parametrizados, NFC, control; `ProfessionalProfileTest` (vaciar el resumen por estado).
- Servicio: `ProfileAppServiceTest.updateProfileInfo_shouldIgnoreProvenance_whenCommandHasNone` (el perfil conserva `MANUAL`), `updateProfileInfo_shouldNotSave_whenSummaryClearIsRejected`.
- Web: `ProfileControllerTest` (§4 y §6).
- Integración: `GeneralInfoEndToEndIT` (puerto aleatorio, PostgreSQL real), `ProfileWriteLockIT` (carrera de §9), `ColumnLengthConsistencyIT` (filas nuevas), `OpenApiDocumentIT` (`maxLength` 255 y 2000).
- Cobertura ≥ 90 % de líneas y ramas en `ProfileName`, `ProfessionalSummary`, `UpdateProfileInfoRequest`, `UpdateProfileInfoCommand`, `ProfileAppService`, `ProfessionalProfile`, `ProfileResponse`, `ErrorCatalog`; la global no baja. Comando: `./mvnw.cmd -B clean verify` → `target/site/jacoco/jacoco.csv`.

## 14. Postman y Newman (R11)

Carpeta `HU-2.3 — Información general` / `PATCH profiles/{id}` con las utilidades `expectProblem` y `expectFieldError` de CM-274 PR 4 (si aún no existen, este PR las crea con el mismo nombre). Una petición por fila de §4 y por causa de §6: `name` `""`, espacios, U+00A0, 254, 255, 256, 255 con emoji, control; `summary` 1999, 2000, 2001, 2000 emojis, vacío en Borrador y en Activo, `<script>`; `preferredModality` inválido; dos campos a la vez; `{}`; cuerpo ilegible; `text/plain`; sin identidad; ajeno; inexistente; `provenance` ignorado. Corrida: `docker compose down -v`, `docker compose up -d --build`, `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-2.3 — Información general" --reporters cli,junit`; se pega la salida.

## 15. Atributos de calidad (R13)

Anexo, cuadro 9: **HE-02** declara AC-0001, AC-0003, AC-0006 y MAN-02; se agregan las métricas que el cambio toca. «Resultado real» lo llenan la ejecución y la verificación.

| Atributo | Métrica (ID) | Límite | Cómo lo cumple | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0001 (HE-02) | IA-01 | 100 % con procedencia válida | `summaryProvenance` `MANUAL` derivado; `provenance` del cuerpo ignorado; la IA no escribe el resumen en el MVP | `GeneralInfoEndToEndIT.updateProfileInfo_shouldSaveNameAndSummary_whenDraft`; `…shouldIgnoreUnexpectedFields_whenBodyHasThem` | PENDIENTE |
| AC-0003 (HE-02) | SEG-01 | ASVS N1 aplicable | Sección 10.1 | Las de la sección 10.1 | PENDIENTE |
| AC-0003 (HE-02) | SEG-02 | 100 % de accesos cruzados rechazados | 403 sin cambios, con dos Usuarios | `GeneralInfoEndToEndIT.updateProfileInfo_shouldNotChangeOtherProfile_whenUserIsNotOwner` | PENDIENTE |
| AC-0003 (HE-02) | SEG-03 | Cero críticas; sin dependencias nuevas | — | `git diff origin/develop -- pom.xml` vacío | PENDIENTE |
| AC-0003 (HE-02) | SEG-04 | Cero datos sensibles en logs | Nunca el nombre ni el resumen | `docker compose logs app \| Select-String -Pattern 'Analista\|<script>'` → 0 | PENDIENTE |
| AC-0006 (HE-02) | INT-06 (parte de Backend) | El cliente muestra estado y acción sin ayuda | Mensajes literales, todos los de forma a la vez | `ProfileControllerTest.updateProfileInfo_shouldReturnBothFields_whenNameAndSummaryAreInvalid` | PENDIENTE |
| MAN-02 (HE-02) | MAN-02 | Adaptadores con éxito, formato inválido, tiempo de espera y cuota | No se toca ningún adaptador (la persistencia no cambia) | — | No aplica |
| AC-0002 (cambio) | DES-02 | p95 ≤ 2 s, 100 observaciones | Una lectura con bloqueo y una escritura | Carpeta `DES-02 — HU-2.3` (crear perfil y `PATCH`), `-n 105`, mismo script de p95 que CM-274 T-E3 | PENDIENTE |
| AC-0004 (cambio) | FIA-01 | Todo fallo conserva el último estado | Un perfil Activo nunca queda sin resumen; un 422 no guarda nada | `ProfileWriteLockIT.updateProfileInfo_shouldNeverLeaveActiveProfileWithoutSummary_whenCompletionRunsAtTheSameTime`; `ProfileAppServiceTest.updateProfileInfo_shouldNotSave_whenSummaryClearIsRejected` | PENDIENTE |
| AC-0004 (cambio) | FIA-02 | Cero efectos en 20 repeticiones | El mismo `PATCH` repetido deja el mismo estado | `GeneralInfoEndToEndIT.updateProfileInfo_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | PENDIENTE |
| Control | MAN-01 | ≥ 90 % líneas y ramas en lo tocado | Sección 13 | `jacoco.csv` | PENDIENTE |
| Control | IOP-01 | OpenAPI completo | Sección 12 | `OpenApiDocumentIT` | PENDIENTE |
| Control | IOP-04 | Outbox | No aplica aquí: CM-67 publica `perfil.profesional-actualizado` en toda escritura de un perfil `COMPLETED`, incluido este `PATCH` | — | No aplica |
| Restricción | REST-0003 | Datos mínimos | Sin columnas nuevas; procedencia derivada | — | No aplica |

## 16. Fuera de alcance (con destino)

CA-2.3.2 (HU-2.8) y CA-2.3.6 (HU-2.14; 409 al editar un archivado) · CA-2.3.14 y el guardado automático (cliente) · la parte de CM-247 de la interfaz (Juan José Arias Chacua; se avisa en el documento de Frontend que el servidor ya borra el resumen) · publicar `perfil.profesional-actualizado` (CM-67) · `salaryExpectation` (fuera del MVP) · restricciones `CHECK` de nombre, resumen y modalidad en `perfil_profesional` (pendiente de la segunda parte de CM-283, D10) · tope de tamaño del cuerpo de la petición (DevOps, por una tarea que abre Vela; hoy el servicio lee y valida cuerpos de decenas de MiB antes de responder 422, sin error 5xx).

## 17. Decisiones

| # | Decisión | Porqué | Alternativas | Decisión humana |
|---|---|---|---|---|
| D1 | `""`/espacios en `summary` = borrar; ausente/`null` = no cambiar | Lo que envía `cameia-web` y pide CA-2.3.4, sin romper el `PATCH` parcial | `null` = borrar | Paula, 7-oct-2026 (D21 de CM-271) |
| D2 | Forma en el borde y estado en el dominio; caracteres y modalidad con `@DomainRule` (regla escrita una vez en el dominio) | Igual que CM-274 (D3) y Cuentas (D3 y D18 de CM-36) | Todo en el dominio; copiar las reglas en anotaciones propias | Paula, 6 y 7-oct-2026 (D3 y D18 de CM-36) |
| D3 | `name` ausente o `null` no cambia | `PATCH` parcial | Rechazar | Paula, 6-oct-2026 |
| D4 | `summaryProvenance` derivado, sin columna | En el MVP todo resumen es `MANUAL` | Columna nueva | Paula, 6-oct-2026 |
| D5 | `provenance` ignorado en el `PATCH` | HU-2.3 no lo pide (RT-01-CA06) | Mantenerlo | Paula, 6-oct-2026 |
| D6 | `""` en `preferredModality` es valor inválido (422 `PREFERRED_MODALITY_INVALID_VALUE`) | No es una opción y el campo está fuera de HU-2.3 | Tratarlo como borrar; tratarlo como ausente | Paula, 9-oct-2026 |
| D7 | No se agrega un campo de «origen de ejecución de IA» | Lo define HU-2.8 (fuera del MVP); un campo sin nombre fijado sería contrato sin uso | `summaryAiRunId: null` siempre | Paula, 9-oct-2026 |
| D8 | Caracteres de control: «El nombre tiene caracteres no permitidos.» y «El resumen tiene caracteres no permitidos.»; el resumen admite tabulador, salto de línea y retorno | Mismo criterio que CM-274 (D11); el texto se pide a Vela para el backlog | Un código común; quitarlos en silencio | Paula, 9-oct-2026 |
| D9 | Un campo con varias reglas rotas informa una sola, por orden fijo: obligatorio o no admitido, luego caracteres, valor o formato, y al final el largo | El mensaje dependía del orden interno de las violaciones y cambiaba con el contenido; quien corrige el largo recibía después «caracteres no permitidos». Aplica también a los campos de experiencia y formación | Dejar el orden indefinido; informar todas las reglas del campo | Paula, 10-oct-2026 |
| D10 | Sin `CHECK` en `perfil_profesional` para nombre, resumen y modalidad en esta CM | Evita tocar el esquema aquí; el código y las pruebas ya impiden los valores. Queda como pendiente de la segunda parte de CM-283 | Migración con `ck_perfil_nombre_no_vacio`, `ck_perfil_resumen_no_vacio` y `ck_perfil_modalidad` | Paula, 10-oct-2026 |

## 18. Preguntas abiertas

Ninguna. La que había (la nota «Sanitización XSS en el backend») la resuelve el propio backlog: la nota remite a RT-01-CA08, que manda guardar el texto literal (REQ-IG-12).

## 19. Integración con las otras CM de Perfil

Perfil se ejecuta en serie: **CM-279 P1 → CM-279 P2 → CM-274 → CM-54 → CM-66 → CM-67** (detalle en la sección 19 de la spec de CM-274). Esta CM:

| Pieza compartida | Qué hace CM-54 |
|---|---|
| Migraciones | Ninguna (V5 a V7 ya están; V8 es de CM-66) |
| `ErrorCode`, `ErrorCatalog`, `docs/errores.md` | Agrega al final `PROFILE_NAME_INVALID_CHARACTERS` y `SUMMARY_INVALID_CHARACTERS` con su fila; `ErrorCodeDocumentationTest` (CM-274) obliga a documentarlos y probarlos en el mismo PR |
| `DomainRule.Rule` | Agrega `PROFILE_NAME`, `SUMMARY` y `PREFERRED_MODALITY` al final; CM-66 agrega las suyas después |
| `ProfileAppService` | Solo `updateProfileInfo` y sus privados |
| Colección de Postman | Carpeta nueva `HU-2.3 — Información general` y `DES-02 — HU-2.3`; reutiliza `expectProblem`/`expectFieldError` y el entorno `docs/perfil-local.postman_environment.json` |
| `ColumnLengthConsistencyIT`, `ProfileWriteLockIT`, `OpenApiDocumentIT` | Agrega filas y casos |

## 20. Línea base verde

En la base de la rama del PR 1 (`origin/develop` con CM-274 y el PR 0), antes de editar; salida pegada en `analisis/ejecucion_CM-54.md`. Si algo falla, se detiene y se reporta.

| Comprobación | Comando | Esperado |
|---|---|---|
| Herramientas | `java -version`; `docker version`; `npx newman --version` | JDK 21, Docker encendido, Newman 6 |
| Build y pruebas | `./mvnw.cmd -B clean verify` | `BUILD SUCCESS`, 0 fallos y 0 omitidas (se anota el número de pruebas) |
| Cobertura de partida | `target/site/jacoco/jacoco.csv` | Global anotado: piso que el PR no puede bajar |
| Postman | `docker compose down -v`; `docker compose up -d --build`; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --reporters cli` | 0 fallos |

Trampa: si el puerto 5432 está ocupado en la máquina, `$env:POSTGRES_PORT='55433'` antes de `docker compose up`.

## 21. Regla → evidencia (R1 a R14)

| Regla | Evidencia prevista | Estado en la spec |
|---|---|---|
| R1 | Diff: identificadores en inglés; Javadoc, comentarios, OpenAPI y logs en español | Cubierta |
| R2 | Sección 6; `ProfileControllerTest`, `ProfileValueRulesTest` | Cubierta |
| R3 | Sección 8; U+0000 → 422 (`GeneralInfoEndToEndIT.updateProfileInfo_shouldRejectControlCharacter_whenNameHasNul`); `ErrorCodeDocumentationTest` | Cubierta |
| R4 | Sin migración; `ColumnLengthConsistencyIT` (255 y 2000) | Cubierta |
| R5 | Sección 9 | Cubierta |
| R6 | Sección 10 (ASVS y OWASP API); SEG-01 a SEG-04 | Cubierta |
| R7 | Sección 11; `ArquitecturaTest` | Cubierta |
| R8 | Sección 12; `OpenApiDocumentIT` | Cubierta |
| R9 | Sección 13 | Cubierta |
| R10 | `jacoco.csv` de las clases de la sección 13 | Cubierta |
| R11 | Sección 14; Newman de la carpeta y de la colección completa | Cubierta |
| R12 | Sección 4 (CA-2.3.1 a 2.3.14; 2.3.2, 2.3.6 y 2.3.14 con destino) | Cubierta |
| R13 | Sección 15 | Cubierta |
| R14 | Sin push, PR ni Jira sin el sí de Paula | Cubierta |

## 22. Lista de verificación de la CM

| # | Comprobación | Cómo | Esperado |
|---|---|---|---|
| V-01 | Cada CA de la sección 4 tiene su prueba | `git grep -n "<método>" -- src/test` por cada nombre | Todas existen |
| V-02 | Cada CA tiene su petición | Nombres en la carpeta `HU-2.3 — Información general` | Todos presentes |
| V-03 | Textos literales | `message`/`detail` contra las secciones 5 y 6 | Iguales carácter por carácter |
| V-04 | Suite completa | `./mvnw.cmd -B clean verify` | Verde, 0 omitidas |
| V-05 | Ninguna ruta al 500 | Peticiones reales: U+0000 en `name` y `summary`, `"name": {}`, cuerpo de 1 MiB, `text/plain`, `{}` | Ningún 500 |
| V-06 | Forma de error | Cualquier 4xx | `problem+json;charset=UTF-8`, `code`, `requestId`, `errors[]` con `field`, `code`, `message`; sin traza |
| V-07 | Errores a la vez | `{"name":"","summary":"<2001 a>","preferredModality":"hybrid"}` | Tres elementos en `errors` |
| V-08 | CM-247 | `PATCH` con `"summary": ""` en Borrador y `GET` posterior | 200 con `summary` y `summaryProvenance` `null`; el `GET` igual |
| V-09 | Perfil Activo sin resumen | `ProfileWriteLockIT` (las dos órdenes de la carrera) | Nunca `COMPLETED` sin resumen |
| V-10 | Procedencia | `{"provenance":"AI_SUGGESTED"}` | 200; el perfil sigue `MANUAL` |
| V-11 | Repetición | `…shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | Mismo estado tras 20 |
| V-12 | Cobertura | `jacoco.csv` | ≥ 90 % líneas y ramas por clase tocada; global ≥ línea base |
| V-13 | Postman y Newman | T-7 | 0 fallos en la carpeta y en la colección completa |
| V-14 | DES-02 | Carpeta `DES-02 — HU-2.3`, 105 iteraciones | p95 ≤ 2000 ms |
| V-15 | Logs | `docker compose logs app` | Sin `ERROR`; sin nombre ni resumen |
| V-16 | Catálogo | `ErrorCodeDocumentationTest` | Verde |
| V-17 | Comentarios | `git grep -n "CM-[0-9]" -- src` | Ninguno |
| V-18 | Sin dependencias | `git diff origin/develop -- pom.xml` | Vacío |
| V-19 | Tamaño | `git diff --shortstat <base>...<rama>` | ≤ 1000 por PR |
| V-20 | Revisiones | `/simplify`, `/code-review high`, `/security-review` | Hallazgos corregidos o descartados con razón |

## 23. Puerta de listo

| Condición | Estado |
|---|---|
| 1. Trazabilidad y atributos | Secciones 4 y 15 |
| 2. Matriz, casos borde, ruta al 500 y plan de seguridad | Secciones 6, 8, 9 y 10 |
| 3. Contraste con `2a54ca7` y ensayo en seco | Hecho el 9-oct-2026 (`analisis/revision-spec_CM-54.md`) |
| 4. Integración | Sección 19 |
| 5. Línea base | Sección 20 |
| 6. Lista de verificación | Sección 22 |
| 7. Dudas | Ninguna |

**Dictamen:** LISTA PARA EJECUTAR, después de CM-274. La aprueba solo Paula.
