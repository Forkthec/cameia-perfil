# Plan — CM-54 (cameia-perfil)

Base: `origin/develop` con **CM-274 completo** fusionado (orden de Perfil: CM-279 P1 → CM-279 P2 → CM-274 → **CM-54** → CM-66 → CM-67; spec §19). CM-274 PR 1 aporta `SingleLineText`, `@CodePointSize`, `@DomainRule`, `FieldValues`, el acumulador, `loadForUserForUpdate` y `ProfileWriteLockIT`; su PR 2, `ColumnLengthConsistencyIT` y `WorkHistoryEndToEndIT` (modelo de la prueba E2E). Estado: pendiente de aprobación de Paula.

| PR | Rama | Base | Tarjetas | Líneas | Horas | Requisito previo |
|---|---|---|---|---|---|---|
| 0 | `CM-54-informacion-general-resumen-spec` | `develop` | — (spec, plan y tarjetas) | ≈ 600, solo documentos | 0,2 | Aprobación de Paula |
| 1 | `CM-54-informacion-general-resumen` | `develop` con CM-274 y el PR 0 | T-0 a T-6 | ≈ 580 | 3 | — |
| 2 | `CM-54-postman-informacion-general` | PR 1 | T-7 | ≈ 480 | 1,5 | — |

## Diseño

1. **Borde.** `UpdateProfileInfoRequest` normaliza en su constructor compacto (`name`, `summary` y `preferredModality` con `SingleLineText.normalize`; en `summary` un `""` se conserva para que el servicio lo lea como «borrar»). Restricciones: `name` `@Size(min = 1) @CodePointSize(max = 255) @DomainRule(Rule.PROFILE_NAME)`; `summary` `@CodePointSize(max = 2000) @DomainRule(Rule.SUMMARY)`; `preferredModality` `@Size(min = 1) @DomainRule(Rule.PREFERRED_MODALITY)`, así `""` se informa como valor inválido (D6). Sin `provenance`.
2. **Dominio.** `ProfileName.of(String)` y `ProfessionalSummary.of(String)` normalizan y validan (la misma regla del borde, como invariante); el constructor canónico del `record` solo exige no nulo, para reconstruir desde la base sin volver a validar. `ProfessionalProfile.updateSummary(null)` conserva la regla `SUMMARY_NOT_ALLOWED`.
3. **Servicio.** `updateProfileInfo` usa `loadForUserForUpdate`; arma nombre, resumen y modalidad con el `InvalidFieldsException.Collector` (privados `readSummary` y `collect`) y deja de llamar `updateProvenance`.
4. **Respuesta.** `ProfileResponse.summaryProvenance` = `"MANUAL"` si hay resumen, `null` si no.

## Archivos

| Crear | Modificar | No tocar |
|---|---|---|
| `src/test/.../presentation/GeneralInfoEndToEndIT.java` | `UpdateProfileInfoRequest`, `UpdateProfileInfoCommand`, `DomainRule` (tres reglas al final de `Rule`), `ProfileName`, `ProfessionalSummary`, `ProfileAppService`, `ProfessionalProfile` (borrar `updateProvenance` si queda sin uso), `ProfileResponse`, `ProfileController` (OpenAPI), `ErrorCode`, `ErrorCatalog`, `ErrorCatalogTest`, `ProfileValueRulesTest`, `ProfessionalProfileTest`, `ProfileAppServiceTest`, `ProfileControllerTest`, `ProfileWriteLockIT`, `ColumnLengthConsistencyIT`, `OpenApiDocumentIT`, `docs/errores.md`, colección de Postman | migraciones, `.github/**`, `pom.xml`, `ProfessionalProfileMapping` (sigue leyendo con el constructor canónico) |

## Riesgos

| Riesgo | Mitigación |
|---|---|
| `cameia-web` envía `provenance` en el `PATCH` y espera que cambie | No lo espera (la v4 no lo usa); el aviso a Frontend va con el contrato aprobado |
| `ProfileValueRulesTest` prueba hoy que `new ProfileName(" ")` lanza | T-2 la cambia a `ProfileName.of(" ")`; el constructor canónico ya no valida |

## Orden

PR 0 → PR 1 → PR 2, en serie después de CM-274 y antes de CM-66. Sin migración y sin preguntas abiertas.
