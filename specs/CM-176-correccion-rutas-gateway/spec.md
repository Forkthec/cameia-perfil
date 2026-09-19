# Spec — CM-176: Corrección de nombrado de endpoints fuera del predicado del Gateway

**Tarea Jira:** CM-176
**Tipo:** Bug de integración (enrutamiento) + regla de documentación
**Fecha spec:** 2026-09-19
**Autor:** Juan David Vela Coronado
**Fuente:** Predicado de ruteo de `cameia-gateway` (`application.yml`, ruta `id: cameia-perfil`)

---

## Contexto

El Gateway enruta hacia este microservicio con un único predicado de path:

```yaml
- id: cameia-perfil
  uri: ${CAMEIA_PERFIL_URL}
  predicates:
    - Path=/api/v1/profiles/**
```

Es decir: **todo lo que no empiece por `/api/v1/profiles/` es inalcanzable a través del
Gateway**, aunque exista y funcione al llamar al microservicio directamente.

Hoy hay exactamente un endpoint en esa situación:

| Método | Ruta actual | Clase | ¿Enruta el Gateway? |
|---|---|---|---|
| `GET` | `/api/v1/professional-roles` | `ProfessionalRoleController` (CM-23) | ❌ No |

Los 16 endpoints de `ProfileController` sí cuelgan de `/api/v1/profiles` y no se tocan.

El síntoma no es un error del microservicio: el Gateway no encuentra ruta y
responde 404 antes de llegar aquí, así que el catálogo de roles profesionales que CM-23 dejó
implementado y probado es inutilizable desde el Frontend.

**Causa de fondo:** no existe en `AGENTS.md` ninguna regla que diga que la ruta base del
microservicio está fijada por el predicado del Gateway. CM-23 nombró su endpoint siguiendo la
convención REST razonable (`/api/v1/<recurso-plural>`) sin saber que el prefijo estaba acotado.
Por eso esta HU corrige la ruta **y** escribe la regla, para que el próximo controlador no
repita el error.

---

## Requisitos (formato EARS)

**REQ-1 — Catálogo alcanzable a través del Gateway:**
Cuando un cliente hace `GET /api/v1/profiles/professional-roles`, el sistema responde
`200 OK` con el mismo cuerpo que hoy devuelve `GET /api/v1/professional-roles`: la lista
completa del catálogo de roles profesionales TI.

**REQ-2 — La ruta vieja deja de existir:**
Cuando un cliente hace `GET /api/v1/professional-roles`, el sistema responde `404 Not Found`.
No se mantiene alias ni redirección: la ruta vieja nunca fue alcanzable por el Gateway, así
que no hay consumidor productivo que romper.

**REQ-3 — Sin cambio de contrato de datos:**
El sistema mantiene sin cambios el método HTTP, el código de respuesta, el esquema de
`ProfessionalRoleResponse`, el tag de OpenAPI y la lógica de `ProfessionalRoleAppService`.
Cambia la ruta, nada más.

**REQ-4 — La ruta literal no colisiona con `GET /{id}`:**
Mientras coexistan `GET /api/v1/profiles/{id}` (UUID, `ProfileController`) y
`GET /api/v1/profiles/professional-roles` (literal, `ProfessionalRoleController`), el sistema
resuelve la petición literal hacia el catálogo y no hacia la lectura de perfil.

> Fundamento: Spring resuelve con `PathPattern`, que da precedencia al patrón literal sobre el
> de plantilla. Además `GET /{id}` está tipado a `UUID` y `professional-roles` no lo es.
> Esto se verifica ejecutando la suite, no se da por supuesto.

**REQ-5 — La regla queda escrita:**
Donde `AGENTS.md` describe cómo se nombran los artefactos del microservicio, el documento
establece que toda ruta HTTP expuesta por este servicio cuelga de `/api/v1/profiles`, cita el
predicado del Gateway como origen de la restricción, y lo registra como antipatrón que bloquea
un PR.

**REQ-6 — La tabla de HUs refleja la ruta vigente:**
Donde `AGENTS.md` §11 lista los endpoints de cada HU, la fila de CM-23 muestra la ruta nueva.

---

## Archivos que cambian

| Archivo | Cambio |
|---|---|
| `presentation/controller/ProfessionalRoleController.java` | `@RequestMapping("/api/v1/professional-roles")` → `@RequestMapping("/api/v1/profiles/professional-roles")` |
| `AGENTS.md` | Nueva §5.4 con la regla de rutas; antipatrón nuevo en §6; fila CM-23 de §11 actualizada; fila CM-176 agregada a §11 |

Ningún otro archivo de `src/` contiene el string `professional-roles` (verificado con `grep`
el 19/09/2026): no hay test de contrato de ese controlador, ni referencias en `README.md`,
`application.yml` ni en la colección Postman.

---

## Fuera de alcance

- **No se toca el Gateway.** El predicado `Path=/api/v1/profiles/**` se acepta como dado; que
  sea la restricción correcta es decisión de arquitectura, no de este repositorio.
- **No se corrigen** los archivos de `specs/023-catalogo-roles-profesionales/` que citan la
  ruta vieja: un spec es el registro de lo que se decidió aquel día y se corrige por el
  histórico de esta HU, no reescribiéndolo.
- **No se agrega** prueba `@WebMvcTest` para `ProfessionalRoleController`. Sigue sin tener test
  de contrato; queda como deuda declarada, no como parte de esta HU.
- **No se cambia** ninguna otra ruta, ni el versionado `/api/v1/` en la URL (§9 de `AGENTS.md`:
  decisión de arquitectura).
- **No se agrega** lógica de negocio, validación ni paginación al catálogo.

---

## Riesgo

Bajo. El único consumidor posible de la ruta vieja sería alguien llamando al microservicio sin
pasar por el Gateway (entorno local o Swagger UI). Se avisa en el PR para que Frontend apunte a
la ruta nueva.
