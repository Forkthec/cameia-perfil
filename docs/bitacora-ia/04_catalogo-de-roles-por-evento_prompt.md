# Entrada 04 · Cómo llega el catálogo de roles de Perfil a Entrevista

- **Fecha:** 2026-10-22 · **Herramienta:** Claude Code (Sonnet 5.5) · **Integrante:** Paula Andrea Muñoz Delgado
- **Salida asociada:** la spec de CM-209, que vive en el repositorio `cameia-entrevista` (`specs/CM-209-ReplicaPerfilProfesional/spec.md`, sección 25 y decisiones D-RPL-08 a D-RPL-12)

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y detallas el criterio de catálogo de roles de CM-209 bajo el estándar de la dueña del Backend.

Contexto: CM-207 valida el rol objetivo de una sesión contra todos los roles activos del catálogo (CA-4.2.14), pero el catálogo solo vive
en Perfil y Entrevista no puede llamar a Perfil (HT-03). Perfil ya publica `perfil.profesional-actualizado` con tabla de salida y relevo.
El catálogo tiene 46 roles, solo cambia con una migración y un despliegue, y Perfil nunca borra un rol: lo desactiva. El equipo decidió el
22-oct absorberlo en CM-209 y no crear otra tarea.

Tarea: Decide cómo llega el catálogo a Entrevista (una instantánea completa o un mensaje por cambio), cuándo lo publica Perfil (en cada
arranque, solo si cambió, o con una tarea programada), qué pasa con los roles que una instantánea no trae y cómo se numera la migración
de Perfil frente a la tarea de alinear el estándar.

Condiciones: sin dependencias nuevas; el relevo y la tabla de salida existentes publican el tipo nuevo sin cambios; ningún registro lleva
nombres de rol; cada decisión con sus alternativas descartadas y cada caso con su prueba y valores literales.

Formato: tabla de decisiones con quién decidió, el porqué y lo descartado; requisitos EARS; contrato del evento con ejemplo válido; tabla
de casos borde.
```
