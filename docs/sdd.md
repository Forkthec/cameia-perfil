# Spec Driven Development — cameia-perfil

> Metodología adoptada el 07/09/2026, basada en el curso de mouredev
> (github.com/mouredev/hello-sdd). Aplica a **toda Historia de Usuario nueva**
> que se implemente en este microservicio.
>
> Esta guía vive en `docs/sdd.md`. Si se mejora, se actualiza aquí.

---

## Qué es SDD

**Spec Driven Development** es una metodología de trabajo con IA donde la
especificación (`spec`) se escribe **antes** del código. Sin spec no hay
implementación: la IA genera código a partir del spec, no a partir de
instrucciones verbales vagas.

El objetivo es producir código que el equipo pueda defender, mantener y auditar,
con trazabilidad completa entre el requisito (spec), el diseño (plan), las tareas
(tasks) y el código (implementación).

---

## Variante adoptada: Spec First + Spec Anchored

| Variante | Qué significa |
|---|---|
| **Spec First** | El spec se escribe antes de generar código. La IA no escribe una línea sin spec aprobado. |
| **Spec Anchored** | El spec y el código se mantienen sincronizados durante toda la vida de la HU. Si el código cambia, el spec se actualiza. |

La tercera variante (*Spec as Source*, donde el spec es el programa en sí) no
aplica aquí: el código Java es la fuente de verdad en ejecución.

---

## El ciclo SDD — 7 fases

```text
Constitution (una vez)
      │
      ▼
 1. Spec ──────────────────── notación EARS, qué + por qué
      │
      ▼
 2. Clarification ──────────── preguntas antes de diseñar
      │
      ▼
 3. Plan ───────────────────── diseño técnico, no código
      │
      ▼
 4. Tasks ──────────────────── checkboxes 20-30 min, trazados al spec
      │
      ▼
 5. Implementation ─────────── una tarea a la vez, marcar ✅ antes de la siguiente
      │
      ▼
 6. Validation ─────────────── cada requisito del spec verificado contra el código
      │
      └──────────────────────── (loop a Spec si hay cambios)
```

### Fase 0 — Constitution (una sola vez)

`docs/constitution.md` define los principios inmutables del proyecto:
arquitectura, idioma del código, convenciones de nombres, qué tecnologías se
usan y cuáles están prohibidas. Se escribe una vez y no cambia sin decisión de
equipo. En este microservicio esa función la cumple `AGENTS.md` (ver §Relación
con AGENTS.md más abajo).

### Fase 1 — Spec

El archivo `specs/NNN-nombre/spec.md` describe **qué** hace el sistema y
**por qué**, sin decir cómo. Contiene:

- Contexto: qué HU es, qué problema resuelve
- Requisitos funcionales en notación EARS
- Requisitos no funcionales relevantes (latencia, seguridad, invariantes)
- Criterios de aceptación verificables

**Regla: ningún `plan.md` se escribe sin `spec.md` aprobado.**

### Fase 2 — Clarification

Antes de diseñar, se repasa el spec buscando:

- Términos ambiguos que dos personas interpretarían distinto
- Requisitos que se contradicen
- Casos borde no cubiertos
- TBDs del equipo que bloquean la implementación

Si hay ambigüedades, se documentan como preguntas en el mismo `spec.md` y se
resuelven antes de continuar. Un TBD nunca se cierra escribiendo código (antipatrón
9 de AGENTS.md §6).

### Fase 3 — Plan

`specs/NNN-nombre/plan.md` describe **cómo** se implementará el spec:

- Clases y paquetes afectados (con referencia a AGENTS.md §3)
- Cambios de modelo de dominio (invariantes nuevas, métodos)
- Cambios de persistencia (migraciones Flyway, nuevas columnas)
- Cambios de API (nuevos endpoints, contratos de request/response)
- Decisiones técnicas y alternativas descartadas
- Riesgos e impacto en otras HUs

**Regla: el plan no contiene código Java. Contiene decisiones.**

### Fase 4 — Tasks

`specs/NNN-nombre/tasks.md` lista el trabajo en orden de ejecución como
checkboxes de GitHub Markdown, donde cada tarea:

- Es completable en 20-30 minutos
- Referencia el requisito del spec que implementa (`REQ-N`)
- Es verificable (hay una prueba o un resultado observable)

Ejemplo:

```markdown
## Tasks — CM-21 agregar-certificaciones

- [ ] REQ-1 · Crear `Certification` como record en `domain/model`
- [ ] REQ-1 · Agregar `addCertification()` al agregado `ProfessionalProfile`
- [ ] REQ-1 · Prueba unitaria positiva y negativa en `ProfessionalProfileTest`
- [ ] REQ-2 · Migración Flyway `V3__add_certification.sql`
- [ ] REQ-2 · Campo `@OneToMany` en `ProfessionalProfileEntity`
- [ ] REQ-2 · Mapeo `toDomain`/`toEntity` en el adaptador
- [ ] REQ-3 · `AddCertificationCommand` en `application/command`
- [ ] REQ-3 · Método `addCertification()` en `ProfileAppService`
- [ ] REQ-3 · `POST /api/v1/profiles/{id}/certifications` en `ProfileController`
- [ ] REQ-3 · Prueba de controlador en `ProfileControllerTest`
- [ ] Validación — `docker compose run --rm verify` en verde
```

**Regla: se marca `[x]` en el tasks.md antes de pasar a la siguiente tarea.**

### Fase 5 — Implementation

Se implementa una tarea a la vez, siguiendo el orden del `tasks.md`. Cada tarea
que toca código Java sigue las reglas de AGENTS.md (DDD, Clean Code, pruebas).

**Regla: no se salta al siguiente checkbox hasta que el anterior compile y sus
pruebas estén en verde.**

### Fase 6 — Validation

Al terminar todas las tareas, se verifica el spec completo:

1. Por cada requisito EARS en `spec.md`, hay al menos una prueba que lo cubre
2. `docker compose run --rm verify` está en verde
3. Los criterios de aceptación del spec son verificables con la prueba o con una
   llamada al endpoint

Si un requisito no está cubierto, se regresa a Implementation. Si el alcance
cambió durante la implementación, se actualiza el spec (Spec Anchored).

---

## Notación EARS

**EARS** (Easy Approach to Requirements Syntax) produce requisitos no ambiguos
usando patrones fijos. Todos los requisitos en `spec.md` van en EARS.

| Patrón | Cuándo usarlo | Forma |
|---|---|---|
| Comportamiento | Regla siempre activa | `El sistema [verbo] [objeto]` |
| Evento | Acción que dispara una respuesta | `Cuando [actor] [acción], el sistema [respuesta]` |
| Estado | Regla que aplica en un estado particular | `Mientras [estado activo], el sistema [comportamiento]` |
| No deseado | Manejo de error o violación de invariante | `Si [condición no deseada], el sistema [manejo]` |
| Opcional | Comportamiento que depende de una opción | `Donde se proporcione [opción], el sistema [comportamiento]` |

### Ejemplos concretos para este microservicio

```
El sistema almacenará los roles objetivo con procedencia y estado de revisión.

Cuando el usuario agrega un rol objetivo, el sistema verifica que el perfil
no tenga más de 5 roles y rechaza la operación si el límite está alcanzado.

Mientras el perfil esté en estado IN_REVIEW, el sistema no permitirá
modificar los campos de texto libre.

Si el usuario intenta eliminar el único rol objetivo del perfil, el sistema
lanza LastTargetRoleException sin modificar el perfil.

Donde se proporcione una seniority, el sistema la valida contra el enum
Seniority antes de persistir el rol.
```

---

## Estructura de carpetas

```
cameia-perfil/
├── docs/
│   ├── sdd.md                      ← esta guía
│   ├── constitution.md             ← si se crea; hoy lo cubre AGENTS.md
│   └── ...otros docs existentes...
└── specs/
    ├── 016-crear-perfil/
    │   ├── spec.md
    │   ├── plan.md
    │   └── tasks.md
    ├── 017-actualizar-info-perfil/
    │   └── ...
    └── NNN-nombre-hu/
        ├── spec.md
        ├── plan.md
        └── tasks.md
```

Los números `NNN` coinciden con el número de la HU de Jira (CM-NNN).

---

## Plantillas

### spec.md

```markdown
# Spec — CM-NNN [nombre de la HU]

## Contexto
[Qué HU es, qué problema resuelve, referencia a la tabla de §11 de AGENTS.md]

## Requisitos funcionales

REQ-1: El sistema ...
REQ-2: Cuando [actor] ..., el sistema ...
REQ-3: Si [condición], el sistema ...

## Requisitos no funcionales

REQ-NF-1: [Latencia, seguridad, invariante de dominio, etc.]

## Criterios de aceptación

- [ ] [Verificable con prueba o llamada a endpoint]
- [ ] [...]

## Preguntas abiertas (Clarification)

- [ ] [Pregunta antes de diseñar]
```

### plan.md

```markdown
# Plan — CM-NNN [nombre de la HU]

## Capas afectadas
- domain/model: [qué cambia]
- application/command: [qué cambia]
- application/service: [qué cambia]
- infrastructure/persistence: [qué cambia]
- presentation/controller: [qué cambia]
- presentation/dto: [qué cambia]

## Nuevas clases / interfaces
| Clase | Paquete | Descripción |
|---|---|---|

## Cambios de persistencia
- Migración: `VNNN__descripcion.sql`
- Columnas nuevas o modificadas: [lista]

## Decisiones técnicas
- [Decisión] — Alternativa descartada: [razón]

## Riesgos e impacto
- [CM-NN que puede verse afectado y por qué]
```

### tasks.md

```markdown
## Tasks — CM-NNN [nombre de la HU]

- [ ] REQ-N · [Tarea de dominio]
- [ ] REQ-N · [Prueba de dominio]
- [ ] REQ-N · [Migración]
- [ ] REQ-N · [Adaptador de persistencia]
- [ ] REQ-N · [Command / AppService]
- [ ] REQ-N · [Controller / DTO]
- [ ] REQ-N · [Prueba de servicio]
- [ ] REQ-N · [Prueba de controlador]
- [ ] Validación — `docker compose run --rm verify` en verde
- [ ] Bitácora IA actualizada
```

---

## Relación con AGENTS.md

`AGENTS.md` actúa como la **Constitution** del proyecto: define los principios
inmutables de arquitectura, idioma, nombrado y pruebas. El ciclo SDD **no
reemplaza** AGENTS.md sino que lo usa como marco:

- El `plan.md` de cada HU debe respetar la estructura de §3 y §4 de AGENTS.md
- Las clases nuevas que aparezcan en el plan deben estar en la tabla de §3.1
  o justificar su ausencia
- Las tareas de implementation deben seguir §5 (nombres), §6 (Clean Code) y §7
  (pruebas)

---

## Aplicación retroactiva a CM-16..CM-20

Las HUs del Sprint 1 (CM-16 a CM-20) se implementaron sin spec formal porque el
SDD se adoptó después. Para mantener la trazabilidad mínima:

- Los specs retroactivos se pueden crear como documentación descriptiva
  (no cambian el código)
- A partir de CM-21 y cualquier HU nueva, el ciclo SDD es obligatorio desde
  la Fase 1
- En la Bitácora IA, la columna E (HU/Tarea) hace referencia al spec
  cuando existe: `CM-21 spec.md REQ-1`

---

## Checklist de entrada a una HU nueva

```
Antes de crear la rama CM-NNN-...:
- [ ] spec.md escrito y aclarado
- [ ] plan.md aprobado
- [ ] tasks.md con checkboxes trazados al spec
- [ ] Ningún TBD del equipo sin resolver en el spec
- [ ] AGENTS.md §11 tiene la HU registrada con sus endpoints y reglas
```
