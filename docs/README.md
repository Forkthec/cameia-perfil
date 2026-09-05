# Documentación de `cameia-perfil`

Índice de lo que hay en esta carpeta y para qué sirve cada archivo.

## Para escribir código en este repositorio

| Archivo | Qué es |
|---|---|
| [`../AGENTS.md`](../AGENTS.md) | **La norma de este repositorio.** Estructura, nombres por capa, el patrón de persistencia de tres piezas, Clean Code, prácticas de Spring Boot (§6.1), los 12 antipatrones que bloquean un PR y las decisiones abiertas (§9). Se lee completo antes de escribir la primera línea |
| [`03092026_v1_reglas-codigo-backend-cameia.md`](03092026_v1_reglas-codigo-backend-cameia.md) | La norma **del equipo**, común a los seis microservicios. `AGENTS.md` deriva de ella y se queda con lo que aplica a Perfil |
| [`05092026_v1_handoff-implementacion-hu.md`](05092026_v1_handoff-implementacion-hu.md) | Estado del repositorio, lo que hay que resolver antes de CM-16 y las cinco Historias de Usuario con sus reglas duras |

> **Cuál manda si se contradicen.** Manda el documento del equipo. Además, cada afirmación suya
> está marcada `CONFIRMADO`, `PROPUESTO` o `TBD`: cuando dos secciones chocan, gana la marcada
> `CONFIRMADO`, y un `PROPUESTO` nunca cierra un `TBD`.
>
> `03092026_v1_reglas-codigo-backend-cameia.md` es una **copia** del documento del equipo
> (md5 idéntico al original en el momento de copiarlo). La fuente sigue siendo la del equipo: si
> ellos publican una versión nueva, hay que traerla, no editarla aquí.

## Entorno y operación

| Archivo | Qué es |
|---|---|
| [`DOCKER.md`](DOCKER.md) | Convenciones de contenedores de este repositorio, puertos y decisiones abiertas |
| [`04092026_v1_convenciones-docker-cameia.md`](04092026_v1_convenciones-docker-cameia.md) | Propuesta de convenciones de contenedores para **los ocho repositorios**, con la matriz de puertos. Estado `PROPUESTO`: hay que llevarla a la reunión |

## Respuestas al equipo

| Archivo | Qué es |
|---|---|
| [`04092026_v1_respuesta-insumos-cameia-perfil.md`](04092026_v1_respuesta-insumos-cameia-perfil.md) | Respuesta a `DEV-IN-01..10` desde este microservicio |

## Lo que NO está aquí, a propósito

- **Los diagramas C1–C4, el diagrama de paquetes, el backlog y el glosario.** Son del proyecto
  entero, no de este microservicio, y viven en el paquete de arquitectura del equipo. Copiarlos
  aquí crearía una segunda versión que se desincroniza en cuanto arquitectura actualice la suya.
- **La bitácora de uso de IA.** Es un entregable de la asignatura hacia el docente. El uso de IA
  en este repositorio se declara en la plantilla de Pull Request, que tiene una sección para ello.
