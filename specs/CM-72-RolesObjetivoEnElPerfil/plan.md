# Plan — CM-72: catálogo con solo roles activos y Roles Objetivo estables en el perfil

## 1. Enfoque

1. El catálogo filtra `estado = 'ACTIVE'` en sus dos consultas JPQL (`findAllOrderedEs`, `findAllOrderedEn`). `findById` no filtra: CM-70 necesita
   distinguir «inactivo» (422) de «no existe» (404).
2. El agregado sustituye en el mismo índice de la lista y la colección JPA se lee ordenada por `createdAt` y `id`.
3. Pruebas de los dos cambios y del mapeo de `TargetRoleItem`, OpenAPI del catálogo y Postman.

## 2. Archivos

| Modificar | No tocar |
|---|---|
| `infrastructure/persistence/repository/ProfessionalRoleJpaRepository.java`, `infrastructure/persistence/entity/ProfessionalProfileEntity.java` (`@OrderBy` en `targetRoles`), `domain/model/ProfessionalProfile.java` (`updateTargetRole`), `presentation/controller/ProfessionalRoleController.java` (descripción OpenAPI), `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`; pruebas `ProfessionalRoleRepositoryAdapterIT`, `ProfessionalRoleControllerTest`, `ProfessionalProfileTest`, `ProfessionalProfileRepositoryAdapterIT`, `ProfileControllerTest` | migraciones, `ErrorCode`, `ProfessionalRoleResponse` |

## 3. Integración

Base: punta de CM-71. `ProfessionalRoleRepositoryAdapterIT` lo crea CM-70. Sin códigos de error nuevos: `docs/errores.md` no cambia.

## 4. Estimación

≈ 1,5 h de ejecución.
