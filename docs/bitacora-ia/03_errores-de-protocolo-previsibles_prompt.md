# Entrada 03 · Qué hace Perfil ante peticiones mal formadas que hoy terminan en 500

- **Fecha:** 2026-10-17 · **Herramienta:** Claude Code (Sonnet 5.5) · **Integrante:** Paula Andrea Muñoz Delgado
- **Salida asociada:** [`../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md`](../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md)

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y escribes la spec de CM-335 (errores de protocolo previsibles) de cameia-perfil bajo el
estándar de la dueña del Backend.

Contexto: Con peticiones reales contra el jar empaquetado se midió que un formulario mal codificado (`a=%zz`), un parámetro de consulta
mal codificado (`?lang=%zz`, `?lang=%C3`), un multipart sin boundary y un `TRACE` terminan en el 500 genérico o en la forma por defecto de
Spring, y que un `X-User-Id` con tabulación se acepta como identidad. La regla de errores visibles pide que todo fallo previsible tenga
su código estable y su mensaje, y que el 500 genérico quede solo para lo imprevisto.

Tarea: Decide qué estado y qué código devuelve cada caso (por ejemplo, 415 o 422 para un formulario o una consulta mal codificados, y si
el multipart roto lleva un código propio), cómo se reconoce la excepción de Tomcat sin que `presentation` importe el contenedor, y si
`TRACE` y `/error` se resuelven con un controlador de errores propio.

Condiciones: Cambio aditivo para Frontend; ningún valor recibido ni mensaje de la excepción de Tomcat aparece en la respuesta ni en el
registro; sin migración; cada decisión con alternativas descartadas y cada caso con su prueba y valores literales.

Formato: Tabla de decisiones con quién decidió, el porqué y lo descartado; requisitos EARS; tabla de casos con la prueba que los cubre.
```
