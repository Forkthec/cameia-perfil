# ADR 0009 · Base de datos no disponible

## Estado

Aceptada. Decisión de Backend, 19 de octubre de 2026; aprobada por Paula Andrea Muñoz Delgado el 19 de octubre de 2026.

## Contexto

Cuando la base de datos no estaba disponible, cualquier ruta que la usa terminaba en el 500 genérico (`INTERNAL_ERROR`), y con la configuración por defecto del pool la respuesta tardaba 30 s, más que el límite de espera del Gateway, de modo que el cliente ni siquiera la veía. Es un fallo previsible: la persona puede reintentar.

Medido con PostgreSQL 16 real (19 de octubre de 2026):

| Caso | Lo que llega al manejador |
|---|---|
| Pool agotado | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` («Connection is not available, request timed out») |
| Consulta que la base corta por su tiempo máximo | `QueryTimeoutException` con `SQLState` `57014` |
| Conexión cortada por el servidor antes de la consulta | `JpaSystemException` ← `org.hibernate.TransactionException` («Unable to rollback») ← `SQLException` «Connection is closed», sin `SQLState` |
| Corte dentro de una transacción | igual que el anterior |
| Corte justo antes del commit | `DataAccessResourceFailureException` ← `PSQLException` `57P01`; la conexión cerrada viaja en `getSuppressed()`, no en `getCause()` |
| Base detenida con el pool por defecto | 500 tras unos 30 s: el `connection-timeout` por defecto de Hikari |

## Decisión

- **D-1** Un clasificador `DatabaseUnavailability` en `presentation.advice` y una rama en el manejador general. Recorre la excepción, sus causas y sus suprimidas (hasta 64 nodos, sin repetir) y responde 503 `DATABASE_UNAVAILABLE`. Sigue el precedente de `BadParameterEncoding`.
- **D-2** Cuentan como indisponibilidad las excepciones de acceso a recursos de Spring, las de conexión y transacción de Hibernate y de JDBC, el tiempo agotado de consulta y un `SQLException` con estado `08xxx`, `57P01`, `57P02`, `57P03` o `53300`. No cuentan `22xxx`, `23xxx`, `42xxx` ni `55P03`: son fallos del dato o de la consulta, y `55P03` conserva sus 409.
- **D-3** `spring.datasource.hikari.connection-timeout` en 5000 ms, para que el 503 llegue antes del límite del Gateway.
- **D-4** Mismo título («Servicio no disponible») y mensaje («Ocurrió un error. Inténtalo de nuevo.») que `BIRTH_DATE_UNAVAILABLE`: la persona no puede actuar distinto y un mensaje específico solo ayudaría a conocer la infraestructura.
- **D-5** Sin encabezado `Retry-After`: no se sabe cuándo vuelve la base.
- **D-6** No se configura tiempo máximo de consulta en esta tarea.
- **D-7** Un solo código para pool agotado, base detenida, corte y consulta lenta. El registro distingue la causa con `sqlState` y la clase.

El registro es una línea `ERROR` por petición con `code`, `requestId`, `firebaseUid` y `sqlState`, y la excepción redactada; no repite el mensaje de la base ni del pool en la respuesta. Para que sea la única línea, `application.yml` baja a `ERROR` los avisos de Hibernate (`org.hibernate.orm.jdbc.error`) y apaga el error de la transacción de Spring (`org.springframework.transaction.interceptor.TransactionInterceptor`, «Application exception overridden by rollback exception»)): los dos citan el SQL y el mensaje de PostgreSQL, que pueden traer valores de columnas, y ninguno lleva el `requestId`. Los avisos del pool de conexiones se conservan: son texto fijo sin datos y delatan conexiones caducadas aunque la base esté viva. Los fallos que no son de la base no pierden su línea: el 500 genérico registra la suya, redactada, y los consumidores y el Job de relevo registran los suyos.

## Consecuencias

- Cambio aditivo para Frontend: un código nuevo, `DATABASE_UNAVAILABLE` (503), que reemplaza al 500 genérico cuando la base falla. Se avisa con el catálogo de errores.
- Si la base falla mientras se lee la fecha de nacimiento, gana `DATABASE_UNAVAILABLE` y no `BIRTH_DATE_UNAVAILABLE`.
- Una consulta que no termina sigue sin producir error: sin tiempo máximo de consulta solo se cubre lo que la base misma corta. Destino: la tarea de medición de consultas lentas.
- Los consumidores de RabbitMQ y el Job de relevo no pasan por el manejador HTTP y no cambian.
- El 503 queda documentado en las 15 operaciones de OpenAPI con un `OperationCustomizer`.

## Alternativas descartadas

- **Envolver los adaptadores de persistencia en una excepción de dominio.** No cubre abrir ni cerrar la transacción, donde ocurre la mayoría de los cortes.
- **Un aspecto AOP.** Más maquinaria que una rama en el manejador.
- **Incluir `55P03` como indisponibilidad.** Rompería los 409 vigentes.
- **Dejar el `connection-timeout` en 30 s.** El 503 sería invisible para el cliente; con 3 s habría falsos 503 en el arranque en frío de Cloud Run; una variable nueva pediría trabajo de DevOps y `.env.example`.
- **Un mensaje propio.** Solo ayudaría a un atacante.
- **`Retry-After: 5` fijo.** Promete una recuperación que no se conoce.
- **Fijar `jakarta.persistence.query.timeout` ahora.** Cambia el comportamiento de consultas lentas legítimas y pide medir antes.
- **Un código por causa.** La acción de la persona es la misma: reintentar.
