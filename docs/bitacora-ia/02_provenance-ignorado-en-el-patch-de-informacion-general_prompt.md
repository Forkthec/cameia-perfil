# Entrada 13 · Qué hace el PATCH de información general con un provenance que llega en el cuerpo

- **Fecha:** 2026-10-06 · **Herramienta:** Claude Code (Sonnet 5.5) · **Integrante:** Paula Andrea Muñoz Delgado
- **Salida asociada:** [`../salidas/13_provenance-ignorado-en-el-patch-de-informacion-general.md`](../salidas/13_provenance-ignorado-en-el-patch-de-informacion-general.md)

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y escribes la spec de CM-54 (información general y resumen profesional, HU-2.3) de cameia-perfil
bajo el estándar de la dueña del Backend.

Contexto: El `PATCH /api/v1/profiles/{id}` de HU-2.3 acepta `name`, `summary` y `preferredModality`. El cuerpo puede traer campos que el contrato no define, entre ellos `provenance`. La regla transversal RT-01-CA06 dice que los campos no esperados se ignoran, y HU-2.3 no pide que el cliente declare la procedencia del resumen; en el MVP todo resumen es manual y la procedencia se deriva sin columna.

Tarea: Decide qué debe hacer el servicio cuando el cuerpo trae `provenance`: (a) ignorarlo, (b) rechazarlo con 422, (c) guardarlo. Justifica con RT-01-CA06 y con el riesgo de asignación masiva (OWASP API3).

Condiciones: No cambies el contrato publicado; no agregues columnas; la respuesta sigue devolviendo `summaryProvenance` derivado; cada rama lleva su prueba con valores literales (por ejemplo `provenance:"AI_SUGGESTED"` sin efecto).

Formato: Veredicto con la regla que lo respalda, alternativas descartadas con su razón y la prueba que lo demuestra.
```
