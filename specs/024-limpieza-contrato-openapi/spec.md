# Spec — CM-24: Limpieza de contrato OpenAPI

- **Fecha:** 11/09/2026
- **Fuente:** decisiones BE-09, BE-10, BE-14 del documento `02_cambios-backend.md` (Producto)
- **Prioridad:** P0 (BE-09) / P1 (BE-10, BE-14)
- **Nota:** debe implementarse DESPUÉS de CM-21, CM-22 y CM-23

## Contexto

El contrato OpenAPI publicado tiene tres problemas:
1. Los POST que crean recursos responden 200 en el spec pero devuelven 201 en la realidad.
2. Campos fuera del alcance del MVP (`salary-expectation`, `seniority`, `headline`) aparecen en el spec.
3. Campos obligatorios sin `@NotNull` producen `500` (NPE) en vez de `422`.

## Requisitos (notación EARS)

**REQ-1 — Behavior**
El sistema declara `201` como código de respuesta en todos los endpoints `POST` que crean recursos.

**REQ-2 — Behavior**
El sistema declara `application/json` como tipo de contenido en todos los responses de la API.

**REQ-3 — Behavior**
El sistema publica el schema `ProblemDetail` en el OpenAPI para todos los errores 4xx.

**REQ-4 — Constraint**
El endpoint `PATCH /salary-expectation` no aparece en el contrato OpenAPI publicado. El endpoint permanece en el código (dormido) para no romper datos existentes.

**REQ-5 — Constraint**
Los campos `seniority` (eliminado en CM-21) y `headline` (nunca implementado) no aparecen en ningún schema del contrato.

**REQ-6 — Unwanted behavior (BE-10)**
Si un campo obligatorio (`employmentStatus`, `level`, `provenance`) llega nulo al sistema, el sistema retorna 400 con `ProblemDetail` en vez de 500.
