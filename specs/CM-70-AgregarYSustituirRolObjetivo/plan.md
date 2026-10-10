# Plan — CM-70: agregar y sustituir Roles Objetivo solo con roles activos

## 1. Enfoque

1. **Datos primero (PR 70-1).** La regla «solo roles activos» necesita que el catálogo tenga estado. Se agrega `rol_profesional.estado` y las
   restricciones de `rol_objetivo` en `V5_1`, el estado sube al dominio (`ProfessionalRole.active`) y la violación de unicidad se traduce a 409.
2. **Regla y contrato (PR 70-2).** `ProfessionalRole.requireSelectable()` rechaza el rol inactivo con `InvalidFieldsException`; el servicio la usa al
   agregar y al sustituir; el máximo pasa a 409; `provenance` sale del contrato de entrada y se guarda `MANUAL`; OpenAPI, `docs/errores.md`, pruebas y
   Postman.

Patrones: objeto de valor del dominio con su regla (`ProfessionalRole`), puerto y adaptador (existentes). Nada nuevo fuera de eso.

## 2. Archivos

| PR | Crear | Modificar | No tocar |
|---|---|---|---|
| 70-1 | `src/main/resources/db/migration/V5_1__estado_catalogo_y_restricciones_rol_objetivo.sql`; pruebas `infrastructure/persistence/TargetRoleSchemaIT.java`, `infrastructure/persistence/ProfessionalRoleRepositoryAdapterIT.java`, `domain/model/ProfessionalRoleTest.java` | `domain/model/ProfessionalRole.java`, `infrastructure/persistence/entity/ProfessionalRoleEntity.java`, `infrastructure/persistence/repository/ProfessionalRoleRepositoryAdapter.java`, `infrastructure/persistence/repository/ProfessionalProfileRepositoryAdapter.java` (`save`), `infrastructure/persistence/ProfessionalProfileRepositoryAdapterIT.java`, pruebas que construyen `ProfessionalRole` (`ProfileAppServiceTest`, `ProfessionalRoleControllerTest`) | migraciones `V1` a `V5`, `pom.xml`, `.github/**` |
| 70-2 | `infrastructure/persistence/TargetRoleConcurrencyIT.java` | `domain/exception/ErrorCode.java`, `presentation/advice/ErrorCatalog.java`, `application/service/ProfileAppService.java`, `application/command/AddTargetRoleCommand.java`, `presentation/dto/AddTargetRoleRequest.java`, `presentation/controller/ProfileController.java` (OpenAPI de `POST` y `PATCH …/target-roles`), `docs/errores.md`, `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`; pruebas `ErrorCatalogTest`, `ApiExceptionHandlerTest`, `ProfileAppServiceTest`, `ProfileControllerTest`, `ProfessionalProfileTest` | `ProfessionalProfile` (sus reglas de máximo y duplicado ya son correctas), `DELETE …/target-roles` (CM-71) |

## 3. Integración con la cadena

- Base: punta de `CM-274-base-escritura-perfil`. Trae `loadForUserForUpdate`, `findByIdForUpdate`, `ProfileUpdateInProgressException`,
  `ErrorCodeDocumentationTest`, `FieldValues` (antes `CommandValues`) y la sección «Códigos retirados» de `docs/errores.md`.
- Migración `V5_1` (D70-7). Antes de crearla: `ls src/main/resources/db/migration` debe mostrar `V1` a `V5` y ningún `V5_1` ni `V6`.
- `ErrorCode`, `ErrorCatalog`, `docs/errores.md`, colección de Postman: esta CM agrega al final de cada lista; CM-71, CM-72, CM-73 y CM-271 siguen.
- `ProfessionalProfileRepositoryAdapter.save` pasa a `saveAndFlush`; CM-274 PR 2 en adelante no cambia ese método.

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| Filas de staging que violan `NOT NULL` o `UNIQUE` | Consultas previas de la spec §6 (Paula) |
| `saveAndFlush` adelanta errores que antes salían al confirmar | Mismo efecto final (se deshace todo); `clean verify` completo en la capa |
| Hibernate inserta antes de borrar al guardar el agregado | Ninguna operación de una petición quita y agrega el mismo rol; cubierto por `TargetRoleConcurrencyIT` |

## 5. Matriz de pruebas (resumen; detalle en `tasks.md`)

Dominio: `ProfessionalRoleTest` (activo, inactivo), `ProfessionalProfileTest` (4→5, lleno y repetido, sustituir por sí mismo). Aplicación:
`ProfileAppServiceTest` (inactivo al agregar y al sustituir, antes del máximo, `MANUAL`, ajeno ×3, no existe). Web: `ProfileControllerTest`
(409 del máximo, 422 con `errors[0]`, `provenance` ignorado, UUID mal escrito). Persistencia: `TargetRoleSchemaIT`, `ProfessionalRoleRepositoryAdapterIT`,
`ProfessionalProfileRepositoryAdapterIT`, `TargetRoleConcurrencyIT`. Arquitectura: `ArquitecturaTest`. API: Newman.

PR: 70-0 (solo documentos, ≈ 645 líneas), 70-1 (datos, ≈ 420) y 70-2 (reglas y contrato, ≈ 650).

## 6. Estimación

PR 70-1 ≈ 2,5 h; PR 70-2 ≈ 3 h. Total ≈ 5,5 h de ejecución (la subtarea de Jira tiene otra estimación; no se cambia, se informa a Vela).
