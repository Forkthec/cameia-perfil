# Plan — CM-71: eliminar Roles Objetivo con el último rol de un perfil activo en 409

## 1. Enfoque

La regla ya está en el dominio. Se corrige el estado del código en el catálogo (422 → 409), se documenta en OpenAPI y `docs/errores.md`, y se
agregan las pruebas que faltan: servicio (eliminar no tenía ninguna), controlador (409 y 204), concurrencia y Postman.

## 2. Archivos

| Modificar | Crear | No tocar |
|---|---|---|
| `presentation/advice/ErrorCatalog.java`, `presentation/controller/ProfileController.java` (`@ApiResponses` de `DELETE …/target-roles/{roleId}`), `docs/errores.md`, `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`; pruebas `ApiExceptionHandlerTest`, `ProfileControllerTest`, `ProfileAppServiceTest`, `ProfessionalProfileTest`, `infrastructure/persistence/TargetRoleConcurrencyIT.java` | — | `ProfessionalProfile.removeTargetRole` (su regla ya es correcta), migraciones |

## 3. Integración

Base: punta de CM-70. `TargetRoleConcurrencyIT` lo crea CM-70 (T-70.8): aquí se le agrega un método. Postman: la carpeta «HU-2.11 — Roles
objetivo» la crea CM-70; aquí se agregan peticiones al final y se corrige la petición existente del 422 de la carpeta «CM-20 — Roles objetivo».

## 4. Riesgos

Ninguno de datos. El cambio de estado lo consume Frontend: va en el documento de Frontend de `comunicaciones/` junto con los de CM-70.

## 5. Estimación

≈ 2 h de ejecución.
