# Evidencia antes — evento del perfil (rama sin cambios, 15-oct-2026)

Aplicación empaquetada con `docker compose up -d --build` sobre `origin/develop` (504f838). Perfil completo por la API con
`X-User-Id: uid-ev-antes-1` (crear, nombre y resumen, formación, habilidad, rol objetivo) y `POST …/completion` → 200 `COMPLETED`.

| Petición | Resultado antes | Resultado esperado después |
|---|---|---|
| `select count(*) from evento_saliente` tras finalizar | `0` | `1` fila con tipo `perfil.profesional-actualizado` |
| `GET /api/exchanges/%2F/perfil.events/bindings/source` (RabbitMQ) | `[]` (nadie ligado) | un enlace con clave `perfil.profesional-actualizado` hacia `entrevista.perfil-profesional-actualizado` |
| `update perfil_profesional set resumen = repeat('a', 2001)` | `ERROR:  value too long for type character varying(2000)` | igual (la base sigue rechazando 2001; CA-2.5.18) |

Salida literal de la primera consulta:

```
 count 
-------
     0
(1 row)
```
