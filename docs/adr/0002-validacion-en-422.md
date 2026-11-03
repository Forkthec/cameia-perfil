# ADR 0002 · Validación y cuerpo ilegible en 422

## Estado

Aceptada. Decisión de Backend, 6 de octubre de 2026.

## Contexto

Perfil respondía 400 cuando un campo no pasaba la validación y cuando el cuerpo no se podía leer como JSON. La regla transversal de validación del proyecto pide 422 con un elemento por campo rechazado, y Cuentas ya responde así. Con dos estados distintos para el mismo tipo de error, Frontend tendría que tratar cada servicio por separado.

## Decisión

- Un campo que no pasa Bean Validation responde **422** `VALIDATION_FAILED`, con `errors[]` (`field`, `code`, `message`).
- Un cuerpo que no se puede leer (JSON mal formado, tipo incorrecto, valor de enumeración desconocido) responde **422** `REQUEST_BODY_INVALID_FORMAT`.
- Un dato que incumple una regla del dominio (obligatorio, largo, signo, fecha mal escrita, opción que no existe o fechas que no encajan) responde igual que Bean Validation: **422** `VALIDATION_FAILED` con `errors[]`, y en cada elemento el código de la regla y un mensaje que dice qué corregir. Lo lanza `InvalidFieldsException`. Hasta el 7 de octubre respondía un único `REQUEST_INVALID_VALUE` sin campo y con un mensaje genérico; ver la decisión D18 de la spec de CM-271.

Los tres siguen la forma común de [errores.md](../errores.md).

## Consecuencias

- Perfil y Cuentas responden igual a los errores de entrada.
- Frontend ajusta los formularios de Perfil que esperaban 400 para leer 422; se le avisa con el catálogo de códigos.
- El cuerpo ilegible se aparta del mapa base del estándar (400). Queda registrado en la sección «Estados que difieren del mapa base» de [errores.md](../errores.md).

## Alternativas descartadas

- **400 para todo, como el mapa base.** No cumple la regla transversal de validación y deja a Perfil distinto de Cuentas.
- **422 para la validación y 400 para el cuerpo ilegible.** Para el Usuario los dos casos son lo mismo (revisar lo enviado) y el cliente tendría que manejar dos estados.
