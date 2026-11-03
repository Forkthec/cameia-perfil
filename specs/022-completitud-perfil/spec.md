# Spec — CM-22: Completitud del perfil

- **Fecha:** 11/09/2026
- **Fuente:** decisiones BE-02, BE-03, BE-08 del documento `02_cambios-backend.md` (Producto)
- **Prioridad:** P0 (BE-02, BE-03) / P1 (BE-08)

## Contexto

Hoy no existe forma de que un candidato marque su perfil como `COMPLETED`. El único camino de estado es `IN_PROGRESS → IN_REVIEW → COMPLETED`, pasando por revisión humana/IA. Producto decide que en el MVP el candidato debe poder completar su propio perfil directamente (`IN_PROGRESS → COMPLETED`) si cumple 5 requisitos mínimos.

Adicionalmente, la regla actual que bloquea borrar el último rol objetivo es demasiado restrictiva: impide limpiar el perfil mientras aún está en construcción.

## Requisitos (notación EARS)

**REQ-1 — Behavior**
Cuando el sistema recibe `POST /api/v1/profiles/{id}/completion` y el perfil cumple los 5 requisitos mínimos, el sistema transiciona el perfil de `IN_PROGRESS` a `COMPLETED` en una sola transacción y retorna 200 con el perfil actualizado.

**REQ-2 — Unwanted behavior**
Si el perfil no cumple alguno de los requisitos mínimos, el sistema retorna 422 con la lista completa de requisitos incumplidos y no cambia el estado.

**REQ-3 — Constraint (5 requisitos mínimos)**
Los requisitos que el sistema evalúa son:
1. `name` no vacío
2. `summary` no vacío
3. Al menos 1 educación
4. Al menos 1 habilidad
5. Al menos 1 rol objetivo

**REQ-4 — Unwanted behavior**
Si el perfil ya está en `COMPLETED`, el sistema retorna 409.

**REQ-5 — Behavior (BE-08)**
Cuando el sistema recibe `DELETE /api/v1/profiles/{id}/target-roles/{roleId}` y el perfil está en `IN_PROGRESS` o `IN_REVIEW`, el sistema permite eliminar el último rol objetivo.

**REQ-6 — Unwanted behavior (BE-08)**
Si el perfil está en `COMPLETED` y solo tiene un rol objetivo, el sistema rechaza la eliminación con 422.

**REQ-7 — Constraint**
El flujo `IN_REVIEW` se conserva en el enum `ProfileStatus` para uso futuro (asistencia IA + revisión humana). No es un paso obligatorio para llegar a `COMPLETED` en el flujo manual.
