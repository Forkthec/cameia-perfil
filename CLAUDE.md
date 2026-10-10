# cameia-perfil

## 1. Servicio

CAMEIA ofrece práctica y simulación de entrevistas virtuales para preparar entrevistas de trabajo. Este repositorio es el microservicio de Perfil Profesional, dueño de los perfiles profesionales, la experiencia, la educación, las habilidades, los roles objetivo y la revisión humana de lo que sugiere la IA.

- No es dueño de credenciales, pagos, sesiones de entrevista ni del consumo.
- No verifica tokens: eso es del Gateway. No usa Firebase Admin SDK.
- No calcula la cuota del plan: consume su réplica. Recalcular la verdad de otro servicio es un antipatrón.
- Tiene base propia (`cameia_perfil`) y ninguna clave foránea hacia otro servicio.

Las reglas comunes de Backend están en [docs/estandar-backend.md](docs/estandar-backend.md) y los principios no negociables, en [docs/constitution.md](docs/constitution.md).

## 2. Estructura y dependencias

Pila: Java 21, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 16 y Flyway.

El paquete base es `co.edu.unicauca.cameia.perfil`, distinto del `tech.cameia.<servicio>` de los demás servicios. Se conserva porque renombrarlo toca todos los archivos del servicio y es una tarea aparte, no parte de alinear los documentos.

Capas con Domain-Driven Design. Se crean subcarpetas solo con un motivo de cambio distinto.

```text
co.edu.unicauca.cameia.perfil
├── presentation
│   ├── controller          // adaptadores de entrada HTTP
│   ├── dto                 // request/response del contrato público
│   └── advice              // manejo de errores HTTP
├── application
│   ├── service             // casos de uso (orquestación, transacción)
│   └── command             // objetos de entrada de los casos de uso
├── domain
│   ├── model               // agregados, entidades, objetos de valor, enumerados
│   ├── port                // interfaces que el dominio define y no implementa
│   └── exception           // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity          // modelo JPA, distinto del modelo de dominio
    │   └── repository      // Spring Data y adaptadores de los puertos
    ├── messaging
    │   ├── consumer        // @RabbitListener
    │   ├── publisher       // RabbitTemplate
    │   ├── payload         // contratos de mensaje versionados
    │   └── config          // colas y configuración de RabbitMQ
    └── config              // configuración de Spring
```

El servicio no tiene `infrastructure/client` ni `persistence/mapper`: el mapeo entre dominio y entidad se escribe a mano y es privado al paquete del adaptador (en el adaptador, o en una clase `<Agregado>Mapping` del mismo paquete cuando el adaptador pasaría de 200 líneas). Si hace falta una carpeta nueva (`domain/service`, `domain/policy`, `domain/event`, `infrastructure/ia`), se pregunta antes de crearla.

**Regla de dependencias.** `domain` no importa nada de `presentation`, `application` ni `infrastructure`, ni `org.springframework`, `jakarta.persistence`, `com.rabbitmq` o `com.google`; `application` depende de `domain` por sus puertos; `infrastructure` implementa los puertos de `domain`; `presentation` no importa `infrastructure`; no hay dependencias circulares. La vigila `ArquitecturaTest`, y toda clase nueva debe pasarla.

**Componentes.**

| Capa | Clase | Qué hace |
|---|---|---|
| Controller | `ProfileController` | Gestiona el perfil y todo lo que cuelga de él |
| Controller | `ProfessionalRoleController` | Expone el catálogo de roles profesionales |
| Aplicación | `ProfileAppService` | Casos de uso del perfil; comprueba el dueño en cada acceso |
| Aplicación | `ProfessionalRoleAppService` | Lee el catálogo de roles por idioma |
| Dominio | `ProfessionalProfile` | Agregado: contiene experiencia, educación, habilidades y roles objetivo, y sus invariantes (completitud, 1 a 5 roles objetivo, no quitar el último rol de un perfil finalizado) |
| Dominio | `ProfessionalProfileRepository`, `ProfessionalRoleRepository` | Puertos de persistencia |
| Infraestructura | `ProfessionalProfileRepositoryAdapter`, `ProfessionalRoleRepositoryAdapter` | Implementan los puertos sobre Spring Data |
| Infraestructura | `AccountEventsRabbitConfig`, `AccountEventsRetryConfig`, `AccountCreatedListener`, `AccountDeletedListener`, `AccountEventsErrorHandler`, `AccountEventsRecoverer` | Consumen `cuenta.creada` y `cuenta.eliminada` para mantener la réplica de la fecha de nacimiento, con Inbox, reintentos y una cola de fallidos por cola |
| Infraestructura | `RabbitConfig`, `SubscriptionUpdatedListener`, `ConsumptionRecordedListener`, `ProfileEventPublisher` | Mensajería restante: esqueletos (ver sección 10) |

**Persistencia en tres piezas.** Un `JpaRepository` suelto no es el repositorio del dominio. El puerto vive en `domain/port` con el vocabulario del dominio; el `JpaRepository` y el adaptador viven en `infrastructure/persistence/repository` (el adaptador implementa el puerto y es el único que conoce las dos orillas). `application.service` inyecta el puerto, nunca el adaptador ni el `JpaRepository`.

**Idioma y trazabilidad.** El código va en inglés y lo que lo explica, en español. Los nombres del glosario del proyecto van en español, así que cada clase de dominio lleva en su Javadoc el término del que sale. Un concepto nuevo se agrega a esta tabla antes de usarlo, para que dos personas no lo traduzcan distinto.

| Glosario (español) | Código (inglés) |
|---|---|
| Perfil Profesional | `ProfessionalProfile` |
| Hoja de Vida (CV) | `Resume` |
| Experiencia Laboral | `WorkExperience` |
| Educación | `Education` |
| Habilidad del perfil | `ProfileSkill` |
| Rol profesional | `ProfessionalRole` |
| Rol Objetivo | `TargetRole` |
| Procedencia del dato | `DataProvenance` |
| Estado de revisión | `ReviewStatus` |
| Guardia de Cuota | `QuotaGuard` |
| Política de Procedencia y Revisión | `ProvenanceAndReviewPolicy` |
| Réplica de cuota y plan | `PlanQuotaReplica` |
| Extractor de Texto | `TextExtractor` |
| Generador de Texto | `TextGenerator` |

No se traducen los códigos de enumerados (`IN_PROGRESS`, `COMPLETED`, `MANUAL`, `AI_SUGGESTED`, `AI_EDITED`), que son contrato con Frontend, ni los nombres de tablas y columnas, que van en español `snake_case`:

```java
@Entity
@Table(name = "perfil_profesional")
public class ProfessionalProfileEntity { }
```

## 3. Límites de confianza

El servicio solo debe aceptar tráfico del API Gateway; la autenticidad la garantiza el despliegue (IAM con token OIDC), no la aplicación.

- **Identidad.** Llega en el encabezado `X-User-Id` (el `firebaseUid`), nunca en el cuerpo ni en la ruta. No se confía en un encabezado enviado directamente por un cliente externo.
- **Sin identidad: 401.** Sin `X-User-Id`, con él en blanco o con más de 128 caracteres, toda ruta responde 401 `IDENTITY_REQUIRED`.
- **Recurso ajeno: 403.** Cada acceso a un perfil comprueba que su dueño sea el usuario del encabezado; un perfil ajeno responde 403.
- **Una sola ruta sin identidad:** `GET /api/v1/profiles/professional-roles`, el catálogo de roles, que no lee datos de ningún usuario.
- **Rutas.** Todo endpoint cuelga de `/api/v1/profiles/**`, porque el Gateway enruta hacia Perfil con un único predicado, `Path=/api/v1/profiles/**`. Un endpoint fuera de ese prefijo compila y pasa sus pruebas, pero es inalcanzable en el despliegue: el Gateway responde 404 antes de que la petición llegue. Si hace falta uno fuera del prefijo, se pide el predicado al Gateway y se espera.

## 4. Contrato y errores

- La versión va en la ruta: `/api/v1/...`. Los recursos van en inglés y en plural, y los nombres JSON en `camelCase`.
- Los errores son `ProblemDetail` (RFC 9457) con `Content-Type: application/problem+json`, activado con `spring.mvc.problemdetails.enabled`. El formato y las reglas están en la [sección 6 del estándar](docs/estandar-backend.md#6-errores) y lo que el servicio emite hoy, en [docs/errores.md](docs/errores.md).
- La finalización incompleta del perfil responde la forma común con `code` `PROFILE_INCOMPLETE` y, además, `missingRequirements`.
- Los mensajes de las excepciones de negocio llegan al usuario tal cual; un fallo técnico no debe exponer su detalle.

| Método | Ruta (bajo `/api/v1/profiles`) | Qué hace |
|---|---|---|
| `POST` | (la raíz, `/api/v1/profiles`) | Crea el perfil del usuario |
| `GET` | `/{id}` | Lee un perfil |
| `PATCH` | `/{id}` | Edita nombre, titular y resumen |
| `POST`, `DELETE` | `/{id}/work-experiences`, `/{id}/work-experiences/{expId}` | Agrega o quita experiencia laboral |
| `POST`, `DELETE` | `/{id}/educations`, `/{id}/educations/{eduId}` | Agrega o quita educación |
| `PATCH` | `/{id}/salary-expectation` | Actualiza la expectativa salarial |
| `POST`, `DELETE` | `/{id}/skills`, `/{id}/skills/{skillId}` | Agrega o quita una habilidad |
| `POST` | `/{id}/review-requests` | Pide la revisión del perfil; oculta del OpenAPI y sin HU en el MVP, CM-67 decide si se borra (D26) |
| `POST`, `PATCH`, `DELETE` | `/{id}/target-roles`, `/{id}/target-roles/{roleId}` | Gestiona los roles objetivo |
| `POST` | `/{id}/completion` | Finaliza el perfil |
| `GET` | `/professional-roles?lang=es\|en` | Catálogo de roles profesionales (sin identidad) |

OpenAPI en `http://localhost:8082/v3/api-docs` y Swagger UI en `http://localhost:8082/swagger-ui.html`.

## 5. Datos

Base PostgreSQL propia `cameia_perfil` y migraciones Flyway en `src/main/resources/db/migration`:

| Migración | Qué hace |
|---|---|
| `V1__crear_tablas_perfil_profesional.sql` | Crea `perfil_profesional`, `experiencia_laboral`, `educacion`, `rol_objetivo` y `habilidad_perfil` |
| `V2__eliminar_seniority.sql` | Quita la columna `seniority` |
| `V3__catalogo_roles_profesionales.sql` | Crea el catálogo `rol_profesional` y la clave foránea desde `rol_objetivo` |
| `V4__i18n_catalogo_roles.sql` | Agrega los nombres en inglés del catálogo |
| `V5__replica_fecha_nacimiento.sql` | Crea `fecha_nacimiento_usuario` (réplica de la fecha de nacimiento por Usuario) y `evento_procesado` (Inbox de eventos de cuenta) |

No hay claves foráneas hacia otros servicios. Las tablas y columnas van en `snake_case` español y las fechas en `TIMESTAMPTZ`; el resto de las reglas de esquema están en la [sección 7 del estándar](docs/estandar-backend.md#7-base-de-datos). Todo cambio de esquema es una migración nueva.

## 6. Seguridad

- **Nunca se registra el contenido de un CV, un resumen, una experiencia ni dato alguno del perfil.** Son datos personales: se registra el identificador y el resultado, no el contenido.
- De un CV solo se guarda el texto extraído, nunca el archivo.
- Ningún dato generado por IA se persiste sin `procedencia` y `estadoRevision`: nada propuesto por la IA se da por válido hasta que el usuario lo confirma o lo edita.
- Perfil no calcula cuota ni habla directamente con un proveedor de LLM: cuando exista, va detrás de un puerto.
- Todo cambio de autenticación, autorización o datos de otro usuario se revisa contra la [sección 8 del estándar](docs/estandar-backend.md#8-seguridad).

## 7. Pruebas

| Capa | Tipo | Regla |
|---|---|---|
| `domain` | Unitaria pura | Sin contexto de Spring; si lo necesita, no es dominio |
| `application` | Unitaria con dobles | Los puertos se sustituyen por mocks |
| `infrastructure.persistence` | Integración | Contra PostgreSQL real; H2 está prohibido |
| `presentation` | Contrato | `@WebMvcTest` |
| Arquitectura | Estructural | ArchUnit, en `ArquitecturaTest` |

- Las unitarias se llaman `<Clase>Test` y las de integración `<Clase>IT`; los nombres van en inglés y `@DisplayName`, en español.
- Las pruebas nuevas se nombran `metodo_shouldResultado_whenCondicion`.
- Toda prueba que levante el servidor usa puerto aleatorio (`RANDOM_PORT`), nunca el 8082.
- Datos sintéticos, nunca reales. Cada regla de negocio tiene su prueba positiva y su prueba negativa.
- Las pruebas de base de datos (`*IT`) levantan PostgreSQL con Testcontainers y las corre Failsafe en `clean verify`; necesitan Docker encendido. Las reglas de pruebas y de cobertura están en la [sección 9 del estándar](docs/estandar-backend.md#9-pruebas-y-cobertura).

## 8. Verificación

Verificación completa: `./mvnw.cmd clean verify`. Genera el informe de cobertura en `target/site/jacoco/index.html`.

- Sin instalar Java ni Maven: `docker compose run --rm verify`.
- Con Docker: `docker compose up -d` levanta Perfil y PostgreSQL 16.
- Con la aplicación en `http://localhost:8082`: salud en `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness`; solo `health` e `info` están expuestos.
- RabbitMQ: `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, `SPRING_RABBITMQ_VIRTUAL_HOST` y `SPRING_RABBITMQ_SSL_ENABLED` (`true` en staging y producción); `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false` apaga los consumidores en una instancia que solo atiende la API.
- El puerto sale de `SERVER_PORT` (8082 por defecto; en Cloud Run, de `PORT`). El Gateway apunta a Perfil con `CAMEIA_PERFIL_URL`.

## 9. Contribución

Rama, commit, tipos, título de PR, revisión y merge: rige [CONTRIBUTING.md](CONTRIBUTING.md). Lo que este repositorio añade:

- El título del PR lleva `[IA-ASISTIDO]` al final cuando hubo IA; el commit no lo lleva. Un commit asistido por IA lleva el trailer `Co-Authored-By` con el modelo.
- Una IA puede abrir un PR; nunca lo fusiona. El control humano lo marca la persona que revisa.
- Un PR cubre una pieza reconocible y no pasa de 1000 líneas entre agregadas y eliminadas.
- Antes del código hay una spec aprobada en `specs/CM-NNN-Descripcion/` (`spec.md`, `plan.md` y `tasks.md`); el flujo está en la sección 12 de [docs/estandar-backend.md](docs/estandar-backend.md).
- Las decisiones con peso humano se registran en [docs/bitacora-ia/](docs/bitacora-ia/README.md).

Las specs nuevas viven en `specs/CM-NNN-Descripcion/` con `Descripcion` en PascalCase; las carpetas de spec existentes con otro nombre no se renombran.

## 10. Pendientes

| Pendiente | Responsable | Qué bloquea |
|---|---|---|
| API y consumidor de RabbitMQ en el mismo proceso | Backend (arranque, `Dockerfile`, `docker-compose.yml`) y DevOps (recursos de Cloud Run) | Escalar la API sin duplicar consumidores |
| Los consumidores de suscripción y de consumo y el publicador de eventos del perfil son esqueletos con `TODO`: sin payload definido, idempotencia ni Outbox | Backend, con Cuentas | Reaccionar a cambios de plan; publicar cambios del perfil |
| El cupo de perfiles es fijo en 1 (Plan Free), a la espera de la réplica del plan | Backend y Product Owner | Perfiles múltiples según el plan |
| Swagger UI y OpenAPI sin bandera de apagado ni perfil `prod` | Backend (CM-283) | Cerrar la documentación en producción |
| Cobertura por debajo de la meta del estándar | Backend (CM-283) | Cumplir el 70 % de la rúbrica y la meta del 90 % |
| Lecturas que consume Entrevista (`GET /api/v1/profiles?status=COMPLETED`, con paginación, y las sugerencias de roles): no existen en el código | Backend, con Entrevista | Integración con Entrevista |
| Extracción de CV, sugerencias y asistente de redacción con IA: sin implementar (`Resume`, `QuotaGuard`, adaptadores de LLM) | Backend y Product Owner | Todo lo que dependa de IA |
