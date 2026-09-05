# AGENTS.md — reglas de código de `cameia-perfil`

> Reglas operativas de **este** repositorio. Deriva de
> `03092026_v1_reglas-codigo-backend-cameia.md` v1.3, pero contiene **solo lo que aplica al
> Microservicio de Perfil Profesional**: quien trabaje aquí no necesita cargar las reglas de los
> otros cinco repositorios.
>
> Si algo de aquí contradice al documento de reglas del equipo, manda el documento del equipo
> y hay que avisarlo.

---

## 0. Antes de escribir una línea

1. **No se publica nada sin autorización expresa.** Prohibido `git push`, abrir Pull Request,
   hacer merge, usar comandos `gh` que escriban en GitHub, o tocar `main` y `develop`.
   Commits locales sí.
2. **No asumir: preguntar.** Si falta un dato, se pregunta. Si dos documentos se contradicen, se
   avisa y se detiene. Un `TBD` del equipo **nunca** se cierra escribiendo código.
3. **No inventar.** Comandos, versiones, puertos y rutas se verifican antes de escribirlos.
   Si no se ejecutó, no se escribe como si se hubiera ejecutado.
4. **Se explica mientras se trabaja**, en español: qué archivo, por qué ahí, de qué documento
   sale la decisión.
5. **La bitácora de IA se llena el mismo día.** Ver §7.

---

## 1. Qué es este microservicio

Dueño de: perfiles profesionales, experiencia, educación, habilidades, roles objetivo y la
revisión humana de todo lo que sugiere la IA.

**No** es dueño de: credenciales, pagos, sesiones de entrevista ni el ledger de consumo.

- Estilo: **capas DDD**. Java 21, Spring Boot 4.1.1, PostgreSQL 16, Flyway.
- Base propia: `cameia_perfil`. **Ninguna clave foránea hacia otro contexto.**
- Perfil **no verifica tokens**: eso es del Gateway. **Nunca** entra Firebase Admin SDK aquí.
- Perfil **no calcula cuota**: consume la réplica. Recalcular la verdad de otro contexto es
  antipatrón.

---

## 2. Idioma — `PROPUESTO`, pendiente de aprobación del equipo

**Todo el código en inglés. Todo lo que explica el código, en español.**

| En inglés | En español |
|---|---|
| Paquetes y carpetas | Comentarios y Javadoc |
| Clases, interfaces, métodos, variables, constantes | `@DisplayName` de las pruebas |
| Nombres de las clases de prueba | Mensajes de log |
| Rutas HTTP y variables de entorno | Mensajes de las excepciones |
| | README, documentación y descripciones de OpenAPI |
| | Mensajes de commit y de Pull Request |

### Lo que NO se traduce, pase lo que pase

- **Los códigos de enum y estados.** Son el contrato con Frontend y con Entrevista:
  `PENDING`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`, `MANUAL`, `AI_SUGGESTED`, `AI_EDITED`.
- **Los nombres de tablas y columnas**, que van en español `snake_case` porque salen del DDL:
  `contexto_profesional`, `nombre_perfil`, `resumen_profesional`, `experiencia_laboral`,
  `educacion`, `habilidad_perfil`, `rol_objetivo`.

Por eso la clase va en inglés y la tabla en español, con el mapeo explícito:

```java
@Entity
@Table(name = "contexto_profesional")
public class ProfessionalProfileEntity { }
```

### Regla de mitigación, obligatoria

El C4 y el glosario nombran los conceptos **en español**. Cada clase de dominio lleva un Javadoc
que dice de qué término sale, para no perder la trazabilidad con la arquitectura:

```java
/** Perfil Profesional (C4 «Clases JPA - Perfil Profesional» y glosario §6.2). */
public class ProfessionalProfile { }
```

### Tabla de equivalencias glosario → código

Se mantiene actualizada. Si aparece un concepto nuevo, se agrega aquí **antes** de usarlo,
para que dos personas no lo traduzcan distinto.

| Glosario / C4 (español) | Código (inglés) |
|---|---|
| Perfil Profesional | `ProfessionalProfile` |
| Hoja de Vida (CV) | `Resume` |
| Experiencia Laboral | `WorkExperience` |
| Educación | `Education` |
| Habilidad | `Skill` |
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

---

## 3. Estructura — sale del diagrama de paquetes, hoja "Paquetes - Perfil Profesional"

```text
co.edu.unicauca.cameia.perfil
├── presentation
│   ├── controller      adaptadores de entrada HTTP
│   ├── dto             request/response del contrato público
│   └── advice          manejo de errores HTTP
├── application
│   ├── service         casos de uso: orquestación y transacción
│   └── command         objetos de entrada de los casos de uso
├── domain
│   ├── model           agregados, entidades, objetos de valor, enums
│   ├── service         servicios de dominio
│   ├── policy          políticas de dominio
│   ├── port            interfaces que el dominio define y NO implementa
│   ├── event           eventos de dominio
│   └── exception       excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity      modelo JPA — NO es el modelo de dominio
    │   └── repository  Spring Data + adaptadores de los puertos
    ├── messaging
    │   ├── consumer    @RabbitListener        (vacío: RabbitMQ es POSTERIOR)
    │   ├── publisher   RabbitTemplate         (vacío)
    │   └── payload     contratos versionados  (vacío)
    ├── ia              adaptadores de LLM     (vacío: Sprint 2)
    └── config          configuración de Spring
```

**Perfil NO tiene `infrastructure/client`.** Verificado contra las cuatro hojas del diagrama:
Cuentas, Entrevista y Empleo lo tienen; Perfil no.

**No existe `persistence/mapper`** y no se crea sin decisión del equipo: no aparece en ninguna
hoja del diagrama. Ver §5.3.

### Reglas de dependencia — las verifica `ArquitecturaTest`, no la buena voluntad

```text
presentation ──> application ──> domain
                      │
                      └────────> infrastructure ──> domain
```

1. `domain` no importa nada de `presentation`, `application` ni `infrastructure`.
2. `domain` no importa `org.springframework..`, `jakarta.persistence..`, `com.rabbitmq..` ni
   `com.google..`.
3. `infrastructure` depende de `domain`, nunca al revés.
4. `presentation` no importa `infrastructure`.
5. Sin dependencias circulares entre paquetes.

---

## 4. Dónde va cada cosa

| Lo que estás escribiendo | Va en |
|---|---|
| Recibe un HTTP y devuelve JSON | `presentation.controller` |
| Valida formato de entrada (`@NotBlank`, tamaño) | `presentation.dto` |
| Coordina el caso de uso, abre transacción | `application.service` |
| Regla que siempre debe cumplirse dentro del agregado | `domain.model` |
| Regla de negocio que no pertenece a una sola entidad | `domain.service` |
| Decisión de permitir o bloquear según una política | `domain.policy` |
| Interfaz que el dominio necesita y otro implementa | `domain.port` |
| Habla con PostgreSQL | `infrastructure.persistence` |
| Habla con RabbitMQ | `infrastructure.messaging` |
| Habla con Gemini o Kimi | `infrastructure.ia` |
| Configura beans, colas, timeouts | `infrastructure.config` |

---

## 5. Nombres

### 5.1 Sufijo por capa

| Capa | Patrón | Ejemplo |
|---|---|---|
| `presentation.controller` | `<Concept>Controller` | `ProfileController` |
| `presentation.dto` | `<UseCase>Request` / `<Concept>Response` | `CreateProfileRequest`, `ProfileResponse` |
| `presentation.advice` | `<Scope>ExceptionHandler` | `ApiExceptionHandler` |
| `application.service` | `<Concept>AppService` | `ProfessionalProfileAppService` |
| `application.command` | `<UseCase>Command` | `FinalizeProfileCommand` |
| `domain.model` | término traducido, **sin sufijo** | `ProfessionalProfile`, `TargetRole` |
| `domain.service` | sustantivo de agente, sin sufijo | `TargetRoleSuggester` |
| `domain.policy` | `<Concept>Policy` / `<Concept>Guard` | `ProvenanceAndReviewPolicy`, `QuotaGuard` |
| `domain.port` | `<Aggregate>Repository` o agente | `ProfessionalProfileRepository`, `TextGenerator` |
| `domain.event` | hecho pasado | `ProfessionalProfileUpdated` |
| `domain.exception` | `<Rule>Exception` | `IncompleteProfileException` |
| `…persistence.entity` | `<Concept>Entity` | `ProfessionalProfileEntity` |
| `…persistence.repository` | `<Entity>JpaRepository` + `<Port>JpaAdapter` | ver §5.3 |
| `…messaging.consumer` | `<Event>Listener` | `AccountDeletedListener` |
| `…messaging.payload` | `<Event>PayloadV<n>` | `ConsumptionRecordedPayloadV1` |
| `…config` | `<Topic>Config` | `OpenApiConfig` |

**Sin abreviaturas.** Sin prefijo `I` en interfaces, sin sufijo `Impl`.

### 5.2 Verbos

| Intención | Forma | Ejemplo |
|---|---|---|
| Cambiar el estado del agregado | verbo imperativo | `finalize()`, `addTargetRole()`, `removeTargetRole()` |
| Preguntar sin cambiar nada | `can…` / `is…` / `has…` | `canFinalize()`, `isComplete()` |
| Calcular o derivar | sustantivo del resultado | `missingFields()`, `activeRoles()` |
| Fábrica estática | `create` / `of` | `ProfessionalProfile.create(...)` |
| Guardia privada que lanza | `require…` | `requireNotLastRole()` |
| Decisión de política | `allows…`, devuelve **enum** | `allowsFinalization(...)` |

Un método `can…` no tiene efectos secundarios. Una política devuelve una **decisión**, no un
`boolean` ni una excepción: un `boolean` pierde el motivo.

### 5.3 Persistencia: son TRES piezas, no una

**Un `JpaRepository` suelto NO es el repositorio del dominio.** Si el caso de uso lo inyecta
directo, el dominio queda acoplado a Spring Data y `ArquitecturaTest` falla.

| # | Pieza | Dónde | Qué es |
|---|---|---|---|
| 1 | **Puerto** | `domain/port/` | Interfaz que escribe el dominio, con su vocabulario. No conoce JPA |
| 2 | **Spring Data** | `infrastructure/persistence/repository/` | `extends JpaRepository<…Entity, UUID>` |
| 3 | **Adaptador** | `infrastructure/persistence/repository/` | Implementa el puerto usando el de Spring Data |

```java
// 1) domain/port/ProfessionalProfileRepository.java — sin una sola importación de Spring ni JPA
public interface ProfessionalProfileRepository {
    Optional<ProfessionalProfile> findById(ProfileId id);
    List<ProfessionalProfile> findCompletedByOwner(FirebaseUid owner);
    ProfessionalProfile save(ProfessionalProfile profile);
}

// 2) infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java
interface ProfessionalProfileJpaRepository
        extends JpaRepository<ProfessionalProfileEntity, UUID> {
    List<ProfessionalProfileEntity> findByFirebaseUidAndStatus(String firebaseUid, String status);
}

// 3) …/ProfessionalProfileRepositoryJpaAdapter.java — la única que conoce las dos orillas
@Repository
class ProfessionalProfileRepositoryJpaAdapter implements ProfessionalProfileRepository {

    private final ProfessionalProfileJpaRepository jpa;

    ProfessionalProfileRepositoryJpaAdapter(ProfessionalProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ProfessionalProfile> findById(ProfileId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    private ProfessionalProfile toDomain(ProfessionalProfileEntity entity) { /* a mano */ }
}
```

- `application.service` inyecta **el puerto**, nunca el adaptador ni el `JpaRepository`.
- El `JpaRepository` y el adaptador son **package-private** cuando se puede.
- El mapeo se escribe **a mano**. Prohibido cualquier framework de mapeo automático que oculte
  la traducción dominio ↔ entidad.
- Mientras no exista carpeta `mapper` aprobada, los métodos `toDomain` / `toEntity` van privados
  dentro del adaptador.

---

## 6. Clean Code — límites que se revisan en el PR

- Método: máximo **20 líneas**; en agregados y objetos de valor, **15**.
- Máximo **3 parámetros**. Del cuarto en adelante, un objeto de valor o un `Command`.
- Máximo **2 niveles de anidamiento**. Se sale temprano con guardias.
- Complejidad ciclomática **≤ 8**. Clase: máximo **200 líneas**.
- Prohibido el parámetro booleano que cambia el comportamiento.
- Objetos de valor **inmutables**: `record` de Java 21.
- **Sin setters públicos en el dominio.** El estado cambia por métodos con nombre de negocio.
- Los agregados no exponen sus colecciones internas: devuelven copia o vista de solo lectura.
- Sin `TODO` sin clave de Jira: `// TODO CM-137: …`.
- Inyección **por constructor**. Prohibido `@Autowired` en campos.
- `@Transactional` **solo** en `application.service`.
- `@ManyToOne` siempre `fetch = LAZY`. `@Enumerated(EnumType.STRING)`, nunca `ORDINAL`.

### Los antipatrones que bloquean un PR en este repositorio

1. Modelo de dominio **anémico**: solo getters y setters.
2. Usar la entidad JPA como modelo de dominio.
3. Anotar el agregado con `@Entity` o cualquier anotación de Spring/JPA.
4. Lógica de negocio en el controlador.
5. Devolver la entidad JPA o el agregado desde el controlador.
6. Clave foránea hacia otro contexto.
7. **Perfil calculando cuota**, en vez de consumir la réplica.
8. `Map<String, Object>` como cuerpo de respuesta o payload de evento.
9. **Convertir un `TBD` en constante.**
10. **Guardar el archivo binario del CV.** Solo `textoExtraido`, nunca el binario.
11. **Persistir un dato generado por IA sin `procedencia` y `estadoRevision`.**
12. Inyectar un `JpaRepository` desde `application`.

---

## 7. Pruebas

| Capa | Tipo | Regla |
|---|---|---|
| `domain` | unitaria pura | **Sin contexto de Spring.** Si lo necesita, no es dominio |
| `application` | unitaria con dobles | Los puertos se sustituyen por mocks |
| `infrastructure.persistence` | integración | Contra **PostgreSQL real, no H2** |
| `presentation` | contrato | `@WebMvcTest` |
| arquitectura | estructural | ArchUnit, en `ArquitecturaTest` |

- Clase: `<Class>Test` unitaria, `<Class>IT` integración. **Nombre en inglés.**
- Método: `<method>_should<Result>_when<Condition>`.
- `@DisplayName` **en español**.
- **Cada HU llega con prueba positiva y negativa de cada regla de negocio que toca.**
- Datos sintéticos o anonimizados, **nunca reales**.

```java
@Test
@DisplayName("Un perfil no pasa a COMPLETED si le faltan los datos mínimos")
void finalize_shouldThrowIncompleteProfileException_whenRequiredFieldsAreMissing() { }
```

Cómo se ejecuta, sin instalar Java ni Maven:

```bash
docker compose run --rm verify
```

---

## 8. Bitácora de IA — OBLIGATORIA

`..\..\Entregables\04092026_01_BitacoraIA_Codigo_E2.xlsx`, hoja **`Bitacora_Codigo_E2_Sofia`**.

**Se llena el mismo día.** Reconstruirla la noche antes de la entrega se nota y se penaliza.

- Se registra el prompt cuyo resultado **tocó el código**: generación, depuración, pruebas,
  refactorización, seguridad, comparación de alternativas, y **los resultados descartados**.
- No se registran conversaciones de documentación ni consultas triviales.
- **La columna K, "Cambios humanos", es obligatoria** cuando la IA generó código. Si queda vacía,
  la fila no vale para la rúbrica.
- El **ID es una fórmula**: no se escribe a mano.
- La hoja `Tabla11_Codigo_E2_Sofia` **no se toca**: se calcula sola.
- Nunca se inventan prompts, commits, pruebas ni resultados.

Todo PR con código asistido por IA lleva `[IA-ASISTIDO]` en el título y declara herramienta y
validación en la descripción.

---

## 9. Lo que está abierto — no cerrar por cuenta propia

| Tema | Estado |
|---|---|
| Nombre de rama y título del PR | 5 fuentes dicen `CA-NNN`, 2 dicen `tipo/CM-NNN` |
| Puerto del servicio | `DEV-IN-05`. 8082 es **PROVISIONAL** |
| Idioma del código | §2 es `PROPUESTO`: contradice los nombres en español del C3 y el C4 |
| `persistence/mapper` | No está en el diagrama. Sin decidir |
| **Perfil no tiene modelo de dominio en el C4** | Solo hay clases JPA. Hay que derivarlo y que arquitectura lo revise **antes de CM-16** |
| Falta el campo `estado` en el C4 | El backlog lo exige: `IN_PROGRESS → IN_REVIEW/COMPLETED` |
| Cómo llega la identidad del usuario | Define si entra `spring-boot-starter-oauth2-resource-server` |
| Nombres de las colas | El C2 se contradice entre hojas |
| `API-TBD-05, 06, 07, 09, 18` | Decisiones de producto que afectan a CM-16..CM-20 |

---

## 10. Las Historias de Usuario de este repositorio

Sprint 1, 21 puntos. **Ninguna está implementada todavía.**

| Jira | HU | Endpoints | Reglas duras |
|---|---|---|---|
| CM-16 | HU-2.2 | `POST /api/v1/profiles` | Revalida límite de perfiles por plan. Crea en `IN_PROGRESS` |
| CM-17 | HU-2.3 | `PATCH /api/v1/profiles/{id}` | Editar un campo `AI_SUGGESTED` lo pasa a `AI_EDITED` |
| CM-18 | HU-2.4 | `PATCH …/experience`, `PATCH …/education` | `ACTUAL`/`FIN_DESCONOCIDO` → `fecha_fin` nula; `FINALIZADA` → no nula |
| CM-19 | HU-2.5 | `PATCH …/skills`, `POST …/finalize` | Finalizar exige campos mínimos **y ≥1 rol objetivo** |
| CM-20 | HU-2.11 | `GET`/`POST …/roles`, `PATCH`/`DELETE …/roles/{id}` | Entre **1 y 5** roles, sin duplicados, **no se elimina el último** |

Más dos lecturas que consume Entrevista:
`GET /api/v1/profiles?status=COMPLETED` y `GET /api/v1/roles/suggestions?q={texto}`.
