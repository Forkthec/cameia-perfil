# Spec — CM-66: habilidades del Perfil Profesional (HU-2.5)

- **Tarea:** CM-66 · subtarea «HU-2.5 – Backend: habilidades del perfil» · padre CM-19 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Código de referencia: `origin/develop` `2a54ca7` (9-oct-2026).
- **Requisitos:** backlog `09102026_01_Backlog_v6.xlsx`, HU-2.5: CA-2.5.1 a 2.5.3, 2.5.7 a 2.5.10, 2.5.12, 2.5.15 a 2.5.17, 2.5.19, 2.5.20, 2.5.23 (la finalización es CM-67); RT-01, RT-03, RT-05, RT-06.
- **Estado:** pendiente de aprobación de Paula. Puerta de listo: sección 23. Decisiones en la sección 17; sin preguntas abiertas (sección 18).
- **Depende de:** CM-274 (`SingleLineText`, `@CodePointSize`, `@DomainRule`, `FieldValues`, bloqueo `loadForUserForUpdate`, acumulador de errores, `ColumnLengthConsistencyIT`) y CM-54 (`collect` en `ProfileAppService`, reglas de `DomainRule` a continuación de las cuales van las de esta tarea), fusionadas. CM-67 depende de esta tarea (CA-2.5.22). Orden de Perfil en la sección 19.

## 1. Objetivo

Que `POST /api/v1/profiles/{id}/skills` y `DELETE /api/v1/profiles/{id}/skills/{skillId}` cumplan cada CA con el estado, `code`, campo y mensaje literal: habilidad de 1 a 60 caracteres con nivel, máximo 20 por perfil, sin repetir (sin distinguir mayúsculas, tildes ni espacios sobrantes, también en la base), y sin eliminar la última de un perfil Activo, aunque haya dos pestañas.

## 2. Estado real del código y causa raíz

| # | Situación en `2a54ca7` | Causa comprobada | Reproducción antes | Después |
|---|---|---|---|---|
| E1 | 409 `SKILL_ALREADY_EXISTS` «Esa habilidad ya está en tu perfil.»; 404 `SKILL_NOT_FOUND`; 204 al eliminar; `SKILL_LEVEL_INVALID_VALUE` «Selecciona una opción.»; `SKILL_NAME_REQUIRED` y `SKILL_LEVEL_REQUIRED` | CM-271 | Postman `ERR DELETE /skills/{ajena} — 404`, `ERR POST /skills — 422 nivel fuera de la lista (CA-2.5.10)` | Se conservan |
| D1 | El límite es 255, no 60 | `ProfileSkill.MAX_NAME_LENGTH = 255`; columna `nombre_habilidad VARCHAR(255)` | `skillName` de 61 → hoy 201 | 422 `SKILL_NAME_TOO_LONG` «La habilidad no puede superar los 60 caracteres.» |
| D2 | El duplicado no ignora tildes | `ProfessionalProfile.normalizarNombreHabilidad` hace `trim`, une espacios y minúsculas, sin quitar marcas | «Programación» guardada + «programacion» → hoy 201 | 409 `SKILL_ALREADY_EXISTS` |
| D3 | Sin máximo de 20 | `addSkill` no cuenta | 21.ª → hoy 201 | 409 `SKILL_LIMIT_REACHED` |
| D4 | Se puede eliminar la última habilidad de un perfil Activo | `removeSkill` solo comprueba pertenencia | `DELETE` con 1 habilidad en `COMPLETED` → 204 | 409 `SKILL_NOT_ALLOWED` |
| D5 | Sin unicidad en la base ni bloqueo | V1 sin `UNIQUE`; lectura sin `FOR UPDATE` | Dos altas simultáneas de «Python» → pueden quedar dos | `uq_habilidad_perfil_nombre` + bloqueo; la violación se traduce a 409 |
| D6 | Recorte de Java, sin NFC, conteo UTF-16; nombre y nivel inválidos a la vez dan un solo error | `FieldRules`; `@NotBlank` en el DTO y el nivel se valida en el servicio | `{"skillName":"","level":"EXPERT"}` → hoy un error | Dos errores a la vez |
| D7 | U+0000 en `skillName` → 500 | PostgreSQL `22021` → `handleUnexpected` | `"Py\u0000"` → 500 | 422 `SKILL_NAME_INVALID_CHARACTERS` |
| D8 | `AddSkillCommand` lanza `IllegalArgumentException`/`NullPointerException` (500 si se llama sin el DTO) | Constructor del `record` | Prueba de servicio | Solo exige `profileId` |
| D9 | Sin `@Schema`; restricciones sin nombre estándar; sin índice en `perfil_id` | V1, DTO | `pg_constraint`, `/v3/api-docs` | V8 y OpenAPI |

## 3. Trazabilidad CA → requisito → prueba → Postman

Carpeta de Postman `HU-2.5 — Habilidades` / `POST skills` y `DELETE skills/{skillId}`.

| CA | Exige | REQ | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| 2.5.1 | 201; `INTERMEDIATE`; procedencia `MANUAL`; se ve «Python · Intermedio» | REQ-HB-01 | `SkillsEndToEndIT.addSkill_shouldReturn201WithManualProvenance_whenSkillIsValid` | `201 Python intermedio (CA-2.5.1)` |
| 2.5.2 | 409 «Esa habilidad ya está en tu perfil.» con «machine learning» frente a «Machine Learning» | REQ-HB-05 | `SkillNameTest.sameAs_shouldMatch_whenNamesDifferOnlyInCaseSpacesOrAccents` · `ProfileControllerTest.addSkill_shouldReturn409_whenSkillIsDuplicated` | `409 machine learning repetida (CA-2.5.2)` |
| 2.5.3 | 201 la vigésima | REQ-HB-04 | `ProfessionalProfileTest.addSkill_shouldAcceptTwentieth_whenProfileHasNineteen` | `201 vigésima habilidad (CA-2.5.3)` |
| 2.5.7 | 422 «Ingresa una habilidad.» en `skillName` o «Elige un nivel.» en `level` | REQ-HB-02 | `ProfileControllerTest.addSkill_shouldReturnRequired_whenFieldIsMissingOrBlank` (parametrizada) · `…_shouldReturnBothFields_whenNameIsBlankAndLevelIsUnknown` | `422 habilidad vacía`, `422 habilidad solo espacios`, `422 sin nivel (CA-2.5.7)` |
| 2.5.8 | 201 con 60 | REQ-HB-03 | `ProfileControllerTest.addSkill_shouldAccept_whenNameIsAtLimit` (60 `a`; 59 `a` + `"😀"`) | `201 habilidad de 60 (CA-2.5.8)` |
| 2.5.9 | 422 «La habilidad no puede superar los 60 caracteres.» | REQ-HB-03 | `ProfileControllerTest.addSkill_shouldReturnTooLong_whenNameExceedsLimit` | `422 habilidad de 61 (CA-2.5.9)` |
| 2.5.10 | 422 en `level` con `EXPERT` | REQ-HB-06 | `ProfileControllerTest.addSkill_shouldReturnLevelInvalid_whenLevelIsUnknown` (`EXPERT`, `basic`) | `422 nivel EXPERT (CA-2.5.10)` (existe) |
| 2.5.12 | 409 «No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.»; conserva habilidad y estado | REQ-HB-08 | `ProfessionalProfileTest.removeSkill_shouldRejectLast_whenProfileIsCompleted` · `ProfileControllerTest.removeSkill_shouldReturn409_whenLastSkillOfActiveProfile` | `409 última habilidad de un perfil Activo (CA-2.5.12)` |
| 2.5.15 | 403 en `POST …/skills` y `DELETE …/skills/{skillId}` (y finalizar, CM-67) | REQ-HB-10 | `ProfileControllerTest.skills_shouldReturn403_whenProfileBelongsToAnotherUser` (las dos rutas) | `403 agregar habilidad a un perfil ajeno`, `403 eliminar habilidad de un perfil ajeno` |
| 2.5.16 | 204 en Borrador con una | REQ-HB-09 | `SkillsEndToEndIT.removeSkill_shouldReturn204_whenDraftHasOnlyOne` | `204 única habilidad en Borrador (CA-2.5.16)` |
| 2.5.17 | 204 con dos en Activo; sigue Activo | REQ-HB-09 | `ProfessionalProfileTest.removeSkill_shouldRemove_whenCompletedProfileHasTwo` | `204 una de dos en Activo (CA-2.5.17)` |
| 2.5.19 | 409 «Ya tienes el máximo de 20 habilidades.» | REQ-HB-04 | `ProfessionalProfileTest.addSkill_shouldRejectTwentyFirst_whenProfileHasTwenty` · `ProfileWriteLockIT.addSkill_shouldKeepTwenty_whenTwoRequestsArriveWithNineteen` | `409 habilidad 21 (CA-2.5.19)` |
| 2.5.20 | 409 con «programacion» frente a «Programación» | REQ-HB-05 | `SkillNameTest.sameAs_shouldMatch_whenNamesDifferOnlyInCaseSpacesOrAccents` (fila) · `SkillsEndToEndIT.addSkill_shouldReturn409_whenNameDiffersOnlyInAccents` | `409 programacion sin tilde (CA-2.5.20)` |
| 2.5.23 | 404 «No encontramos lo que buscabas.», no elimina | REQ-HB-07 | `ProfileControllerTest.removeSkill_shouldReturn404_whenSkillIsNotInProfile` · `SkillsEndToEndIT.removeSkill_shouldKeepOtherProfileSkill_whenIdBelongsToAnotherProfile` | `404 habilidad de otro perfil (CA-2.5.23)`, `404 habilidad inexistente` |
| RT-01-CA04 | Varios campos a la vez | REQ-HB-02 | `ProfileControllerTest.addSkill_shouldReturnBothFields_whenNameIsBlankAndLevelIsUnknown` | `422 nombre vacío y nivel EXPERT` |
| RT-01-CA06 | Campos no esperados ignorados | REQ-HB-12 | `SkillsEndToEndIT.addSkill_shouldIgnoreUnexpectedFields_whenBodyHasThem` | `201 ignora id y status` |

## 4. Requisitos (EARS)

- **REQ-HB-01 (CA-2.5.1).** Cuando llegue un `POST …/skills` válido, Perfil debe agregar la habilidad con el nombre normalizado para guardar (§5), el nivel recibido y la procedencia `MANUAL` (la que venga en el cuerpo se ignora, D9), y responder 201 con el perfil.
- **REQ-HB-02 (CA-2.5.7, RT-01-CA04).** `skillName` ausente, `null`, vacío o solo espacios → 422 `{field:"skillName", code:"SKILL_NAME_REQUIRED", message:"Ingresa una habilidad."}`; `level` ausente, `null` o vacío → `{field:"level", code:"SKILL_LEVEL_REQUIRED", message:"Elige un nivel."}`. Todos los errores de forma a la vez.
- **REQ-HB-03 (CA-2.5.8, 2.5.9).** `skillName` de más de 60 puntos de código tras recortar y NFC → 422 `SKILL_NAME_TOO_LONG` «La habilidad no puede superar los 60 caracteres.».
- **REQ-HB-04 (CA-2.5.3, 2.5.19).** Con 20 habilidades, agregar otra → **409** `SKILL_LIMIT_REACHED` «Ya tienes el máximo de 20 habilidades.», sin `errors`; el máximo se comprueba antes que el duplicado. La vigésima se agrega.
- **REQ-HB-05 (CA-2.5.2, 2.5.20).** Dos nombres son la misma habilidad si coinciden sus claves de comparación (§5). Repetida → **409** `SKILL_ALREADY_EXISTS` «Esa habilidad ya está en tu perfil.», con `errors` `[{field:"skillName", code:"SKILL_ALREADY_EXISTS", message:"Esa habilidad ya está en tu perfil."}]` para que la interfaz lo muestre bajo el campo (CA: «bajo el campo "Habilidad"»).
- **REQ-HB-06 (CA-2.5.10).** `level` distinto de `BASIC`, `INTERMEDIATE`, `ADVANCED` (exacto) → 422 `SKILL_LEVEL_INVALID_VALUE` «Selecciona una opción.».
- **REQ-HB-07 (CA-2.5.23).** Eliminar un identificador que no pertenece al perfil (de otro perfil o inexistente) → 404 `SKILL_NOT_FOUND` «No encontramos lo que buscabas.», sin eliminar nada.
- **REQ-HB-08 (CA-2.5.12).** Con el perfil `COMPLETED` y una sola habilidad, eliminarla → **409** `SKILL_NOT_ALLOWED` «No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.», sin cambios. La pertenencia se comprueba antes (404 antes que 409).
- **REQ-HB-09 (CA-2.5.16, 2.5.17).** Eliminar responde 204; en `IN_PROGRESS` no hay mínimo.
- **REQ-HB-10 (CA-2.5.15).** Perfil ajeno → 403 `PROFILE_NOT_ALLOWED` en las dos rutas, sin cambios.
- **REQ-HB-11.** El máximo, el duplicado y el mínimo se comprueban con el perfil bloqueado (CM-274 PR 1).
- **REQ-HB-12 (RT-01-CA06).** Campos no esperados (`id`, `status`, `firebaseUid`, `profileId`) se ignoran.
- **REQ-HB-13 (base de datos).** `uq_habilidad_perfil_nombre (perfil_id, nombre_normalizado)` repite la unicidad; si se viola (escritura que saltó el bloqueo), Perfil responde 409 `SKILL_ALREADY_EXISTS`, nunca 500.

## 5. Normalización del nombre

| Uso | Regla | Ejemplo |
|---|---|---|
| **Guardar y mostrar** (`SkillName.value`) | `SingleLineText.normalize`: recorte de los espacios de JavaScript y NFC. Mayúsculas, tildes y espacios internos se conservan (decisión D2) | `"  Machine   Learning "` → `"Machine   Learning"` |
| **Comparar** (`SkillName.comparisonKey`, columna `nombre_normalizado`) | sobre el valor guardado: unir cada secuencia de espacios (los de `SingleLineText`) en un espacio; `toLowerCase(Locale.ROOT)`; `Normalizer.normalize(…, NFD)`; quitar `\p{M}`; volver a NFC | `"Machine   Learning"` → `"machine learning"`; `"Programación"` → `"programacion"`; `"Diseño"` → `"diseno"` (decisión de Paula del 6-oct-2026) |

Pares que deben ser iguales: `Machine Learning`/`machine learning`, `Machine Learning`/`  MACHINE   LEARNING `, `Programación`/`programacion`, `PROGRAMACIÓN`/`programación`, `Diseño`/`Diseno`, `Python`/`python ` (U+00A0 al final), `Ü`/`u`. Pares distintos: `C`/`C++`, `C#`/`C`, `Java`/`JavaScript`, `R`/`R `(igual tras recorte → **iguales**), `Go`/`Gó` (iguales). Se prueban todos.

## 6. Matriz de validación por campo (R2) — `POST …/skills`

| Campo | Causa (en orden) | Valores | Fase | Estado · `code` | Mensaje |
|---|---|---|---|---|---|
| `skillName` | ausente, `null`, `""`, `"   "`, `"\t\n"`, `" "`, `"﻿"` | los siete | B `@NotBlank` | 422 `SKILL_NAME_REQUIRED` | «Ingresa una habilidad.» |
| `skillName` | carácter de control (categoría Cc) | `"Py\u0000"`, `"Py\u0007"` | B `@DomainRule(Rule.SKILL_NAME)` | 422 `SKILL_NAME_INVALID_CHARACTERS` | «La habilidad tiene caracteres no permitidos.» |
| `skillName` | más de 60 | 61 `a`; 60 `a` + `"😀"`; 61 `ñ` | B `@CodePointSize(max = 60)` | 422 `SKILL_NAME_TOO_LONG` | «La habilidad no puede superar los 60 caracteres.» |
| `skillName` | válido | 59, 60 `a`; 59 `a` + `"😀"`; `"é"` × 60; `"  Python  "` → `"Python"`; `"<b>SQL</b>"` literal; `"100 %"`, `"a_b"` | — | 201 | — |
| `level` | ausente, `null`, `""`, `"  "` | — | B `@NotBlank` | 422 `SKILL_LEVEL_REQUIRED` | «Elige un nivel.» |
| `level` | fuera del conjunto | `"EXPERT"`, `"basic"`, `"Basic"` | B `@DomainRule(Rule.SKILL_LEVEL)` | 422 `SKILL_LEVEL_INVALID_VALUE` | «Selecciona una opción.» |
| `provenance` | cualquier valor o ausente | `"AI_SUGGESTED"`, `"HUMAN"`, sin clave | — | se ignora; se guarda y se devuelve `MANUAL` (D9) | — |
| estado | 20 habilidades | — | D | 409 `SKILL_LIMIT_REACHED` | «Ya tienes el máximo de 20 habilidades.» |
| estado | repetida | §5 | D | 409 `SKILL_ALREADY_EXISTS` | «Esa habilidad ya está en tu perfil.» |
| cuerpo | JSON mal formado, objeto o arreglo en un texto | — | Jackson | 422 `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» |
| cuerpo | número en un texto (`"skillName": 123`) | — | — | se convierte en `"123"` (V1 de CM-274) | — |
| cuerpo | `text/plain` | — | Spring | 415 `MEDIA_TYPE_NOT_ALLOWED` | «Tipo de contenido no admitido.» |
| ruta | `{id}` o `{skillId}` no UUID | `"x"` | — | 422 `PROFILE_ID_INVALID_FORMAT` / `SKILL_ID_INVALID_FORMAT` | sus textos |
| ruta | perfil inexistente | — | — | 404 `PROFILE_NOT_FOUND` | «No encontramos lo que buscabas.» |
| encabezado | `X-User-Id` ausente, en blanco, 129 | — | — | 401 `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» |

## 7. Base de datos (R4)

`V8__alinear_habilidad_perfil.sql` (número fijo del orden de Perfil: V5 CM-279, V6 y V7 CM-274, V9 CM-67):

```sql
ALTER TABLE habilidad_perfil RENAME CONSTRAINT habilidad_perfil_pkey TO pk_habilidad_perfil;
ALTER TABLE habilidad_perfil RENAME CONSTRAINT habilidad_perfil_perfil_id_fkey TO fk_habilidad_perfil_perfil;
ALTER TABLE habilidad_perfil ALTER COLUMN nombre_habilidad TYPE VARCHAR(60);
ALTER TABLE habilidad_perfil ADD COLUMN nombre_normalizado VARCHAR(60);
-- Aproximación de la regla de la aplicación para las filas existentes: solo tildes del español y ñ
-- (PostgreSQL no quita tildes sin la extensión unaccent). La aplicación reescribe la columna en cada guardado.
UPDATE habilidad_perfil
   SET nombre_normalizado = translate(lower(regexp_replace(btrim(nombre_habilidad), '\s+', ' ', 'g')),
                                      'áéíóúüñàèìòùâêîôûäëïö', 'aeiouunaeiouaeiouaeio');
ALTER TABLE habilidad_perfil ALTER COLUMN nombre_normalizado SET NOT NULL;
ALTER TABLE habilidad_perfil
    ADD CONSTRAINT ck_habilidad_perfil_nombre_presente CHECK (length(btrim(nombre_habilidad)) > 0),
    ADD CONSTRAINT ck_habilidad_perfil_nivel CHECK (nivel IN ('BASIC', 'INTERMEDIATE', 'ADVANCED')),
    ADD CONSTRAINT ck_habilidad_perfil_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT uq_habilidad_perfil_nombre UNIQUE (perfil_id, nombre_normalizado);
CREATE INDEX ix_habilidad_perfil_perfil_id ON habilidad_perfil (perfil_id);
```

- `uq_habilidad_perfil_nombre` crea su propio índice con `perfil_id` primero; el `ix_habilidad_perfil_perfil_id` se conserva igual por el estándar (índice explícito en cada clave foránea). Si Paula prefiere no duplicarlo, se quita (no cambia el comportamiento).
- `ProfileSkillEntity`: `nombre_habilidad` `length = 60`; nuevo `nombre_normalizado` `nullable = false, length = 60`. `ProfessionalProfileMapping.toSkillEntity` escribe `SkillName.comparisonKey()` en cada guardado.
- **Traducción de la violación:** `ProfessionalProfileRepositoryAdapter.save` usa `jpa.saveAndFlush` y, si la causa es una violación de `uq_habilidad_perfil_nombre` (`SQLState 23505` y nombre de la restricción), lanza `DuplicateSkillException`. **Trampa:** con `save` sin `flush`, la violación aparece al confirmar la transacción, fuera del adaptador, y llega al 500.
- **Verificación previa en staging (la ejecuta Paula, antes de desplegar el PR de la migración de habilidades).** Complementa el pedido 17 ya respondido (máximo 12 caracteres, 0 perfiles con más de 20). **Cómo:** conéctate a la base de **Perfil** de staging con la conexión de solo lectura que ya usas (sin imprimir credenciales ni pegarlas en el chat), abre `BEGIN READ ONLY;`, ejecuta cada consulta y termina con `ROLLBACK;`. Anota el número de cada una y la fecha en `ESTADO.md`. Si alguna no da lo esperado, **no se despliega** el PR indicado: se reporta la fila (sin datos personales) y se decide si se corrige el dato antes o se ajusta la migración.

  | # | Consulta (solo lectura) | Esperado |
  |---|---|---|
  | 1 | `select count(*) from (select perfil_id, translate(lower(regexp_replace(btrim(nombre_habilidad), '\s+', ' ', 'g')), 'áéíóúüñàèìòùâêîôûäëïö', 'aeiouunaeiouaeiouaeio') k from habilidad_perfil group by 1, 2 having count(*) > 1) d;` | 0 (si no, `uq_habilidad_perfil_nombre` falla y el servicio no arranca). Para ver cuáles: la misma consulta sin el `count` exterior y con `count(*)` en la lista |
  | 2 | `select count(*) from habilidad_perfil where lower(nombre_habilidad) ~ '[^ -~áéíóúüñàèìòùâêîôûäëïö]';` | Informativo: letras fuera de la lista de `translate` (por ejemplo «ç») que tendrían una clave distinta de la de Java hasta el siguiente guardado (riesgo aceptado); anota el número |
- **Riesgo aceptado:** una fila existente con una letra fuera de la lista de `translate` (por ejemplo «ç») recibe una clave distinta de la de Java hasta el siguiente guardado de su perfil; si ese perfil tuviera otra habilidad con la misma clave de Java, ese guardado respondería 409 `SKILL_ALREADY_EXISTS`. Staging tiene nombres de 12 caracteres como máximo (pedido 17) y la consulta previa de DevOps lo descarta.
- **Pruebas:** `SkillSchemaIT` (migración sobre una base V7 con una fila «Programación» → `nombre_normalizado` `programacion`; cada `ck_` y el `uq_` violados por JDBC con su `SQLState`; nombres de restricción e índice); `ColumnLengthConsistencyIT` (`nombre_habilidad` = `SkillName.MAX_LENGTH`); `ProfessionalProfileRepositoryAdapterIT.save_shouldThrowDuplicateSkill_whenComparisonKeyRepeatsInProfile` (agregado reconstituido con dos habilidades de la misma clave → `DuplicateSkillException`).

## 8. Errores (R3)

| `code` | HTTP | Excepción | Mensaje | Estado |
|---|---|---|---|---|
| `SKILL_LIMIT_REACHED` | 409 | `SkillLimitReachedException` (nueva) | «Ya tienes el máximo de 20 habilidades.» | Nuevo |
| `SKILL_NOT_ALLOWED` | 409 | `SkillNotAllowedException` (nueva) | «No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.» | Nuevo |
| `SKILL_NAME_INVALID_CHARACTERS` | 422 (campo) | `@DomainRule` en el borde y `SkillName.of` (constante `SkillName.CHARACTERS`) | «La habilidad tiene caracteres no permitidos.» | Nuevo |
| `SKILL_NAME_TOO_LONG` | 422 (campo) | Bean Validation + `SkillName.of` | «La habilidad no puede superar los 60 caracteres.» | Cambia de 255 a 60 |
| `SKILL_ALREADY_EXISTS` | 409 | `DuplicateSkillException` (también desde el adaptador) | «Esa habilidad ya está en tu perfil.» | Existe; agrega `errors[skillName]`; también desde la traducción del `UNIQUE` |
| `SKILL_NAME_REQUIRED`, `SKILL_LEVEL_REQUIRED`, `SKILL_LEVEL_INVALID_VALUE`, `SKILL_NOT_FOUND`, `SKILL_ID_INVALID_FORMAT` | — | — | Sin cambio | Existen |

Rutas al 500 hoy y su destino: U+0000 (→ 422); violación de `UNIQUE` futura (→ 409 por la traducción del adaptador); `AddSkillCommand` con `null` (→ el dominio responde por campo). El 409 de duplicado lleva `errors[{field:"skillName", code:"SKILL_ALREADY_EXISTS", message:"Esa habilidad ya está en tu perfil."}]`, igual que el 409 `EMAIL_ALREADY_REGISTERED` de Cuentas lleva `errors[email]` (D7); lo agrega `ApiExceptionHandler.handleBusiness` para `DuplicateSkillException`.

## 9. Casos borde (R5)

| Grupo | Caso | Cómo |
|---|---|---|
| Presencia | §6 | Parametrizadas |
| Texto | 59/60/61 con `a`, `ñ`, emoji, NFD; espacios en extremos (se recortan) e internos (se guardan; se unen para comparar); control; `<b>`, `%`, `_` (sin búsquedas `LIKE`: texto literal) | §5, §6 |
| Números, fechas | No aplican | Justificado |
| Enumerados | `level` válido, desconocido, minúsculas, vacío | §6 |
| Duplicados | otra mayúscula, tilde, «ñ», espacios internos y extremos, U+00A0; duplicado concurrente (dos «Python» a la vez → una 201 y otra 409) | `SkillNameTest`, `ProfileWriteLockIT.addSkill_shouldKeepOne_whenSameSkillArrivesTwiceAtOnce` |
| Estado | eliminar dos veces (204 y 404); última en `COMPLETED` (409); en `IN_PROGRESS` (204) | `ProfessionalProfileTest` |
| Propiedad | perfil ajeno 403; habilidad de otro perfil 404 y la otra sigue | §3 |
| Colecciones | 0→1, 19→20 (201), 20→21 (409); con 20 y repetida → 409 `SKILL_LIMIT_REACHED` (el máximo va primero) | `ProfessionalProfileTest` |
| Concurrencia | dos altas con 19 (20 en total); dos bajas con 2 en Activo (204 y 409) | `ProfileWriteLockIT` |
| Falla parcial | Una transacción; la violación del `UNIQUE` revierte todo | `ProfessionalProfileRepositoryAdapterIT` |
| Dependencias | Ninguna externa | Justificado |
| Carga | `skillName` de 1 MiB → 422 `SKILL_NAME_TOO_LONG` sin 500; JSON mal formado; `text/plain`; campos extra | `SkillsEndToEndIT.addSkill_shouldReturn422_whenNameIsOneMebibyte` |

## 10. Seguridad (R6)

### 10.1 ASVS 5.0.0 nivel 1 (`cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`, `4c46e89`)

| ID | Aplica | Qué lo cumple | Prueba |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | `ProfileResponse` por Jackson | `ProfileControllerTest` |
| 1.2.4 Consultas parametrizadas | Sí | Sin SQL nuevo con datos; la clave se compara en memoria y la unicidad la impone `uq_habilidad_perfil_nombre` | `SkillSchemaIT` |
| 1.3.2 Sin evaluación dinámica | Sí | El mensaje de `@DomainRule` se escapa | `DomainRuleValidatorTest` |
| 2.2.1 Validación con lista permitida | Sí | 60 puntos de código, control rechazado, nivel exacto | Matriz de la sección 6 |
| 2.2.2 Validación en el servidor | Sí | Borde, dominio y restricción `UNIQUE` | `SkillsEndToEndIT`; `ProfessionalProfileRepositoryAdapterIT.save_shouldThrowDuplicateSkill_…` |
| 2.3.1 Flujo de negocio en orden | Sí | Máximo, repetida y mínimo con la fila bloqueada | `ProfileWriteLockIT` |
| 4.1.1 `charset` | Sí | Forzado en Perfil | `ProfileControllerTest`; Newman |
| 8.2.2 / 8.3.1 BOLA | Sí | `loadForUserForUpdate`; habilidad de otro perfil → 404 sin tocarla | `ProfileControllerTest.skills_shouldReturn403_whenProfileBelongsToAnotherUser`; `SkillsEndToEndIT.removeSkill_shouldKeepOtherProfileSkill_whenIdBelongsToAnotherProfile` |
| 15.3.1 Respuesta con lo necesario | Sí | `ProfileSkillItem` sin `nombre_normalizado` | `ProfileControllerTest` (el cuerpo no tiene la clave) |

### 10.2 OWASP API Security Top 10 — `POST …/skills` y `DELETE …/skills/{skillId}`

| Riesgo | Cómo | Prueba |
|---|---|---|
| API1 BOLA | Dueño por `X-User-Id`; habilidad ajena → 404 | Las de 8.2.2 |
| API2 Autenticación | Sin `X-User-Id` → 401; `firebaseUid` del cuerpo ignorado | `ProfileControllerTest` (401); `SkillsEndToEndIT.addSkill_shouldIgnoreUnexpectedFields_whenBodyHasThem` |
| API3 Propiedades del objeto | `provenance`, `id`, `status` no se enlazan | La misma |
| API4 Consumo de recursos | 60 caracteres, 20 por perfil, espera de 2 s; 1 MiB → 422 sin 500 | `ProfessionalProfileTest.addSkill_shouldRejectTwentyFirst_…`; `SkillsEndToEndIT.addSkill_shouldReturn422_whenNameIsOneMebibyte` |
| API5 Autorización por función | Solo el dueño | Las de API1 |
| API6 Flujos sensibles | Repetir el mismo `POST` no duplica (409); eliminar la última en Activo está protegido | `…addSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes`; `ProfileWriteLockIT` |
| API7 SSRF | No aplica | — |
| API8 Configuración | Sin traza ni nombres de clase | V-08 de la lista de verificación |
| API9 Inventario | Las dos rutas en OpenAPI con todos sus estados | `OpenApiDocumentIT` |
| API10 Consumo inseguro | No aplica | — |

Logs sin el nombre de la habilidad (SEG-04).

## 11. Código y patrones (R7)

- **Crea:** `domain/model/SkillName` (`record` con `MAX_LENGTH = 60`, `of(String)` que normaliza y valida, constructor canónico que solo exige no nulo para reconstruir, `comparisonKey()` y `sameAs(SkillName)`; reemplaza `normalizarNombreHabilidad` del agregado y la regla de texto de `ProfileSkill`), `SkillLimitReachedException`, `SkillNotAllowedException`, migración V8, `SkillsEndToEndIT`, `SkillNameTest`, `SkillSchemaIT`.
- **Modifica:** `ProfileSkill` (recibe `SkillName`), `ProfessionalProfile` (`MAX_SKILLS = 20`; `addSkill`: máximo y luego duplicado con `sameAs`; `removeSkill`: pertenencia y luego mínimo), `AddSkillRequest` (normalización y restricciones; `@Schema`), `AddSkillCommand` (solo `profileId`), `ProfileAppService.addSkill`, `ProfileSkillEntity`, `ProfessionalProfileMapping`, `ProfessionalProfileRepositoryAdapter.save` (`saveAndFlush` y traducción), `ApiExceptionHandler` (`errors` del duplicado), `ErrorCode`, `ErrorCatalog`, `ProfileController` (OpenAPI), `docs/errores.md`, `ProfileResponse.ProfileSkillItem` (`@Schema`).
- **Reutiliza:** todo lo de CM-274 PR 1, `DuplicateSkillException`, `SkillNotFoundException`, `ProfileWriteLockIT`, `ColumnLengthConsistencyIT`.
- **Idioma (R1):** identificadores y nombres de prueba en inglés; Javadoc, comentarios (también los de SQL), OpenAPI y logs en español.

## 12. Documentación (R8)

`@Schema` en `AddSkillRequest` (`skillName` `minLength = 1`, `maxLength = 60`; `level` `allowableValues = {"BASIC","INTERMEDIATE","ADVANCED"}`; sin `provenance`) y en `ProfileSkillItem`. `@ApiResponse` de las dos rutas: 201/204, 401, 403, 404, 409 (`SKILL_ALREADY_EXISTS`, `SKILL_LIMIT_REACHED`, `SKILL_NOT_ALLOWED`), 415, 422, 409 `PROFILE_UPDATE_IN_PROGRESS`. `docs/errores.md`: filas de §8; el «Pendiente con destino» de la última habilidad pasa a la fila de `SKILL_NOT_ALLOWED` 409. Comentario de bloque en `SkillName.comparisonKey` con la regla y su porqué.

## 13. Pruebas (R9) y cobertura (R10)

- Dominio: `SkillNameTest` (todos los pares de §5, límites, NFC, control, reconstrucción sin validar), `ProfessionalProfileTest` (máximo, duplicado, mínimo, orden máximo-antes-que-duplicado, 404-antes-que-409).
- Web: `ProfileControllerTest` (§3 y §6; `errors` del 409 de duplicado; `Content-Type`; sin traza).
- Servicio: `ProfileAppServiceTest.addSkill_shouldThrowFieldErrors_whenCommandHasNullRequiredFields`.
- Integración: `SkillsEndToEndIT`, `SkillSchemaIT`, `ProfessionalProfileRepositoryAdapterIT` (traducción del `UNIQUE`), `ProfileWriteLockIT` (dos altas con 19; dos «Python» a la vez; dos bajas con 2 en Activo), `ColumnLengthConsistencyIT`, `OpenApiDocumentIT`.
- Cobertura ≥ 90 % de líneas y ramas en `SkillName`, `ProfileSkill`, `ProfessionalProfile`, `SkillLimitReachedException`, `SkillNotAllowedException`, `AddSkillRequest`, `AddSkillCommand`, `ProfileAppService`, `ProfessionalProfileMapping`, `ProfessionalProfileRepositoryAdapter`, `ApiExceptionHandler`, `ErrorCatalog`; global sin bajar. `./mvnw.cmd -B clean verify` → `target/site/jacoco/jacoco.csv`.

## 14. Postman y Newman (R11)

Carpeta `HU-2.5 — Habilidades`: una petición por fila de §3 y por causa de §6 (vacío, espacios, U+00A0, 59, 60, 61, 60 con emoji, control, `EXPERT`, `basic`, sin nivel, dos campos a la vez, repetidas con mayúsculas, con tilde, con «ñ» y con espacios, vigésima, vigésima primera, última en Activo, una de dos en Activo, única en Borrador, de otro perfil, inexistente, dos veces la misma, ajeno en las dos rutas, sin identidad, cuerpo ilegible, `text/plain`, campos extra). La vigésima y la 21.ª se arman con un bucle en el `pre-request` (`pm.sendRequest` 19 veces). Corrida: `docker compose down -v`, `docker compose up -d --build`, `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-2.5 — Habilidades" --reporters cli,junit`; se pega la salida.

## 15. Atributos de calidad (R13)

Anexo, cuadro 9: **HE-02** declara AC-0001, AC-0003, AC-0006 y MAN-02; se agregan las que el cambio toca. «Resultado real» lo llenan la ejecución y la verificación.

| Atributo | Métrica (ID) | Límite | Cómo lo cumple | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0001 (HE-02) | IA-01 | 100 % con procedencia válida | `provenance` del cuerpo ignorado; se guarda `MANUAL`; `CHECK` de procedencia | `SkillsEndToEndIT.addSkill_shouldReturn201WithManualProvenance_whenSkillIsValid`; `SkillSchemaIT` (`ck_habilidad_perfil_procedencia`) | PENDIENTE |
| AC-0003 (HE-02) | SEG-01 | ASVS N1 aplicable | Sección 10.1 | Las de 10.1 | PENDIENTE |
| AC-0003 (HE-02) | SEG-02 | 100 % de accesos cruzados rechazados | 403 y 404 por propiedad, con dos Usuarios | `ProfileControllerTest.skills_shouldReturn403_…`; `SkillsEndToEndIT.removeSkill_shouldKeepOtherProfileSkill_…` | PENDIENTE |
| AC-0003 (HE-02) | SEG-03 | Sin dependencias nuevas | — | `git diff origin/develop -- pom.xml` vacío | PENDIENTE |
| AC-0003 (HE-02) | SEG-04 | Cero datos sensibles en logs | Nunca el nombre de la habilidad | `docker compose logs app \| Select-String -Pattern 'Programaci\|machine learning\|Python'` → 0 | PENDIENTE |
| AC-0006 (HE-02) | INT-06 (parte de Backend) | El cliente muestra estado y acción | Mensajes literales; la repetida bajo el campo (`errors[skillName]`) | `ProfileControllerTest.addSkill_shouldReturn409_whenSkillIsDuplicated` | PENDIENTE |
| MAN-02 (HE-02) | MAN-02 | Adaptador con éxito, formato inválido, tiempo de espera y cuota | `ProfessionalProfileRepositoryAdapter.save`: éxito, violación de `UNIQUE` traducida (formato inválido de la fila), espera agotada del bloqueo (de CM-274); cuota no aplica | `ProfessionalProfileRepositoryAdapterIT.save_shouldThrowDuplicateSkill_…`; `ProfileWriteLockIT` | PENDIENTE |
| AC-0002 (cambio) | DES-02 | p95 ≤ 2 s, 100 observaciones | Índice en `perfil_id`; comparación en memoria sobre ≤ 20 | Carpeta `DES-02 — HU-2.5 habilidades`, `-n 105`, script de p95 | PENDIENTE |
| AC-0004 (cambio) | FIA-01 | Todo fallo conserva el último estado | Bloqueo; `UNIQUE` traducido sin efectos; nunca un Activo sin habilidades | `ProfileWriteLockIT`; `ProfessionalProfileRepositoryAdapterIT` | PENDIENTE |
| AC-0004 (cambio) | FIA-02 | Cero efectos en 20 repeticiones | El mismo `POST` → una habilidad; el mismo `DELETE` → un 204 | `SkillsEndToEndIT.addSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes`, `…removeSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | PENDIENTE |
| Control | MAN-01 | ≥ 90 % líneas y ramas en lo tocado | Sección 13 | `jacoco.csv` | PENDIENTE |
| Control | IOP-01 | OpenAPI completo | Sección 12 | `OpenApiDocumentIT` | PENDIENTE |
| Control | IOP-04 | Outbox | No aplica aquí: CM-67 publica `perfil.profesional-actualizado`, también al agregar o quitar habilidades de un perfil `COMPLETED` (decisión de Paula del 9-oct-2026) | — | No aplica |
| Restricción | REST-0002 | Límites por usuario | 20 por perfil, 60 caracteres | `ProfessionalProfileTest` | PENDIENTE |

## 16. Fuera de alcance (con destino)

Finalización (CM-67) · catálogo de habilidades (no existe en el MVP) · `Idempotency-Key` (no lo pide HU-2.5) · evento de Perfil (CM-67) · la regla de duplicado de `cameia-web` (`SkillsSection.tsx` solo recorta y pasa a minúsculas): aviso a Frontend para que también ignore tildes y espacios internos.

## 17. Decisiones

| # | Decisión | Porqué | Alternativas | Decisión humana |
|---|---|---|---|---|
| D1 | La igualdad de nombres vive en `SkillName` | Una sola definición, probada sin Spring | Método privado del agregado | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D2 | Se guarda el nombre recortado tal como se escribió; la clave se usa solo para comparar | La persona ve lo que escribió | Guardar normalizado | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D3 | Columna `nombre_normalizado` y `UNIQUE (perfil_id, nombre_normalizado)` | Unicidad por restricción (estándar) | Solo el bloqueo | Paula, 6-oct-2026 |
| D4 | «Diseño» y «Diseno» son la misma habilidad | «Sin tildes» en el CA | Conservar la «ñ» | Paula, 6-oct-2026 |
| D5 | 204 al eliminar | Literal de los CA | — | Paula, 6-oct-2026 |
| D6 | El máximo se comprueba antes que el duplicado | Con 20 no se agrega ninguna; un solo mensaje claro | Duplicado primero | Paula, 9-oct-2026 |
| D7 | El 409 de duplicado lleva también `errors[skillName]` | CA-2.5.2 y 2.5.20 lo muestran bajo el campo; es la forma del 409 `EMAIL_ALREADY_REGISTERED` de Cuentas (`errors[email]`) | Solo `code` y `detail` | Paula, 9-oct-2026 (precedente de Cuentas) |
| D8 | Las filas existentes se normalizan en SQL con `translate` (acentos del español) y la aplicación reescribe la clave en cada guardado | PostgreSQL no quita tildes sin `unaccent`; la extensión exige permisos de DevOps | Migración en Java; extensión `unaccent` | Paula, 9-oct-2026 |
| D9 | `POST …/skills` ignora `provenance` y guarda `MANUAL` | IA-01 y CA-2.5.1 («procedencia MANUAL»); la IA (HU-2.8) escribe por sus propias rutas; mismo criterio que el `PATCH` (D5 de CM-54) | Aceptar solo `MANUAL`; dejarlo como está | Paula, 9-oct-2026 |

## 18. Preguntas abiertas

Ninguna. El texto de control «La habilidad tiene caracteres no permitidos.» sigue la regla D11 de CM-274 (decidida por Paula el 9-oct); el aviso para que el backlog lo incorpore va con el contrato aprobado.

## 19. Integración con las otras CM de Perfil

Orden en serie: CM-279 P1 → CM-279 P2 → CM-274 → CM-54 → **CM-66** → CM-67 (detalle en la sección 19 de la spec de CM-274). Esta CM agrega `V8__alinear_habilidad_perfil.sql`, los códigos `SKILL_NAME_INVALID_CHARACTERS`, `SKILL_LIMIT_REACHED` y `SKILL_NOT_ALLOWED` al final de `ErrorCode` con su fila en `docs/errores.md`, las reglas `SKILL_NAME` y `SKILL_LEVEL` al final de `DomainRule.Rule` (después de las de CM-54), reutiliza `collect` de CM-54 y crea las carpetas `HU-2.5 — Habilidades` y `DES-02 — HU-2.5 habilidades` de la colección.

## 20. Línea base verde

En la base de la rama del PR 1 (`origin/develop` con CM-54 y el PR 0): `./mvnw.cmd -B clean verify` en verde (0 fallos, 0 omitidas, se anota el número y la cobertura global), migraciones V1 a V7, y Newman de la colección completa con `-e docs/perfil-local.postman_environment.json` sin fallos. Si el puerto 5432 está ocupado, `$env:POSTGRES_PORT='55433'`. Salida en `analisis/ejecucion_CM-66.md`.

## 21. Regla → evidencia (R1 a R14)

R1 diff revisado · R2 sección 6 · R3 sección 8 (U+0000 y `UNIQUE` sin 500) · R4 V8 y `SkillSchemaIT` · R5 sección 9 · R6 sección 10 · R7 sección 11 y `ArquitecturaTest` · R8 sección 12 · R9 sección 13 · R10 `jacoco.csv` · R11 sección 14 y Newman completo · R12 sección 3 · R13 sección 15 · R14 sin push, PR ni Jira sin el sí de Paula. Todas cubiertas.

## 22. Lista de verificación de la CM

| # | Comprobación | Esperado |
|---|---|---|
| V-01 | Cada prueba y petición de la sección 3 existe (`git grep`) | Todas |
| V-02 | `./mvnw.cmd -B clean verify` | Verde, 0 omitidas |
| V-03 | Pares de la sección 5 en `SkillNameTest` | Iguales y distintos como dice la tabla |
| V-04 | 61 caracteres, U+0000, 1 MiB, `text/plain` por petición real | 422/415, ningún 500 |
| V-05 | 20.ª y 21.ª; repetida con 20 | 201; 409 `SKILL_LIMIT_REACHED` |
| V-06 | Última en Activo; dos pestañas | 409 `SKILL_NOT_ALLOWED`; nunca Activo sin habilidades |
| V-07 | 409 de repetida | `errors[0]` = `skillName`, `SKILL_ALREADY_EXISTS`, mensaje literal |
| V-08 | Forma de error de cualquier 4xx | `problem+json;charset=UTF-8`, `code`, `requestId`; sin traza |
| V-09 | `SkillSchemaIT` | Clave rellenada; cada `ck_` y el `uq_` rechazan su fila |
| V-10 | 20 repeticiones (`POST` y `DELETE`) | Sin efectos adicionales |
| V-11 | Cobertura | ≥ 90 % por clase de la sección 13; global ≥ línea base |
| V-12 | Newman (carpeta y colección completa) y DES-02 | 0 fallos; p95 ≤ 2000 ms |
| V-13 | Logs; `CM-NNN`; `pom.xml` | Sin nombres de habilidad; ninguno; sin cambios |
| V-14 | Tamaño de cada PR y revisiones (`/simplify`, `/code-review high`, `/security-review`) | ≤ 1000; hallazgos con destino |

## 23. Puerta de listo

Trazabilidad y atributos (3, 15), matriz, casos borde y seguridad (6, 9, 10), contraste con `2a54ca7` hecho el 9-oct-2026, integración (19), línea base (20) y lista de verificación (22) completos; sin dudas abiertas.

**Dictamen:** LISTA PARA EJECUTAR, después de CM-54. La aprueba solo Paula.
