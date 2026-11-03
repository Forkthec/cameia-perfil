# Plan — CM-73: pruebas de los Roles Objetivo con cobertura real ≥ 90 % y una prueba de punta a punta por CA

## 1. Enfoque

1. Medir en la punta de CM-72 con el comando de la spec §3 y comparar con la línea base de `develop` (spec §1).
2. Cerrar los huecos que quedan: `TargetRole`, ramas de `updateTargetRole`, `ProfessionalRoleAppService` (y borrar el código muerto).
3. `TargetRolesEndToEndIT`: una prueba por CA de HU-2.11 con el servidor real y PostgreSQL real.
4. Medir otra vez y reportar por clase.

## 2. Archivos

| Crear | Modificar | No tocar |
|---|---|---|
| `domain/model/TargetRoleTest.java`, `application/service/ProfessionalRoleAppServiceTest.java`, `presentation/TargetRolesEndToEndIT.java` | `ProfessionalProfileTest`; `ProfessionalRoleAppService`, `domain/port/ProfessionalRoleRepository`, `ProfessionalRoleRepositoryAdapter` (solo borrar `listAll`/`findAll`) | todo lo demás |

## 3. Integración

Base: punta de CM-72. Sin `ErrorCode`, `docs/errores.md`, migraciones ni Postman. `TargetRolesEndToEndIT` limpia sus perfiles por
`firebase_uid like 'uid-e2e-%'` y restaura el estado del rol que desactiva.

## 4. Estimación

≈ 2,5 h de ejecución.
