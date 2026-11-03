# Spec — CM-21: Eliminar seniority del contrato MVP

- **Fecha:** 11/09/2026
- **Fuente:** decisiones BE-06 y BE-11 del documento `02_cambios-backend.md` (Producto)
- **Prioridad:** P0 (BE-06) / P1 (BE-11)

## Contexto

El campo `seniority` existe hoy en dos entidades del perfil: `TargetRole` y `WorkExperience`.
Producto decidió retirarlo del alcance del MVP porque:
- En `TargetRole`: la detección de duplicados migrará al `rol_profesional_id` del catálogo (CM-23). Seniority era parte de esa clave duplicada.
- En `WorkExperience`: seniority queda fuera del alcance funcional del MVP (decisión D-BE-11).

Consecuencia: el enum `Seniority` queda **sin uso** en el contrato del MVP. El enum permanece en el código pero no se expone ni en la API ni en las pantallas.

## Requisitos (notación EARS)

**REQ-1 — Behavior**
Cuando el sistema recibe `POST /api/v1/profiles/{id}/target-roles`, el sistema agrega el rol sin campo `seniority` y retorna 201.

**REQ-2 — Behavior**
Cuando el sistema recibe `PATCH /api/v1/profiles/{id}/target-roles/{roleId}`, el sistema actualiza el rol sin campo `seniority` y retorna 200.

**REQ-3 — Behavior**
Cuando el sistema recibe `POST /api/v1/profiles/{id}/work-experiences`, el sistema registra la experiencia sin campo `seniority` y retorna 201.

**REQ-4 — Unwanted behavior**
Si el body de cualquiera de los endpoints anteriores incluye el campo `seniority`, el sistema lo ignora (campo desconocido, no produce error).

**REQ-5 — Behavior**
El sistema detecta duplicados de `TargetRole` por `title` únicamente (transitorio hasta CM-23 que introduce `rol_profesional_id`).

**REQ-6 — Constraint**
El campo `seniority` no aparece en ningún response de la API, ni en Swagger/OpenAPI, ni en la colección de Postman.
