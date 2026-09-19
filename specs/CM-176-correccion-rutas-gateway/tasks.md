# Tasks — CM-176: Corrección de nombrado de endpoints fuera del predicado del Gateway

> Regla de oro (§0.2 de `AGENTS.md`): cada `[x]` se marca **en el momento** en que se completa
> la tarea, no al final ni al abrir el PR.

## Fase 1 — Código: la ruta del catálogo (REQ-1, REQ-2, REQ-3)

- [ ] `ProfessionalRoleController` — cambiar `@RequestMapping("/api/v1/professional-roles")`
      por `@RequestMapping("/api/v1/profiles/professional-roles")` (una línea; no se toca el
      `@GetMapping`, ni el `@Tag`, ni el `@ApiResponse`, ni `ProfessionalRoleAppService`)

## Fase 2 — `AGENTS.md`: la regla que faltaba (REQ-5, REQ-6)

- [ ] Insertar la nueva **§5.4 «Rutas HTTP — TODAS cuelgan de `/api/v1/profiles`»** después de
      §5.3 y antes de §6, con el texto del `plan.md`: predicado del Gateway citado, tabla de
      formas de ruta, ejemplo MAL/BIEN, y el aviso sobre las dos lecturas pendientes de Sprint 2
- [ ] Agregar el antipatrón 13 a la lista de §6: exponer un endpoint fuera de
      `/api/v1/profiles/**`
- [ ] §11 — actualizar la fila de CM-23 a `GET /api/v1/profiles/professional-roles`
- [ ] §11 — agregar la fila de CM-176

## Fase 3 — Verificación (REQ-4)

- [ ] `docker compose run --rm verify` en verde, con `ProfileControllerTest` y
      `ArquitecturaTest` incluidos, y la salida real pegada en el PR
- [ ] Comprobación manual con el servicio levantado: `GET /api/v1/profiles/professional-roles`
      responde 200 y `GET /api/v1/professional-roles` responde 404

## Fase 4 — Cierre

- [ ] Bitácora de IA (§8) del día, con la columna K «Cambios humanos» llena
- [ ] PR con la plantilla completa y título
      `CM-176 | fix(perfil): rutas dentro del predicado del Gateway [IA-ASISTIDO]`
- [ ] Avisar en el PR a Frontend y a Gateway: la ruta vieja desaparece sin alias

---

## Trazabilidad tarea → requisito

| Tarea | Requisito |
|---|---|
| Fase 1 | REQ-1, REQ-2, REQ-3 |
| Fase 2 · §5.4 y antipatrón 13 | REQ-5 |
| Fase 2 · §11 | REQ-6 |
| Fase 3 | REQ-1, REQ-2, REQ-4 |

## Lo que esta HU NO hace

- No toca el Gateway ni su predicado.
- No corrige las rutas citadas en `specs/023-catalogo-roles-profesionales/`.
- No agrega `ProfessionalRoleControllerTest` (el controlador sigue sin test de contrato).
- No toca la colección Postman ni el `README.md`.
