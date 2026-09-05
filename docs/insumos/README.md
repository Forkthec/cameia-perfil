# Entregables de `cameia-perfil` hacia el equipo

Lo que este microservicio devuelve como respuesta a
`03092026_v1_solicitud-insumos-desarrollo-inicio-sprint.md`.

- **Repositorio:** `cameia-perfil`
- **Responsable:** Ana Sofía
- **Tarea Jira de origen:** [CM-102](https://f0rktech.atlassian.net/browse/CM-102) — base técnica
- **Estado general:** `PROPUESTO`

## Qué hay aquí

| Archivo | Responde a |
|---|---|
| [`04092026_v1_respuesta-insumos-cameia-perfil.md`](04092026_v1_respuesta-insumos-cameia-perfil.md) | El formulario de la §10, los entregables comunes `DEV-IN-01` a `DEV-IN-10`, las preguntas específicas de la §7.4 y la declaración de la §4.3 sobre eventos |
| [`04092026_v1_convenciones-docker-cameia.md`](04092026_v1_convenciones-docker-cameia.md) | No lo pedía la solicitud. Es una propuesta que salió de este repositorio al detectar que **no existe convención de contenedores ni matriz de puertos** para el proyecto |

## Paquete mínimo de la §9 — qué está cubierto

| Punto de la §9 | Dónde |
|---|---|
| Formulario de la §10 | `respuesta-insumos` §1 |
| Manifiesto/lockfile real o enlace al commit | `respuesta-insumos` §2 → `pom.xml` en la rama `CM-102-base-tecnica` |
| Comandos ejecutables y resultado observado | `respuesta-insumos` §8, con la salida real pegada |
| Propuesta `.env.example` sin secretos | `.env.example` en `CM-102-base-tecnica` |
| Endpoints / HU / CA / contexto propietario | `respuesta-insumos` §1 y §3 |
| Schemas HTTP/eventos o aplazamiento explícito | `respuesta-insumos` §4 — se **aplazan** los eventos: RabbitMQ es `POSTERIOR` |
| Formato de errores utilizado | RFC 9457 (`application/problem+json`), activo y verificado |
| Estructura de paquetes y convenciones | `respuesta-insumos` §2 y `AGENTS.md` en `CM-102-base-tecnica` |
| Estrategia mínima de pruebas | `respuesta-insumos` §2 y §7 |
| Decisiones/`TBD` con recomendación y responsable | `respuesta-insumos` §5 |
| Riesgos y bloqueos para Jira | `respuesta-insumos` §5 |

## Lo que sigue bloqueado y quién lo decide

| # | Qué | Quién decide |
|---|---|---|
| 1 | **Puerto de `cameia-perfil`** (`DEV-IN-05`). No existe ningún documento del equipo que fije los puertos de los repositorios. 8082 es `PROVISIONAL` | Arquitectura + Gateway |
| 2 | **Perfil no tiene modelo de dominio en el C4**, solo clases JPA, y las notas del C4 prohíben usar el modelo JPA como dominio. **Bloquea CM-16** | Arquitectura + Perfil |
| 3 | El C4 de Perfil **no tiene el campo `estado`** que el backlog exige | Arquitectura + Perfil |
| 4 | Cómo llega la identidad del usuario al microservicio | Arquitectura + Gateway |
| 5 | `API-TBD-05, 06, 07, 09, 18` | Product Owner + arquitectura |
| 6 | **La estrategia de branching, `github.txt` y las reglas de código §5.8 y §14 todavía dicen `CA-<numero>`**, pero el equipo unificó la convención en `CM` ([PR #2](https://github.com/Forkthec/cameia-perfil/pull/2)). Hay que actualizarlas | Arquitectura |

## Nota sobre esta rama

Va aparte de `CM-102-base-tecnica` a propósito: esa rama lleva el código y la norma del
microservicio, y esta lleva lo que el equipo tiene que leer y responder. Cada Pull Request con un
solo propósito.
