# Tasks — CM-82: Catálogo de roles profesionales con soporte i18n

## Fase 1 — Migración V4
- [x] Crear `V4__i18n_catalogo_roles.sql`: agregar `nombre_en VARCHAR(255)`, `categoria_en VARCHAR(100)` a `rol_profesional`
- [x] Poblar `nombre_en` y `categoria_en` para los 45 roles del seed

## Fase 2 — Dominio
- [x] `domain/model/ProfessionalRole.java` ya existía (record: `UUID id`, `String nombre`, `String categoria`)
- [x] Agregar `findAllByLang(String lang)` a `domain/port/ProfessionalRoleRepository.java`

## Fase 3 — Infraestructura
- [x] Agregar campos `nombreEn`, `categoriaEn` a `ProfessionalRoleEntity`
- [x] Agregar JPQL `findAllOrderedEs` / `findAllOrderedEn` en `ProfessionalRoleJpaRepository`
- [x] Implementar `findAllByLang` en `ProfessionalRoleRepositoryAdapter`

## Fase 4 — Aplicación
- [x] Agregar `listAllByLang(String lang)` a `ProfessionalRoleAppService`

## Fase 5 — Presentación
- [x] `ProfessionalRoleResponse` ya existía (sin cambios necesarios)
- [x] Actualizar `ProfessionalRoleController`: agregar `?lang=` con default `es`
- [x] Validar `lang` en controller → 400 si valor no es `es` ni `en`
- [x] Documentar con `@ApiResponses` 200 y 400

## Fase 6 — Tests
- [x] Crear `ProfessionalRoleControllerTest.java`
  - [x] `listAll_returnsSpanishByDefault` (sin lang → 200, español)
  - [x] `listAll_returnsEnglishWhenLangEn` (lang=en → 200, inglés)
  - [x] `listAll_returns400ForUnsupportedLang` (lang=fr → 400)

## Fase 7 — Verificación
- [x] `docker compose run --rm verify` — 75 tests en verde (21-sep-2026)
