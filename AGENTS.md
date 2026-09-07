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

## 0.1 Spec Driven Development — flujo obligatorio para toda HU nueva

> Adoptado el 07/09/2026. Guía completa: [`docs/sdd.md`](docs/sdd.md).
> Fuente: curso SDD de mouredev (github.com/mouredev/hello-sdd).

**Ninguna HU se implementa sin spec previo.** El ciclo SDD tiene 7 fases:

| Fase | Artefacto | Regla |
|---|---|---|
| 1 · Spec | `specs/NNN-nombre/spec.md` | Requisitos EARS — qué hace y por qué, sin decir cómo |
| 2 · Clarification | notas en el spec | Resolver ambigüedades antes de diseñar |
| 3 · Plan | `specs/NNN-nombre/plan.md` | Diseño técnico: capas, clases, migraciones, decisiones |
| 4 · Tasks | `specs/NNN-nombre/tasks.md` | Checkboxes de 20-30 min, trazados al REQ-N del spec |
| 5 · Implementation | código Java | Una tarea a la vez; marcar `[x]` antes de la siguiente |
| 6 · Validation | suite de pruebas | Cada REQ-N del spec tiene al menos una prueba que lo cubre |
| 7 · Loop | — | Si el scope cambia, se actualiza el spec (Spec Anchored) |

### Notación EARS — los requisitos van así

```
El sistema [acción]
Cuando [actor] [acción], el sistema [respuesta]
Mientras [estado], el sistema [comportamiento]
Si [condición no deseada], el sistema [manejo]
Donde se proporcione [opción], el sistema [comportamiento]
```

### Estructura de carpetas

```text
specs/
  NNN-nombre-hu/
    spec.md    ← requisitos EARS
    plan.md    ← diseño técnico
    tasks.md   ← checkboxes
```

### Checklist de entrada a una rama CM-NNN-...

- [ ] `spec.md` escrito y aclarado (sin TBDs sin resolver)
- [ ] `plan.md` aprobado
- [ ] `tasks.md` con checkboxes trazados al spec
- [ ] Ninguna clase nueva en el plan que no esté en §3.1 o justificada

> CM-16..CM-20 se implementaron antes de adoptar SDD. A partir de **CM-21**
> el ciclo es obligatorio desde la Fase 1.

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

## 2. Idioma — DECIDIDO el 5 de septiembre de 2026

**Todo el código en inglés. Todo lo que explica el código, en español.**

Lo decidió Ana Sofía, responsable de este microservicio, y **aplica desde la primera clase**.
Coincide con la §2 del documento de reglas del equipo.

> **Lo que hay que saber al defenderlo, porque en el Code Walkthrough van a preguntarlo:**
> el C3, el C4 y el diagrama de paquetes nombran los conceptos **en español**
> (`PerfilProfesional`, `RepositorioPerfilProfesional`, `GuardiaDeCuota`), y la §5.4 de las reglas
> del equipo los marca `CONFIRMADO` contra el C4. Es decir, esta decisión se aparta del dibujo, y
> la propia §2.1 de las reglas lo advierte y pide que el equipo lo ratifique.
>
> **Por eso la regla de mitigación de más abajo no es opcional**: es lo único que mantiene la
> trazabilidad entre el diagrama y el código. Sin el Javadoc y sin la tabla de equivalencias, la
> decisión es indefendible; con ellos, se sostiene.

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

## 3.1 Los 17 componentes del C3, uno por uno

Hoja **"C3 - Microservicio de Perfil Profesional"** de `29082026_1_Componentes(C3).txt`.
**No se crea ninguna clase que no salga de esta tabla.** Si hace falta una que no está, se
pregunta antes de escribirla.

### Presentación — Controllers

| Componente del C3 | Tecnología | Clase | Sprint |
|---|---|---|---|
| Controller Perfil Profesional | Spring Controller | `ProfileController` | **1** |
| Controller Hoja de Vida | Spring Controller | `ResumeController` | 2 |

*Controller Perfil Profesional:* "Gestiona los Perfiles Profesionales y la revisión humana de
todo lo sugerido por IA."
*Controller Hoja de Vida:* "Recibe la carga del CV y expone las versiones ya procesadas."

### Aplicación — Casos de uso

| Componente del C3 | Tecnología | Clase | Sprint |
|---|---|---|---|
| Aplicacion Perfil Profesional | Spring Service - Aplicación | `ProfessionalProfileAppService` | **1** |
| Aplicacion Roles Objetivo | Spring Service - Aplicación | `TargetRoleAppService` | **1** |
| Aplicacion Hoja de Vida | Spring Service - Aplicación | `ResumeAppService` | 2 |

*Aplicacion Perfil Profesional:* "Coordina crear, editar, versionar y revisar el Perfil
Profesional, y pedir sugerencias de redacción sobre sus textos libres."
*Aplicacion Roles Objetivo:* "Coordina proponer roles objetivo, aceptar o editar los sugeridos y
registrar los que el usuario agrega a mano." → **En Sprint 1 solo la parte manual: CM-20 dice
"sin invocación a IA en esta HU".**

### Dominio — Agregados, servicios y políticas de negocio

| Componente del C3 | Tipo | Clase | Sprint |
|---|---|---|---|
| Perfil Profesional (Agregado) | Modelo de Dominio | `ProfessionalProfile` | **1** |
| Politica de Procedencia y Revision | Política de Dominio | `ProvenanceAndReviewPolicy` | **1** parcial |
| Hoja de Vida (Agregado) | Modelo de Dominio | `Resume` | 2 |
| Guardia de Cuota | Política de Dominio | `QuotaGuard` | 2 |

Las invariantes que el C3 le atribuye a cada uno — **esto es lo que hay que implementar, no lo
que se le ocurra a nadie**:

- **Perfil Profesional (Agregado):** "Invariantes del Perfil Profesional y de los roles objetivo
  que contiene: completitud, versión vigente y cuántos perfiles admite el plan del usuario."
  → Los roles objetivo **viven dentro del agregado**, no son un agregado aparte. Por eso la regla
  de 1 a 5 roles y la de no eliminar el último se validan aquí, no en el `AppService`.
- **Politica de Procedencia y Revision:** "Decide la procedencia de cada dato y su estado de
  revisión: nada propuesto por IA se da por válido hasta que el usuario lo confirma o lo edita."
  → En Sprint 1 no hay IA, así que toda procedencia es `MANUAL`. **Los campos `procedencia` y
  `estadoRevision` sí se persisten desde el primer día**, porque el antipatrón 11 lo exige y
  porque HU-2.3 ya define la transición `AI_SUGGESTED → AI_EDITED`.
- **Guardia de Cuota:** "Verifica el límite operativo de tokens del plan antes de cada llamada a
  IA y decide si la operación procede." → **Sprint 2.** En Sprint 1 no hay ninguna llamada a IA
  que guardar. Devuelve una **decisión** (enum), nunca un `boolean` ni una excepción.
- **Hoja de Vida (Agregado):** "Invariantes de la Hoja de Vida: formato y tamaño admitidos,
  numeración de versiones y la regla de conservar solo el texto extraído, nunca el archivo
  original."

### Infraestructura

| Componente del C3 | Tecnología | Clase | Sprint |
|---|---|---|---|
| Repository Perfil Profesional | Spring Repository | ver §5.3 — son varias clases | **1** |
| Publicador de Eventos | Spring AMQP RabbitTemplate | `ProfileEventPublisher` | POSTERIOR |
| Consumidor de Eventos | Spring AMQP RabbitListener | un `<Event>Listener` por evento | POSTERIOR |
| Extractor de Texto de CV | Apache Tika / PDFBox | `TextExtractorTikaAdapter` | 2 |
| Extractor Estructurado IA | Spring AI | `StructuredExtractorAiAdapter` | 2 |
| Sugeridor de Roles Objetivo | Spring AI | `TargetRoleSuggesterAiAdapter` | 2 |
| Asistente de Redaccion | Spring AI | `WritingAssistantAiAdapter` | 2 |
| Router LLM | Spring Service - Strategy | `LlmRouter` | 2 |

**Dos citas del C3 que cambian cómo se escribe el código:**

1. *Repository Perfil Profesional:* **"Implementa los repositorios del dominio sobre
   JPA/Hibernate: perfiles, versiones de CV y la réplica local de plan y cuota."**
   El verbo es **"implementa"** y el sustantivo va en **plural**. Es el respaldo literal del
   patrón de tres piezas de §5.3: los puertos son del dominio y la infraestructura los
   implementa. Y son varios puertos, no uno: perfiles, versiones de CV y la réplica de cuota.
2. *Sugeridor de Roles Objetivo:* **"Es el camino del MVP: cuando exista Empleo, la sugerencia
   llegará por la Cola 4 y este adaptador se retira."**
   Nace con fecha de caducidad. Cuando se implemente (Sprint 2), detrás de un puerto, para que
   retirarlo sea borrar una clase y no refactorizar el caso de uso.

*Consumidor de Eventos:* "Recibe el plan vigente, los roles objetivo sugeridos por Empleo y el
aviso de cuenta eliminada. **Idempotente por identificador de mensaje.**" → La idempotencia no es
opcional; es antipatrón 9 no tenerla.

### Lo que el C3 conecta hacia afuera

| Destino | Tipo | Relación |
|---|---|---|
| API Gateway | Java 21 / Spring Boot | **Entra** a los dos controllers. Verifica el ID token de Firebase y propaga los custom claims |
| DB Perfil | PostgreSQL | "Perfiles Profesionales, versiones de CV en texto y réplica local de plan y cuota" |
| Cola 1 — Perfil Profesional Actualizado | RabbitMQ | **Publica** |
| Cola 2 — Consumo Registrado | RabbitMQ | **Publica** |
| Cola 3 — Suscripcion Actualizada | RabbitMQ | **Consume** |
| Cola 4 — Rol Objetivo Sugerido | RabbitMQ | **Consume** |
| Cola 7 — Cuenta Eliminada | RabbitMQ | **Consume** |
| Google Gemini API / Kimi API | Sistemas externos | Solo a través del Router LLM. Sprint 2 |
| Langfuse | Autohospedado | Trazabilidad de prompts y tokens. **Nunca es la fuente del consumo** |

**Perfil NO se conecta con las Colas 5 ni 6.** Y **no habla directamente con Gemini ni con Kimi**:
siempre a través del Router LLM, que es lo que permite el respaldo entre proveedores.

### Lo que este microservicio NO tiene, y se nota comparando con el C3

- **No hay `infrastructure/client`.** El C3 de Perfil no dibuja ningún cliente HTTP hacia
  Firebase, Wompi ni Voz. Los únicos externos son los proveedores de LLM, y esos van en `ia`.
- **No hay servicio de dominio.** El C3 de Perfil dibuja agregados y políticas, pero ningún
  componente de tipo "Servicio de Dominio" como el `PlanificadorDeTurnos` de Entrevista. La
  carpeta `domain/service` existe porque el diagrama de paquetes la muestra, pero **hoy no hay
  ningún componente que la ocupe**: si aparece la necesidad, se pregunta antes.

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

## 6.1 Prácticas de Spring Boot — cómo se aplican **aquí**

> Fuente: guía oficial de buenas prácticas de Spring Boot (`guidelines.md`, 14 puntos).
> **No se copian tal cual.** Cada punto está contrastado contra las reglas del equipo, y tres de
> ellos chocan con decisiones que el equipo ya tomó: esos **no se adoptan** hasta que el equipo
> responda, y están en el §9.

| # | Práctica | Estado en este repositorio |
|---|---|---|
| 1 | Inyección por constructor | **Ya es regla** (§6). Se refuerza: campos `final` |
| 2 | `package-private` en componentes Spring | **Se adopta** y se amplía a controllers y `@Configuration` |
| 3 | `@ConfigurationProperties` tipadas y validadas | **Se adopta.** Nada de `@Value` disperso |
| 4 | Fronteras de transacción explícitas | **Se adopta.** Novedad: `readOnly = true` en lecturas |
| 5 | `open-in-view=false` | **Ya cumplido** en `application.yml` |
| 6 | Separar capa web de persistencia | **Ya es regla** (antipatrones 5 y 2) |
| 7 | Principios REST | **Parcial.** El versionado nativo **no se adopta**: ver §9 |
| 8 | Objetos `Command` | **Ya es regla** (`application.command`, §3) |
| 9 | Manejo centralizado de excepciones | **Ya cumplido.** `ApiExceptionHandler` existe |
| 10 | Actuator mínimo | **Ya cumplido.** Solo `health` e `info` |
| 11 | i18n con `ResourceBundles` | **No se adopta.** Choca con la regla de idioma: ver §9 |
| 12 | Testcontainers | **No se adopta todavía.** Ver §9 |
| 13 | Puerto aleatorio en pruebas de integración | **Se adopta.** Obligatorio, y aquí importa más de lo normal |
| 14 | Logging con SLF4J y guardas | **Se adopta** |

### Lo que cambia respecto a lo que ya estaba escrito

**1 · Dependencias `final`.** La regla ya decía "inyección por constructor". Se completa: las
dependencias obligatorias se declaran `private final` y entran por el único constructor. Con un
solo constructor **no se escribe `@Autowired`**, Spring lo detecta.

```java
@Service
class ProfessionalProfileAppService {

    private final ProfessionalProfileRepository profiles;   // el PUERTO, no el JpaRepository

    ProfessionalProfileAppService(ProfessionalProfileRepository profiles) {
        this.profiles = profiles;
    }
}
```

**2 · `package-private` por defecto.** No todo tiene que ser `public`. Se declaran sin modificador
los controllers y sus métodos, las clases `@Configuration`, los métodos `@Bean`, los `@Service`
y —esto ya estaba en §5.3— el `JpaRepository` y su adaptador. Motivo concreto: el compilador
impide que alguien llame a un controlador desde otra capa, y así ArchUnit tiene menos trabajo.

Lo que **sí** es `public`: el modelo de dominio, los puertos, los DTO y los `Command`, porque
cruzan la frontera de paquete a propósito.

**3 · Configuración tipada.** Cuando aparezca la primera propiedad propia del servicio —el rango
de roles objetivo, un timeout de LLM— **no** se lee con `@Value`. Se agrupa bajo un prefijo y se
enlaza con `@ConfigurationProperties` y validación, para que el servicio **no arranque** si la
configuración está mal, en vez de fallar en la primera petición:

```java
@ConfigurationProperties(prefix = "cameia.perfil")
@Validated
record PerfilProperties(@Min(1) @Max(5) int maxTargetRoles) { }
```

Ojo con esto: si el valor sale de un `TBD` del equipo, **no se codifica el número** ni siquiera
como valor por defecto de la propiedad. Es el antipatrón 9. La propiedad se declara sin default y
el servicio falla al arrancar hasta que alguien decida. Eso es correcto: es un aviso, no un bug.

**4 · Transacciones.** La regla decía "`@Transactional` solo en `application.service`". Se
completa con la distinción que faltaba:

- Métodos que **solo leen** → `@Transactional(readOnly = true)`. No es cosmético: le dice a
  Hibernate que no haga *dirty checking*, y permite enrutar a una réplica de lectura si algún día
  existe.
- Métodos que **modifican** → `@Transactional`.
- La transacción cubre lo mínimo indispensable. **Ninguna llamada a un LLM ni a RabbitMQ dentro
  de una transacción abierta:** una llamada a Gemini que tarde 30 s mantiene retenida la conexión
  de base todo ese tiempo. Cuando llegue el Sprint 2, esto es lo primero que se rompe.

**7 · REST — lo que sí se adopta.** `ResponseEntity<T>` con el código explícito (`201 Created` en
`POST /profiles`, no `200`); paginación obligatoria en `GET /api/v1/profiles?status=COMPLETED`,
que hoy no la tiene definida y es una colección sin cota; un objeto JSON en la raíz de toda
respuesta, nunca un array suelto; y nombres de propiedad JSON **consistentes** — el contrato del
equipo usa `snake_case` (`nombre_perfil`, `profile_id`), así que `snake_case`.

**13 · Puerto aleatorio en pruebas.** Toda prueba de integración que levante el servidor va con
`@SpringBootTest(webEnvironment = RANDOM_PORT)`. Aquí no es una precaución teórica: el puerto de
este servicio es **`PROVISIONAL`** (§9), y una prueba clavada al 8082 se rompe el día que el
equipo asigne el puerto real, o cuando dos personas corran la suite a la vez.

**14 · Logging.** SLF4J siempre; `System.out.println` **nunca**. Mensajes en español (§2). Y la
regla que más importa en este microservicio concreto:

> **Nunca se registra el contenido de un CV, un resumen profesional, una experiencia laboral ni
> ningún dato del perfil.** Son datos personales de una persona real. Se registra el
> identificador y el resultado, no el contenido.

```java
// MAL: vuelca datos personales al log
log.debug("Perfil actualizado: {}", profile);

// BIEN: identificador y hecho
log.debug("Perfil {} actualizado, {} roles objetivo", profileId, roleCount);
```

Las llamadas costosas en `DEBUG`/`TRACE` van con guarda o con `Supplier`:

```java
if (log.isDebugEnabled()) {
    log.debug("Estado detallado: {}", calcularDetalleCostoso());
}
```

### Detalle heredado que conviene corregir

La guía §9 recomienda **RFC 9457**, que es la norma que **dejó obsoleta a la RFC 7807** (mismo
media type `application/problem+json`, mismo comportamiento). El `README.md`, el
`application.yml` y el Javadoc de `ApiExceptionHandler` todavía citan la 7807. No cambia una línea
de código —solo la referencia— pero citar una RFC obsoleta en el Code Walkthrough es una pregunta
fácil de evitar.

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
- Toda prueba que levante el servidor va con **puerto aleatorio**, nunca clavada al 8082:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

- La prueba de integración de persistencia corre contra el **PostgreSQL de `docker-compose`**,
  que es real. Testcontainers levantaría ese mismo PostgreSQL desde la propia prueba y es lo que
  recomienda la guía de Spring Boot; **no se ha adoptado** y la razón está en el §9. Lo que no
  cambia en ninguno de los dos casos: **H2 está prohibido.**

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

`..\..\Entregables\05092026_01_BitacoraIA_Codigo_E2.xlsx`, hoja **`Bitacora_Codigo_E2_Sofia`**.

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
| ~~Nombre de rama y título del PR~~ | **RESUELTO el 5-sep-2026: `CM-<numero>-<descripcion>`** (PR #2 del equipo). La estrategia de branching y las reglas de código siguen diciendo `CA`: están desactualizadas |
| Puerto del servicio | `DEV-IN-05`. 8082 es **PROVISIONAL** |
| ~~Idioma del código~~ | **DECIDIDO el 5-sep-2026 por Ana Sofía: código en inglés, explicación en español** (§2). Sigue apartándose de los nombres en español del C3 y el C4, así que el Javadoc de origen y la tabla de equivalencias son **obligatorios**. Pendiente de ratificación del equipo |
| `persistence/mapper` | No está en el diagrama. Sin decidir |
| **Perfil no tiene modelo de dominio en el C4** | Solo hay clases JPA. Hay que derivarlo y que arquitectura lo revise **antes de CM-16** |
| Falta el campo `estado` en el C4 | El backlog lo exige: `IN_PROGRESS → IN_REVIEW/COMPLETED` |
| Cómo llega la identidad del usuario | Define si entra `spring-boot-starter-oauth2-resource-server` |
| Nombres de las colas | El C2 se contradice entre hojas |
| `API-TBD-05, 06, 07, 09, 18` | Decisiones de producto que afectan a CM-16..CM-20 |
| **Versionado nativo de API** (Spring 7) | La guía de Spring Boot recomienda el atributo `version` de `@RequestMapping` sobre el `/v1/` en la URL. **Verificado: existe en el `spring-web` 7.0.9 de este proyecto.** Pero el contrato del equipo fija `/api/v1/profiles`, y lo consumen Gateway, Web y Entrevista. **Cambiarlo es decisión de arquitectura, no de este repositorio** |
| **Testcontainers** | La guía lo pide; el `pom.xml` no lo tiene, a propósito. Hoy las pruebas de integración usarían el PostgreSQL de `docker-compose`. Adoptarlo agrega una dependencia de prueba y hace que la suite corra sin `compose` levantado. **Preguntar si el equipo lo quiere en los 6 repositorios o en ninguno**: media suite con Testcontainers y media sin él es lo peor de los dos mundos |
| **i18n con `ResourceBundles`** | La guía pide externalizar los textos de cara al usuario. Choca con §2, que manda los mensajes de excepción **en español** dentro del código. Hoy el producto es monolingüe en español y no hay HU de idioma en el backlog: adoptarlo sería trabajo sin requisito. **Se reevalúa si aparece una HU de internacionalización** |

---

## 10. Ramas, commits y Pull Requests

> **Recordatorio del §0: nada de esto se ejecuta sin autorización expresa de Ana Sofía.**
> Lo de abajo es la convención cuando llegue el momento, no un permiso para publicar.

### 10.1 Flujo

```text
CA-NNN-descripcion ── PR + Squash ──> develop ── PR + Merge commit ──> main
```

- Las ramas de trabajo **nacen siempre desde `develop`** actualizado, son de corta duración y se
  eliminan después de integrarse.
- `develop` recibe **Squash and merge**. `main` recibe **Merge commit**.
- **Ni `main` ni `develop` reciben push directo.** Tampoco para arreglar la integración.
- Todo cambio entra por Pull Request con **revisión de una persona distinta del autor**.

### 10.2 Nombre de rama — RESUELTO el 5 de septiembre de 2026

```text
CM-<numero>-<descripcion-kebab-case>
```

Sin prefijo de tipo. Ejemplo real de este repositorio: `CM-102-base-tecnica`.

Estuvo en disputa: la estrategia de branching, `github.txt` y las reglas de código §5.8 y §14
decían `CA-<numero>`, mientras el `CONTRIBUTING.md` y la plantilla de PR decían `CM-NNN`.
**El equipo lo unificó en `CM`**: Paula actualizó el `README.md` en el
[PR #2](https://github.com/Forkthec/cameia-perfil/pull/2) — *"Update branch naming conventions in
README"*, commit `22aa21d` — y ese README es el de `develop`, que es la rama de integración.

> **Aviso para quien lea las fuentes antiguas:** la estrategia de branching y las reglas de
> código **todavía dicen `CA`** y no se han actualizado. No son la decisión vigente. Si alguien
> las cita para pedir un cambio de nombre, la respuesta es el PR #2.

**Lección, y por eso está escrita aquí:** esta convención se verificó en su día contra una copia
local del remoto que tenía doce horas de antigüedad, y por eso se llegó a la conclusión contraria.
Antes de nombrar una rama o abrir un PR, **`git fetch` primero**: las convenciones del equipo
cambian en `develop`, no en los documentos de arquitectura.

### 10.3 Commits

Conventional Commits, **en español**, y **no requieren llevar la clave de Jira**.

```text
feat: agregar validacion de roles objetivo
fix: corregir la fecha fin de una experiencia finalizada
test: agregar pruebas de la transicion a COMPLETED
docs: completar el README con los comandos reales
chore: actualizar configuracion de Docker
refactor: extraer la politica de procedencia del caso de uso
build: agregar Flyway al pom
ci: configurar el pipeline de integracion
```

Tipos admitidos: `feat`, `fix`, `test`, `docs`, `refactor`, `perf`, `build`, `ci`, `chore`.

### 10.4 Título del Pull Request

```text
CM-NNN | tipo(scope): resultado
```

Y si hubo código asistido por IA, **el título lleva `[IA-ASISTIDO]`**. Eso no está en disputa y
no es opcional.

La plantilla de PR de este repositorio ya usaba `CM-NNN`, y la decisión del equipo del 5 de
septiembre (§10.2) confirma esa línea. Las reglas de código todavía dicen `CA-NNN | …`: están
desactualizadas.

### 10.5 La plantilla de PR es obligatoria

Está en `.github/pull_request_template.md` y se llena entera. Sus secciones obligatorias:
resumen y trazabilidad, criterios y evidencia, validaciones ejecutadas, impacto y riesgo,
seguridad y privacidad, y **uso de IA y revisión humana**.

De esa última sección, el campo que decide la nota es **"qué se corrigió o rechazó y por qué"**:
es donde se ve si hubo revisión real o si se aceptó lo primero que salió. Se redacta desde la
bitácora del §8, no de memoria.

Formato exigido en la descripción cuando hay código asistido por IA:

```text
[IA-ASISTIDO] Descripción de la función
Herramienta: <herramienta utilizada>
Validación: <pruebas realizadas> + revisión manual + revisión de seguridad
```

### 10.6 Antes de abrir el PR

- [ ] `docker compose run --rm verify` en verde, **con la salida real pegada en el PR**
- [ ] Ningún término nuevo que no esté en el glosario
- [ ] Ningún `TBD` resuelto escribiendo código
- [ ] `domain` no importa Spring, JPA ni RabbitMQ; `ArquitecturaTest` lo confirma
- [ ] Ningún controlador devuelve entidad JPA ni agregado
- [ ] Ningún `JpaRepository` inyectado desde `application`
- [ ] Prueba positiva y negativa por cada regla de negocio tocada
- [ ] Ningún log con PII, CV, transcripciones, tokens ni secretos
- [ ] Ningún valor real de variable secreta en el diff
- [ ] `README.md` y `.env.example` actualizados si cambiaron variables o comandos
- [ ] La bitácora del §8 tiene la fila correspondiente, con la columna K llena
- [ ] El título lleva `[IA-ASISTIDO]` si hubo IA
- [ ] Revisión pedida a **alguien distinto de la autora**

---

## 11. Las Historias de Usuario de este repositorio

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
