# Plan — CM-66 (cameia-perfil)

Base: `origin/develop` con CM-274 y CM-54 fusionados (orden de Perfil: CM-279 P1 → P2 → CM-274 → CM-54 → **CM-66** → CM-67). La spec va en un PR 0 de solo documentos. Estado: pendiente de aprobación de Paula.

| PR | Rama | Tarjetas | Líneas | Horas | Bloqueos |
|---|---|---|---|---|---|
| 1 | `CM-66-habilidades` | T-0 a T-6 | ≈ 780 | 4,5 | — |
| 2 | `CM-66-postman-habilidades` | T-7 | ≈ 550 | 1,5 | — |

## Diseño

1. **Borde.** `AddSkillRequest` normaliza (`SingleLineText.normalize`) y valida: `skillName` `@NotBlank @CodePointSize(max = 60) @DomainRule(Rule.SKILL_NAME)`; `level` `@NotBlank @DomainRule(Rule.SKILL_LEVEL)`; sin `provenance` (se guarda `MANUAL`).
2. **Dominio.** `SkillName` es la única regla de nombre: `of(raw)` normaliza y valida (invariante), el constructor canónico solo exige no nulo (para reconstruir), `comparisonKey()` aplica §5 de la spec, `sameAs(other)` compara claves. `ProfessionalProfile.addSkill`: máximo de 20 (409) y luego duplicado (409); `removeSkill`: pertenencia (404) y luego mínimo en `COMPLETED` (409).
3. **Persistencia.** V8 (columna `nombre_normalizado`, `UNIQUE`, `CHECK`, nombres estándar, índice). El mapeo escribe la clave en cada guardado. El adaptador hace `saveAndFlush` y traduce la violación de `uq_habilidad_perfil_nombre` a `DuplicateSkillException`.
4. **Errores.** `SkillLimitReachedException` y `SkillNotAllowedException` (409). El 409 de duplicado agrega `errors[skillName]` en `ApiExceptionHandler.handleBusiness`, como el 409 de correo repetido de Cuentas.

## Archivos

| Crear | Modificar | No tocar |
|---|---|---|
| `domain/model/SkillName`, `domain/exception/SkillLimitReachedException`, `domain/exception/SkillNotAllowedException`, `db/migration/V8__alinear_habilidad_perfil.sql`; pruebas `SkillNameTest`, `SkillsEndToEndIT`, `SkillSchemaIT` | `ProfileSkill`, `ProfessionalProfile`, `AddSkillRequest`, `AddSkillCommand`, `ProfileAppService`, `ProfileSkillEntity`, `ProfessionalProfileMapping`, `ProfessionalProfileRepositoryAdapter`, `DomainRule` (reglas `SKILL_NAME` y `SKILL_LEVEL`), `ApiExceptionHandler`, `ErrorCode`, `ErrorCatalog`, `ErrorCatalogTest`, `ProfileController`, `ProfileResponse`, `ProfessionalProfileTest`, `ProfileAppServiceTest`, `ProfileControllerTest`, `ProfessionalProfileRepositoryAdapterIT`, `ProfileWriteLockIT`, `ColumnLengthConsistencyIT`, `OpenApiDocumentIT`, `docs/errores.md`; colección (PR 2) | migraciones existentes, `.github/**` |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| `saveAndFlush` cambia el momento del `INSERT` para todas las escrituras | Todas las escrituras ya son de una sola transacción; la suite completa y `ProfileWriteLockIT` deben seguir en verde |
| Filas viejas que chocan al crear el `UNIQUE` | Consulta previa de DevOps (spec §7) |
| Número de migración | Fijo (V8); T-4 comprueba que la base termina en V7 |
| `cameia-web` deja pasar duplicados con tilde que el servidor rechaza | Aviso a Frontend; el 409 se muestra bajo el campo |

## Orden

PR 0 → PR 1 → PR 2, en serie después de CM-54 y antes de CM-67.
