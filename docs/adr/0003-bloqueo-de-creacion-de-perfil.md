# ADR 0003 · Bloqueo por Usuario al crear el Perfil Profesional

## Estado

Aceptada. Decisión de Paula Andrea Muñoz Delgado, 7 de octubre de 2026.

## Contexto

El Plan Free permite un Perfil Profesional por Usuario. La comprobación «¿ya tiene perfil?» y la inserción eran dos pasos separados, y la tabla no tiene restricción de unicidad por Usuario, porque Premium admitirá varios perfiles. Con dos clics seguidos en «Llenado Manual», las dos peticiones pasaban la comprobación antes de que la primera guardara y se creaban dos perfiles. Sin el bloqueo que describe este ADR, la prueba de integración con 5 peticiones simultáneas del mismo Usuario contra PostgreSQL real crea 5 perfiles.

## Decisión

`POST /api/v1/profiles` sigue esta regla, dentro de una sola transacción con el aislamiento por defecto de PostgreSQL (`READ COMMITTED`):

1. Cuenta los perfiles del Usuario (`antes`).
2. Toma un bloqueo de transacción de PostgreSQL por Usuario (`pg_advisory_xact_lock` sobre el hash de su `firebase_uid`). Otra creación del mismo Usuario espera aquí hasta que la primera confirme o se deshaga.
3. Vuelve a contar (`después`), y ve lo que confirmó la creación anterior.
4. Si `después` es menor que el cupo (1 en el Plan Free), crea el perfil vacío y responde 201.
5. Si no, y `antes` era menor que el cupo, la petición llegó mientras otra creaba el perfil: responde 201 con ese mismo perfil.
6. Si no, el Usuario ya tenía el máximo antes de pedir: responde 409 `PROFILE_LIMIT_REACHED`.

El bloqueo se libera solo al terminar la transacción y solo afecta a las creaciones del mismo Usuario.

## Consecuencias

- Nunca se crea más de un perfil por Usuario en el Plan Free. En 30 rondas de 8 peticiones simultáneas contra la app real, cada ronda terminó con un solo perfil.
- Las peticiones que llegan mientras la creación está en proceso reciben 201 con el mismo perfil, y Frontend no necesita cambios.
- Una petición que llega cuando la creación anterior ya terminó, aunque sea pocos milisegundos después, cuenta ese perfil en el paso 1 y recibe 409. En la misma prueba, 9 de 30 rondas tuvieron entre 1 y 3 respuestas 409 de ese tipo.
- Premium cambia solo el cupo con el que se compara; la regla no cambia.
- Si la creación en proceso falla y se deshace, la que esperaba crea el perfil normalmente.
- El bloqueo depende de PostgreSQL; una prueba de integración con PostgreSQL real comprueba la regla, la independencia entre Usuarios y la creación tras deshacer.

## Alternativas descartadas

- **Restricción `UNIQUE` sobre `firebase_uid`.** Impide los duplicados, pero choca con Premium, que admitirá varios perfiles, y convierte la segunda petición en un error.
- **Aislamiento `SERIALIZABLE`.** Obliga a reintentar las transacciones que fallan por conflicto y afecta a todas las escrituras de la transacción, no solo a la creación.
- **409 a toda petición repetida.** Mostraría el mensaje de cupo, y más adelante el Paywall, a quien solo hizo doble clic.
- **Ventana de tiempo.** Tratar como repetida una petición que llega N segundos después exige fijar un valor arbitrario.
- **Encabezado `Idempotency-Key`.** Es la solución general, pero exige cambiar `cameia-web`.
