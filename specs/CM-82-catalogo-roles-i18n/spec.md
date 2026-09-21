# Spec — CM-82: Catálogo de roles profesionales con soporte i18n

**Ticket:** [CM-82](https://f0rktech.atlassian.net/browse/CM-82)
**Tipo:** Subtarea de CM-69 (gestión de roles objetivo, Frontend)
**Fecha:** 21-sep-2026
**Autor:** Ana Sofía Arango Yanza

---

## Contexto

La tabla `rol_profesional` contiene ~45 roles TI en español. El frontend necesita mostrar
este catálogo en español o inglés según el idioma del usuario. Actualmente no existe un
endpoint dedicado para consultar el catálogo completo, y la tabla no tiene columnas de
traducción.

---

## Requisitos EARS

### Consulta del catálogo

| ID | Requisito |
|---|---|
| EARS-1 | WHEN el cliente llama `GET /api/v1/roles?lang=es`, THE SYSTEM SHALL retornar la lista de roles con `nombre` y `categoria` en español. |
| EARS-2 | WHEN el cliente llama `GET /api/v1/roles?lang=en`, THE SYSTEM SHALL retornar la lista de roles con `nombre` y `categoria` en inglés. |
| EARS-3 | WHEN el parámetro `lang` es omitido, THE SYSTEM SHALL comportarse como `lang=es` (español por defecto). |
| EARS-4 | WHEN el parámetro `lang` contiene un valor distinto de `es` o `en`, THE SYSTEM SHALL retornar 400 Bad Request con `ProblemDetail`. |
| EARS-5 | THE SYSTEM SHALL retornar los roles agrupados lógicamente por `categoria`, ordenados por `categoria` ASC y luego por `nombre` ASC. |
| EARS-6 | THE SYSTEM SHALL retornar para cada rol: `id` (UUID), `nombre`, `categoria`. |
| EARS-7 | THE SYSTEM SHALL documentar el endpoint en Swagger con respuestas 200 y 400. |

### Datos

| ID | Requisito |
|---|---|
| EARS-8 | THE SYSTEM SHALL persisistir `nombre_en` y `categoria_en` en la tabla `rol_profesional` vía migración Flyway `V4`. |
| EARS-9 | THE SYSTEM SHALL incluir en `V4` las traducciones al inglés de los ~45 roles del seed existente. |

---

## Contrato de la API

```
GET /api/v1/roles?lang={es|en}
Authorization: no requerida (catálogo público)
```

**Respuesta 200:**
```json
[
  {
    "id": "a0000001-0000-0000-0000-000000000001",
    "nombre": "Desarrollador Frontend",
    "categoria": "Desarrollo"
  }
]
```
*(en inglés: `"nombre": "Frontend Developer"`, `"categoria": "Development"`)*

**Respuesta 400:**
```json
{
  "title": "Parámetro inválido",
  "detail": "El valor 'fr' no es un idioma soportado. Use 'es' o 'en'.",
  "status": 400
}
```

---

## Fuera de alcance

- Agregar nuevos idiomas (más allá de `es` y `en`)
- Paginación (el catálogo es pequeño y estático)
- Autenticación en este endpoint (es de referencia, no datos privados)
- CRUD del catálogo

---

## Criterios de aceptación

| # | Escenario | Resultado esperado |
|---|---|---|
| CA-1 | `GET /api/v1/roles` (sin lang) | 200, roles en español |
| CA-2 | `GET /api/v1/roles?lang=es` | 200, roles en español |
| CA-3 | `GET /api/v1/roles?lang=en` | 200, roles en inglés |
| CA-4 | `GET /api/v1/roles?lang=fr` | 400 ProblemDetail |
| CA-5 | Respuesta contiene `id`, `nombre`, `categoria` por item | ✓ |
| CA-6 | Respuesta ordenada por `categoria` ASC, luego `nombre` ASC | ✓ |
| CA-7 | Test unitario cubre CA-2 y CA-3 | verde |
| CA-8 | Test unitario cubre CA-4 | verde |
| CA-9 | Swagger documenta 200 y 400 | ✓ |
