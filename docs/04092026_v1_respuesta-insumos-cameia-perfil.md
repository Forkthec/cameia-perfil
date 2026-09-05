# Respuesta de Desarrollo — insumos de inicio de Sprint 1 · `cameia-perfil`

- **Versión:** 1.0
- **Fecha:** 4 de septiembre de 2026
- **Responde a:** `03092026_v1_solicitud-insumos-desarrollo-inicio-sprint.md`
- **Repositorio:** `cameia-perfil` — <https://github.com/Forkthec/cameia-perfil>
- **Responsable:** Ana Sofía
- **HU/CA cubiertas:** ninguna. Esta respuesta sale de **CM-102**, que es base técnica; las HU son CM-16 a CM-20
- **Estado general:** `PROPUESTO`

> **Advertencia de trazabilidad:** todo lo marcado `CONFIRMADO` aquí se ejecutó de verdad y su
> salida está pegada en la sección 12. Lo marcado `PROPUESTO` es recomendación técnica que
> requiere aprobación. Lo marcado `TBD` **no se resolvió escribiendo código**.
>
> A la fecha de esta respuesta el trabajo **no está publicado**: vive en una rama local a la
> espera de que el equipo confirme la convención de nombres de rama.

---

## 1. Formulario de la sección 10

```text
RESPUESTA DE DESARROLLO — INSUMOS DE INICIO

Repositorio:      cameia-perfil
Responsable:      Ana Sofía
Fecha:            4 de septiembre de 2026
HU/CA cubiertas:  ninguna (CM-102 es base técnica)
Estado general:   PROPUESTO

Stack y versiones:
  Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · springdoc-openapi 3.1.0
  JUnit 5 + AssertJ + Mockito (spring-boot-starter-test) · ArchUnit 1.5.0

Gestor de dependencias y lockfile:
  Maven. Manifiesto real: pom.xml. Maven no usa lockfile; las versiones las fija
  spring-boot-starter-parent 4.1.1 salvo springdoc y ArchUnit, fijadas explícitamente.
  Wrapper incluido (mvnw / mvnw.cmd), tipo only-script: NO trae .jar.

Estructura de paquetes/carpetas:
  co.edu.unicauca.cameia.perfil, capas DDD:
    presentation    -> controller, dto, advice
    application     -> service, command
    domain          -> model, service, policy, port, event, exception
    infrastructure  -> persistence(entity, repository), messaging(consumer,
                       publisher, payload), ia, config
  Sin infrastructure/client: el diagrama de paquetes de Perfil no lo tiene.
  Carpetas vacías con .gitkeep.

Comando de instalación:      docker compose build          (o ./mvnw -B dependency:go-offline)
Comando de desarrollo:       docker compose up -d          (o ./mvnw spring-boot:run)
Comando de build:            docker compose build          (o ./mvnw -B clean package)
Comando de pruebas:          docker compose run --rm verify (o ./mvnw -B clean verify)
Comando de lint/formato:     no existe todavía. Ver sección 6.
Health check:                GET /actuator/health -> {"status":"UP"}
                             GET /actuator/health/liveness
                             GET /actuator/health/readiness

Variables obligatorias:
  SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
Variables opcionales:
  SPRING_APPLICATION_NAME, SPRING_PROFILES_ACTIVE, SERVER_PORT,
  SPRING_JPA_HIBERNATE_DDL_AUTO, SPRING_FLYWAY_ENABLED, LOG_LEVEL,
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
Variables sensibles (solo nombres):
  SPRING_DATASOURCE_PASSWORD -> fuente: entorno local, GitHub Environment o
  mecanismo de secretos aprobado. Nunca en Git.

Endpoints y contratos relacionados:
  Ninguno implementado. Los 12 de Sprint 1 están en familias-endpoints §4.
  OpenAPI vivo y vacío en /v3/api-docs; se llena conforme cada HU agregue su controlador.

Eventos relacionados:
  POSTERIOR. Ninguna HU de Sprint 1 de Perfil publica ni consume eventos.

Formato de errores:
  RFC 7807 (application/problem+json), activado con spring.mvc.problemdetails.enabled.
  Propuesta para API-TBD-14. Verificado: un 404 ya responde en ese formato.

Persistencia/migraciones:
  PostgreSQL 16, base cameia_perfil, rol propio. Flyway.
  ddl-auto=validate. src/main/resources/db/migration está vacío porque CM-102 no
  crea entidades; la primera migración llega con CM-16.

Pruebas mantenidas por Desarrollo:
  src/test/java — PerfilApplicationTest (arranque) y ArquitecturaTest (7 reglas ArchUnit).
  Comando: docker compose run --rm verify

Dependencias internas/externas:
  PostgreSQL propio. Ninguna otra en Sprint 1: sin RabbitMQ, sin LLM, sin Firebase.
  Identidad: el Gateway verifica el ID Token y propaga los claims. Perfil NO
  verifica tokens y NO lleva Firebase Admin SDK.

Recursos requeridos:
  TBD medido. Sin medición no se inventa. Ver DEV-IN-09.

GLO-TBD/API-TBD relacionados y recomendación:
  API-TBD-09 COMPLETE/COMPLETED -> recomendación: COMPLETED.
  API-TBD-14 formato de error   -> recomendación: RFC 7807, ya implementado.
  API-TBD-05, 06, 07, 18        -> sin recomendación: son decisiones de producto.
  GLO-TBD-06 perfil activo      -> sin recomendación.

Criterios DoD aplicables:
  build, pruebas unitarias, arranque verificado, health check, README actualizado,
  sin secretos versionados.
Criterios DoD todavía NO aplicables:
  cobertura, análisis estático, detección automática de secretos, staging.
  No existe CI todavía.

Riesgos o bloqueos:
  1. Convención de rama y título de PR contradictoria entre fuentes. BLOQUEA publicar.
  2. Puerto sin asignar (DEV-IN-05).
  3. El C4 de Perfil no tiene diagrama de clases de dominio, solo JPA.
  4. El C4 de Perfil no tiene campo `estado`, pero el backlog lo exige.

Decisiones que requiere del Product Owner/arquitectura:
  Ver sección 5.

Enlaces a PR, commit o documentos:
  Sin PR todavía. Rama local a la espera de la convención confirmada.
```

---

## 2. Entregables comunes (`DEV-IN-01` a `DEV-IN-10`)

| ID | Estado | Respuesta |
|---|---|---|
| `DEV-IN-01` | `CONFIRMADO` | Java 21, Spring Boot 4.1.1, Maven con wrapper. Manifiesto real: `pom.xml` |
| `DEV-IN-02` | `CONFIRMADO` | Comandos reales ejecutados y pegados en la sección 12; `README.md` actualizado |
| `DEV-IN-03` | `CONFIRMADO` | Estructura arriba; convenciones en `03092026_v1_reglas-codigo-backend-cameia.md` |
| `DEV-IN-04` | `PROPUESTO` | `.env.example` en la raíz del repositorio, sin valores secretos |
| `DEV-IN-05` | **`TBD`** | Health check `CONFIRMADO`. **Puerto sin asignar.** Base path: sin prefijo de contexto; las rutas ya empiezan por `/api/v1` |
| `DEV-IN-06` | `CONFIRMADO` | Solo PostgreSQL propio. Sin dependencias externas en Sprint 1 |
| `DEV-IN-07` | `PROPUESTO` | PostgreSQL 16, base `cameia_perfil`, Flyway, `db/migration`. Comando: el de pruebas |
| `DEV-IN-08` | `CONFIRMADO` | Ubicación, comando y alcance arriba |
| `DEV-IN-09` | **`TBD`** | Sin medición no hay respuesta. La JVM está limitada con `MaxRAMPercentage=75` |
| `DEV-IN-10` | `CONFIRMADO` | Riesgos en la sección 5 |

## 3. Sección 7.4 — preguntas específicas de `cameia-perfil`

| Pregunta de la solicitud | Respuesta |
|---|---|
| Payload de creación y obligatoriedad de `nombre_perfil` | **`TBD`.** El backlog de HU-2.2 deja la frase incompleta. Es de Product Owner |
| Estados definitivos y transición de finalización | **`TBD`.** El glosario dice `PENDING/IN_PROGRESS/IN_REVIEW/COMPLETED`; el backlog usa `COMPLETE`. Recomendación: `COMPLETED` (`API-TBD-09`) |
| CRUD o reemplazo de experiencia, educación y habilidades | **`TBD`.** Es `API-TBD-06`, sin recomendación: cambia la semántica del contrato |
| Endpoint y modelo de expectativas | **`TBD`.** Es `API-TBD-05`. HU-2.5 las menciona pero no define dónde se guardan |
| `roles` frente a `target-roles` y prioridad | **`TBD`.** Es `API-TBD-07` |
| Paginación/listado de perfiles completos | **`TBD`.** `GET /api/v1/profiles?status=COMPLETED` no define paginación |
| Control de ownership y concurrencia | **`TBD`.** El backlog dice "verifica ownership del `profile_id` (JWT)", pero Perfil no verifica tokens: eso es del Gateway. **Falta definir cómo llega la identidad**: ¿cabecera propagada, o el microservicio valida el token? De eso depende si entra `spring-boot-starter-oauth2-resource-server` |
| IA/RabbitMQ como alcance actual o posterior | **`POSTERIOR` ambos.** HU-2.6 a HU-2.10 son Sprint 2; ninguna HU de Sprint 1 publica ni consume eventos |

## 4. Sección 4.3 — declaración explícita sobre eventos

Conforme al párrafo que exige declararlo:

> **`cameia-perfil` no implementa ningún evento en Sprint 1.** No se agrega
> `spring-boot-starter-amqp`, no se crean colas, no se declaran variables de RabbitMQ y las
> carpetas `infrastructure/messaging/*` quedan vacías con `.gitkeep`. No hay infraestructura
> inactiva presentada como operativa.

Motivo adicional, y es un hallazgo: **los nombres de las colas se contradicen entre hojas del
mismo C2.** La Cola 3 aparece como `entitlements-actualizados` en "Comunicación - Completo" y
como `suscripcion-actualizada` en "Bounded Context" y "Comunicacion - MVP". La Cola 4 aparece
como `trazas-ia` en "Bounded Context" y como "Rol Objetivo Sugerido" en el C3. No se puede
construir mensajería sobre contratos que no coinciden.

## 5. Riesgos, decisiones abiertas y bloqueos (`DEV-IN-10`)

| # | Descripción | Impacto | Quién decide |
|---|---|---|---|
| 1 | Convención de rama y título de PR contradictoria. Branching, README, `github.txt` y las reglas de código dicen `CA-NNN` sin prefijo; `CONTRIBUTING.md` y la plantilla de PR dicen `<tipo>/CM-NNN`. Además branching §4 admite que la clave Jira puede diferir del identificador de rama | **Bloquea publicar el primer PR** | Equipo |
| 2 | Puerto sin asignar (`DEV-IN-05`) | Bloquea la matriz del Gateway y el despliegue | Arquitectura + Gateway |
| 3 | Perfil **no tiene diagrama de clases de dominio** en el C4, solo el JPA. Las notas del C4 prohíben usar el modelo JPA como dominio | Bloquea CM-16 en adelante: hay que derivar los agregados y que arquitectura los revise | Arquitectura + Perfil |
| 4 | El C4 de Perfil **no tiene campo `estado`**, solo `estadoRevision`; pero el backlog exige `contexto_profesional.estado` con `IN_PROGRESS → IN_REVIEW/COMPLETED` y `GET /profiles?status=COMPLETED` filtra por él | Hueco entre C4 y backlog | Arquitectura + Perfil |
| 5 | Cómo llega la identidad del usuario al microservicio | Define si entra una dependencia de seguridad | Arquitectura + Gateway |
| 6 | `cameia-empleo` no aparece en el inventario de repositorios de la propuesta multirepo, aunque el C3, el C4 y las reglas de código sí lo contemplan | No hay plantilla ni puerto para él | Arquitectura + DevOps |
| 7 | No existe convención de contenedores para el equipo. Se propone una en `04092026_v1_convenciones-docker-cameia.md` | Cada repositorio puede inventar la suya | DevOps + arquitectura |
| 8 | No hay CI. La plantilla de PR exige controles que no existen | Los `N/A` del PR hay que justificarlos a mano | DevOps |

## 6. Lint y formato

**No existe todavía y no se inventó.** Las reglas de código lo dejan abierto: *"Formateador y
linter (Spotless, Checkstyle u otro) — Backend + DevOps"*.

Lo único vigente es el `.editorconfig` del repositorio: UTF-8, LF, 2 espacios por defecto, 4 en
`.java`, sin espacios al final. Se respeta, pero no hay comando que lo verifique.

Recomendación: Spotless, porque formatea además de verificar. Requiere aprobación.

## 7. Lo que este repositorio entrega hoy

| Elemento | Estado |
|---|---|
| Estructura de paquetes con `.gitkeep` | Hecho |
| `pom.xml` con 9 dependencias, cada una justificada | Hecho |
| `application.yml` sin secretos | Hecho |
| `.env.example` con valores sensibles vacíos | Hecho |
| `Dockerfile` y `docker-compose.yml` | Hecho |
| `PerfilApplicationTest` y `ArquitecturaTest` | Hecho, en verde |
| Swagger UI y `/v3/api-docs` | Hecho, verificado |
| `README.md` con comandos reales | Hecho |
| Publicación (push y PR) | **Pendiente**, a la espera del riesgo 1 |

## 8. Evidencia de ejecución

`docker compose run --rm verify`:

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- PerfilApplicationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- ArquitecturaTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  50.262 s
```

Servicio arriba:

```text
GET /actuator/health            200  {"groups":["liveness","readiness"],"status":"UP"}
GET /actuator/health/liveness   200  {"status":"UP"}
GET /actuator/health/readiness  200  {"status":"UP"}
GET /swagger-ui.html            200  -> /swagger-ui/index.html  <title>Swagger UI</title>
GET /v3/api-docs                200  JSON válido, openapi 3.1.0, paths {}
GET /api/v1/profiles            404  content-type: application/problem+json
```

El último confirma que el formato RFC 7807 está activo antes de que exista el primer controlador.
