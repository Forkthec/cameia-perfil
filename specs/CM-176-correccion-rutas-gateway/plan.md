# Plan — CM-176: Corrección de nombrado de endpoints fuera del predicado del Gateway

## Estrategia

Dos cambios, uno de código y uno de documentación. No hay capa de dominio ni de aplicación
involucrada: el único artefacto que conoce la ruta HTTP es el controlador (§4 de `AGENTS.md`,
"Recibe un HTTP y devuelve JSON → `presentation.controller`").

1. Mover la ruta base de `ProfessionalRoleController` dentro del prefijo que el Gateway enruta.
2. Escribir en `AGENTS.md` la regla que faltaba, para que el error no se repita en Sprint 2 con
   `ResumeController` ni con los endpoints de lectura que consume Entrevista.

## Orden de cambios

1. **`ProfessionalRoleController`** — una línea: el `@RequestMapping`.
2. **`AGENTS.md` §5.4** — nueva subsección con la regla de rutas.
3. **`AGENTS.md` §6** — antipatrón nuevo en la lista que bloquea un PR.
4. **`AGENTS.md` §11** — fila de CM-23 con la ruta nueva, y fila nueva de CM-176.
5. **Verificación** — `docker compose run --rm verify`.

## Detalle por archivo

### 1. `presentation/controller/ProfessionalRoleController.java`

```java
// antes
@RequestMapping("/api/v1/professional-roles")

// después
@RequestMapping("/api/v1/profiles/professional-roles")
```

El método `listAll()` queda con `@GetMapping` sin path, igual que hoy. No cambia la firma, ni
`ProfessionalRoleResponse`, ni el `@Tag`, ni el `@ApiResponse`.

**Por qué esta ruta y no `/api/v1/profiles/catalogs/professional-roles`:** el predicado solo
exige el prefijo `/api/v1/profiles/`; un nivel `catalogs` intermedio agregaría profundidad sin
resolver nada mientras el catálogo de roles sea el único. Si aparece un segundo catálogo, el
agrupamiento se decide entonces.

**Colisión con `GET /api/v1/profiles/{id}`:** no la hay. `ProfileController` declara
`@GetMapping("/{id}")` con `@PathVariable UUID id`; `PathPattern` da precedencia al segmento
literal sobre el de plantilla, y `professional-roles` ni siquiera parsea como `UUID`. Se
confirma con la suite, no con el razonamiento: si `ProfileControllerTest` sigue en verde y el
catálogo responde 200, está resuelto (REQ-4).

### 2. `AGENTS.md` — nueva §5.4, justo después de §5.3 y antes de §6

Texto a insertar:

````markdown
### 5.4 Rutas HTTP — TODAS cuelgan de `/api/v1/profiles`

**La ruta base de este microservicio no la decide el controlador: la decide el Gateway.**
`cameia-gateway` enruta hacia aquí con un único predicado de path:

```yaml
- id: cameia-perfil
  uri: ${CAMEIA_PERFIL_URL}
  predicates:
    - Path=/api/v1/profiles/**
```

Consecuencia directa, y no es negociable desde este repositorio:

> **Todo endpoint de este microservicio empieza por `/api/v1/profiles`.**
> Un endpoint que no cumpla eso compila, arranca, pasa sus pruebas y responde en Swagger UI —
> y aun así es **inalcanzable en el despliegue real**, porque el Gateway responde 404 antes de
> llegar aquí. Es un fallo que ninguna prueba de este repositorio detecta.

| Qué estás exponiendo | Ruta |
|---|---|
| El recurso perfil | `/api/v1/profiles` |
| Un perfil concreto | `/api/v1/profiles/{id}` |
| Un sub-recurso de un perfil | `/api/v1/profiles/{id}/<sub-recurso-en-plural>` |
| Un catálogo o recurso transversal del contexto | `/api/v1/profiles/<recurso-en-plural>` |

```java
// MAL: existe, funciona en local, y el Gateway nunca lo alcanza
@RequestMapping("/api/v1/professional-roles")

// BIEN: dentro del predicado Path=/api/v1/profiles/**
@RequestMapping("/api/v1/profiles/professional-roles")
```

Lo que **no** cambia: el recurso sigue en **inglés y en plural** (§2 y §5.1), el cuerpo JSON
sigue en `snake_case` (§6.1 punto 7) y el `/v1/` sigue en la URL (§9).

**Cuidado con los dos que quedan pendientes.** Las dos lecturas que consume Entrevista (§11)
todavía están escritas con la ruta vieja en los documentos de handoff:
`GET /api/v1/roles/suggestions?q={texto}` **no enruta**. Cuando se implemente en Sprint 2 va
como `GET /api/v1/profiles/role-suggestions?q={texto}` o equivalente dentro del prefijo, y se
avisa a Entrevista y a Gateway en el mismo PR.

**Si un endpoint realmente necesita vivir fuera del prefijo**, no se escribe y ya: se pide el
predicado nuevo al equipo de Gateway y se espera. Es §0 — no asumir, preguntar.
````

### 3. `AGENTS.md` §6 — antipatrón nuevo

Se agrega al final de la lista "Los antipatrones que bloquean un PR en este repositorio", como
punto 13:

```markdown
13. **Exponer un endpoint fuera de `/api/v1/profiles/**`**, que el Gateway no enruta (§5.4).
```

### 4. `AGENTS.md` §11 — tabla de HUs

- Fila CM-23: `GET /api/v1/professional-roles` → `GET /api/v1/profiles/professional-roles`.
- Fila nueva al final de la tabla:

```markdown
| CM-176 | correccion-rutas-gateway | `GET …/profiles/professional-roles` | 🔄 rama CM-176 | Toda ruta dentro de `Path=/api/v1/profiles/**`; ruta vieja eliminada sin alias |
```

## Validación

`docker compose run --rm verify`. Qué tiene que pasar:

- `ProfileControllerTest` sigue en verde → la ruta literal nueva no rompió `GET /{id}` (REQ-4).
- `ArquitecturaTest` en verde → el cambio no movió nada de capa.
- El resto de la suite sin cambios: no se tocó dominio, aplicación ni persistencia.

Comprobación manual complementaria, con el servicio levantado:

```bash
curl -i http://localhost:8082/api/v1/profiles/professional-roles   # espera 200
curl -i http://localhost:8082/api/v1/professional-roles            # espera 404
```

El puerto 8082 es **PROVISIONAL** (§9): si cambió, se usa el real.

## Bitácora y PR

- Bitácora de IA (§8) el mismo día, con la columna K llena.
- Título del PR: `CM-176 | fix(perfil): rutas dentro del predicado del Gateway [IA-ASISTIDO]`.
