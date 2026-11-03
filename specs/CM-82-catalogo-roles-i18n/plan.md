# Plan — CM-82: Catálogo de roles profesionales con soporte i18n

## Arquitectura de la solución

La solución sigue el modelo DDD ya establecido en el micro:

```
Migración V4 (BD)
    └─ agrega nombre_en, categoria_en a rol_profesional

domain/model/
    └─ ProfessionalRole.java      (nueva entidad de solo lectura)

domain/port/
    └─ ProfessionalRoleRepository.java  (puerto de salida)

infrastructure/persistence/
    ├─ entity/ProfessionalRoleEntity.java
    └─ repository/ProfessionalRoleRepositoryAdapter.java  (Spring Data JPA)

application/service/
    └─ RoleCatalogService.java    (nuevo servicio de aplicación)

presentation/
    ├─ controller/RoleCatalogController.java   (GET /api/v1/roles)
    └─ dto/ProfessionalRoleResponse.java       (record de salida)
```

No se modifica `ProfileController` ni `ProfileAppService` — este es un contexto de
consulta independiente del perfil.

## Decisiones de diseño

| Decisión | Razonamiento |
|---|---|
| Endpoint sin autenticación | El catálogo es datos de referencia públicos — no contiene datos del usuario |
| `lang` como `@RequestParam` con default `"es"` | Patrón REST estándar para locale de respuesta |
| Validación de `lang` en el controller | Falla rápido con 400 antes de llamar al servicio |
| Columnas en BD (no archivo de propiedades) | Los datos de traducción pertenecen con los datos del catálogo |
| Sin paginación | El catálogo tiene ~45 filas; no justifica la complejidad |
| Orden en query JPQL (`ORDER BY categoria ASC, nombre ASC`) | Determinista, independiente del idioma |

## Fases

1. **Migración V4** — columnas `nombre_en`, `categoria_en` + seed en inglés
2. **Capa de dominio** — `ProfessionalRole` + puerto `ProfessionalRoleRepository`
3. **Capa de infraestructura** — `ProfessionalRoleEntity` + adapter JPA
4. **Capa de aplicación** — `RoleCatalogService`
5. **Capa de presentación** — `ProfessionalRoleResponse` DTO + `RoleCatalogController`
6. **Tests** — `RoleCatalogControllerTest` (CA-2, CA-3, CA-4)
7. **Verificación Docker** — `docker compose run --rm verify` en verde
