# Reglas de código backend — CAMEIA

- **Versión:** 1.3
- **Fecha:** 5 de septiembre de 2026
- **Estado:** propuesta para revisión del equipo; no aprobada todavía
- **Alcance:** repositorios Java 21 / Spring Boot de CAMEIA — `cameia-cuentas`, `cameia-perfil`, `cameia-entrevista`, `cameia-empleo`, `cameia-auditoria` y `cameia-gateway`
- **Fuera de alcance:** `cameia-web` (React) y `cameia-voz` (Python/FastAPI); necesitan su propio documento
- **Derivado de:** `26082026_01_Contexto(C1)`, `27082026_01_Contenedores(C2)`, `29082026_1_Componentes(C3)`, `29082026_1_Clases(C4)`, `29082026_Paquetes.drawio.xml`, `30082026_01_Backlog.xlsx`, `03092026_v3_glosario.md`, `03092026_v1_familias-endpoints-sprint-1.md`, `03092026_v1_estrategia-branching-pull-requests.md`, `03092026_v1_env-example-microservicio-spring.md`
- **Responde a:** `DEV-IN-03` de `03092026_v1_solicitud-insumos-desarrollo-inicio-sprint.md` — "estructura de paquetes/carpetas y convenciones de nombres"

---

## 0. Cómo leer este documento

Cada regla lleva un estado, con el mismo vocabulario que usa el equipo en la solicitud de insumos:

| Estado | Significado |
|---|---|
| **`CONFIRMADO`** | La regla se lee directamente de la arquitectura ya dibujada (C3, C4, paquetes) o de un documento ya aprobado. No es opinión: cambiarla obliga a cambiar el diagrama. |
| **`PROPUESTO`** | Es una decisión técnica que este documento propone porque la arquitectura la exige pero no la nombra. Requiere aprobación del equipo. |
| **`TBD`** | Falta una decisión humana registrada. **Prohibido resolverla escribiendo código.** |

Regla 6 del glosario, sección 12: *ningún concepto marcado `TBD` puede convertirse en constante, criterio de aceptación o afirmación de implementación sin decisión humana*. Este documento la hereda como regla de código.

Este documento está pensado para incorporarse como una sección del `AGENTS.md` de cada repositorio. Cuando se use con un asistente de IA, la sección 3 (paquetes) y la sección 5 (nombres) son las que deben pegarse literalmente en el prompt.

---

## 1. Principio rector

> **El código no inventa vocabulario. Lo toma del glosario.**

`03092026_v3_glosario.md` es la única fuente del lenguaje ubicuo. Una clase, un método, una tabla, un endpoint o un evento se llaman como el glosario llama al concepto, en el contexto que lo posee.

De ahí salen tres consecuencias que son reglas duras:

1. **`CONFIRMADO`** Si un concepto no está en el glosario, no se le pone nombre en el código hasta que entre al glosario. Se abre la discusión, no el `.java`.
2. **`CONFIRMADO`** Si el glosario dice que dos términos no deben confundirse (sección 9 del glosario), el código usa nombres visiblemente distintos. Obligatorio para: `Perfil Profesional` ≠ datos de cuenta; `Rol profesional` ≠ `Rol Objetivo`; `API Gateway` ≠ cliente LLM; reintento técnico ≠ reintento funcional ≠ repregunta; `Feedback` ≠ `Reporte de Evaluación`; repositorio Git ≠ repositorio de dominio.
3. **`CONFIRMADO`** Todo cambio de significado actualiza el glosario **en el mismo PR** que cambia el código.

---

## 2. Idioma del código

Esta es la primera pregunta que aparece al abrir un archivo.

**`PROPUESTO` — Regla del idioma: TODO el código en inglés; TODO lo que explica el código, en español.**

La frontera es simple y no admite matices: si el compilador lo lee, va en inglés. Si lo lee una
persona, va en español.

| Elemento | Idioma | Ejemplo |
|---|---|---|
| Paquetes y carpetas | **inglés** | `domain.model`, `infrastructure.messaging.consumer` |
| Nombre completo de clase | **inglés** | `ProfessionalProfile`, `Resume`, `QuotaGuard` |
| Interfaces y puertos | **inglés** | `ProfessionalProfileRepository`, `TextGenerator` |
| Métodos | **inglés** | `finalize()`, `canFinalize()`, `addTargetRole()` |
| Variables, campos y parámetros | **inglés** | `targetRoles`, `createdAt`, `firebaseUid` |
| Constantes | **inglés** `UPPER_SNAKE` | `MAX_TARGET_ROLES` |
| Nombres de las clases de prueba | **inglés** | `ProfessionalProfileTest` |
| Rutas HTTP | inglés | `/api/v1/profiles` |
| Variables de entorno | inglés `UPPER_SNAKE` | `SPRING_DATASOURCE_URL` |
| Anotaciones y librerías | inglés (no se traducen) | `@RestController`, `findById` |
| — | — | — |
| **Comentarios y Javadoc** | **español** | — |
| **`@DisplayName` de las pruebas** | **español** | `"No finaliza si le faltan datos mínimos"` |
| **Mensajes de log** | **español**, sin datos sensibles | ver sección 8 |
| **Mensajes de las excepciones** | **español** | — |
| **README y documentación** | **español** | — |
| **Descripciones de OpenAPI** | **español** | — |
| **Mensajes de commit y PR** | **español** | — |

**Por qué esta división.** El código lo lee el compilador, las librerías y cualquier
desarrollador; el inglés evita mezclas como `guardarPerfil()` junto a `findById()`. La
explicación la lee el equipo y el docente, y va en español para que se entienda sin fricción.

### 2.1 Lo que esta regla cambia respecto a la arquitectura dibujada — LEER

**`TBD` — Esta regla contradice al C3, al C4 y al diagrama de paquetes, y el equipo tiene que
decidirlo antes de que se escriba la primera clase de dominio.**

Los diagramas nombran las clases **en español**: `SesionEntrevista`, `PerfilProfesional`,
`HojaDeVida`, `GuardiaDeCuota`, `PoliticaDeProcedenciaYRevision`, `RepositorioPerfilProfesional`,
`PlanificadorDeTurnos`. Si el código va en inglés, esos nombres dejan de coincidir con el dibujo.

Consecuencias concretas, para decidir con los ojos abiertos:

1. **Se rompe la trazabilidad literal con el C4.** Hoy se puede señalar `GuardiaDeCuota` en el
   diagrama y encontrar esa clase en el código. Con la regla nueva, hay que traducir mentalmente.
   En el Code Walkthrough eso es una pregunta segura del docente.
2. **El glosario sigue siendo la fuente del significado, no del nombre.** La regla 1 de su §12
   dice que las clases usan el término del contexto propietario, y ese término es español. Se
   cumple traduciendo el concepto, no inventándolo.
3. **Hay que mantener una tabla de equivalencias** glosario ↔ código, o cada quien traducirá
   distinto: `HojaDeVida` puede ser `Resume` o `CurriculumVitae` según quién escriba.

**Regla de mitigación, obligatoria si se adopta el inglés:** cada clase de dominio lleva un
Javadoc en español que nombra el término del glosario y del C4 del que sale.

```java
/** Perfil Profesional (C4 y glosario §6.2). Agregado dueño de la información laboral. */
public class ProfessionalProfile { }
```

### 2.2 Lo que NO cambia

**`CONFIRMADO`** Los códigos de estado y los valores de enumeración **no se traducen ni se
"arreglan"**, porque son el contrato con Frontend y con los otros microservicios. Perfil usa
`PENDING/IN_PROGRESS/IN_REVIEW/COMPLETED` y `MANUAL/AI_SUGGESTED/AI_EDITED`; Entrevista usa
`CONFIGURADA/EN_CURSO/FINALIZADA` en español. Esa mezcla es incómoda pero se copia tal cual.
Cambiarla es una decisión de producto (`API-TBD-09`), no una limpieza de código.

**`CONFIRMADO`** Los nombres de **tablas y columnas siguen en español `snake_case`**:
`contexto_profesional`, `nombre_perfil`, `resumen_profesional`. Salen del DDL entregado y de las
especificaciones técnicas del backlog. Cambiarlos obligaría a reescribir migraciones y a romper
la trazabilidad con el modelo de datos. **La clase va en inglés, la tabla en español**, y el
mapeo se hace explícito:

```java
@Entity
@Table(name = "contexto_profesional")
public class ProfessionalProfileEntity { }
```

**`CONFIRMADO`** Las etiquetas de interfaz pueden llevar tilde; los códigos técnicos nunca.
`SIMULACION` en el enum, "Simulación" en la pantalla.

---

## 3. Estructura de paquetes

CAMEIA tiene **dos estilos de arquitectura** y el diagrama de paquetes es explícito en que la diferencia es deliberada. Antes de crear una carpeta hay que saber en cuál de los dos se está.

### 3.1 Paquete base

**`PROPUESTO`** `co.edu.unicauca.cameia.<contexto>`

| Repositorio | Paquete base |
|---|---|
| `cameia-cuentas` | `co.edu.unicauca.cameia.cuentas` |
| `cameia-perfil` | `co.edu.unicauca.cameia.perfil` |
| `cameia-entrevista` | `co.edu.unicauca.cameia.entrevista` |
| `cameia-empleo` | `co.edu.unicauca.cameia.empleo` |
| `cameia-auditoria` | `co.edu.unicauca.cameia.auditoria` |
| `cameia-gateway` | `co.edu.unicauca.cameia.gateway` |

### 3.2 Estilo A — capas DDD

Aplica a **Cuentas, Perfil Profesional, Entrevista y Auditoría**. Estos contextos tienen invariantes propias, así que tienen dominio.

```text
co.edu.unicauca.cameia.<contexto>
├── presentation
│   ├── controller          // adaptadores de entrada HTTP
│   ├── dto                 // request/response del contrato público
│   └── advice              // manejo de errores HTTP
├── application
│   ├── service             // casos de uso (orquestación, transacción)
│   └── command             // objetos de entrada de los casos de uso
├── domain
│   ├── model               // agregados, entidades, objetos de valor, enums
│   ├── service             // servicios de dominio
│   ├── policy              // políticas de dominio
│   ├── port                // interfaces que el dominio define y NO implementa
│   ├── event               // eventos de dominio
│   └── exception           // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity          // modelo JPA — NO es el modelo de dominio
    │   ├── repository      // Spring Data + adaptadores de los puertos
    │   └── mapper          // dominio <-> entity
    ├── messaging
    │   ├── consumer        // @RabbitListener
    │   ├── publisher       // RabbitTemplate
    │   └── payload         // contratos de mensaje versionados
    ├── client              // WebClient hacia sistemas externos
    ├── ia                  // adaptadores de LLM (solo Perfil y Entrevista)
    └── config              // configuración de Spring
```

**`CONFIRMADO`** Las carpetas vacías se crean igual. Cita literal de la nota del diagrama de paquetes: *"Las carpetas vacías existen a propósito: el sitio está decidido aunque el código aún no exista."* Se sostienen con un `.gitkeep`.

### 3.3 Estilo B — capas clásicas, sin dominio

Aplica **solo a Empleo**. La nota del diagrama justifica la ausencia y hay que respetarla: Empleo es CRUD de ofertas, ingesta externa y un análisis cuyo criterio lo pone el modelo de lenguaje. No tiene invariantes propias, y *"sin invariantes propias, un paquete domain sería una carpeta de clases anémicas, que es peor que no tenerla"*.

```text
co.edu.unicauca.cameia.empleo
├── presentation
│   ├── controller
│   └── advice
├── service
│   ├── service             // servicios Spring con la lógica
│   ├── dto                 // aquí viven los DTO, no en presentation
│   ├── client              // ingesta de portales externos
│   ├── ia                  // guardia de cuota y router LLM
│   ├── messaging           // consumidor y publicador
│   └── config
└── data
    ├── repository
    └── entity              // se usan directamente desde los servicios
```

**`CONFIRMADO`** En Empleo los DTO viven en la capa de servicios, no en `presentation`. Cita: *"con tres capas no hay nada que atravesar"*.

**`PROPUESTO`** Nadie "asciende" Empleo a DDD sin una decisión registrada. Si al implementar aparecen invariantes reales, se abre un ADR; no se crea un paquete `domain` por costumbre.

### 3.4 Reglas de dependencia entre capas

**`CONFIRMADO`** — leídas de las flechas del diagrama de paquetes:

**Estilo A (DDD):**

```text
presentation ──> application ──> domain
                      │
                      └────────> infrastructure ──> domain
```

1. `domain` **no importa nada** de `presentation`, `application` ni `infrastructure`.
2. `domain` **no importa Spring, JPA ni RabbitMQ**. Cita del diagrama: *"El dominio no apunta a ninguna capa, y por eso puede existir sin Spring, sin JPA y sin RabbitMQ."* En la práctica: prohibidos los `import org.springframework.*`, `jakarta.persistence.*`, `com.rabbitmq.*` y `com.google.*` dentro de `domain`.
3. `infrastructure` **depende de** `domain`, nunca al revés. Cita: *"La flecha verde es la firma del estilo."*
4. `application` conoce `infrastructure` solo en ejecución, a través de las interfaces de `domain.port`. En compilación importa el puerto, no el adaptador.
5. `presentation` **no importa** `domain` ni `infrastructure`. Habla con `application` y con sus propios DTO.

**Estilo B (Empleo):** `presentation ──> service ──> data`. Todas las flechas bajan. Ninguna sube.

**`PROPUESTO`** Estas reglas se verifican con una prueba ArchUnit por repositorio (`ArquitecturaTest`), no con revisión visual. Es la única forma de que sobrevivan al sprint 3.

### 3.5 Modularidad: alta cohesión, bajo acoplamiento

"Que esté bien modularizado" suena a criterio de gusto. No lo es: en este proyecto se puede medir, y estas son las medidas.

#### Cohesión — qué debe estar junto

**`PROPUESTO`**

1. **Un paquete = un motivo de cambio.** Si dentro de un paquete hay clases que cambian por razones distintas (una porque cambió una regla de negocio, otra porque cambió el proveedor de IA), ese paquete son dos.
2. **Prueba práctica:** si para entender qué hace una clase hay que abrir más de **3 archivos**, la cohesión está mal. O la clase hace demasiado, o su lógica se fugó a otro lado.
3. **La lógica vive junto al dato que gobierna.** `TurnoEntrevista` sabe si admite reintento porque `MAX_REINTENTOS` es suyo. Sacar esa constante a una clase `Constantes` rompe la cohesión y produce un modelo anémico.
4. **Nada de paquetes por tipo técnico dentro de una capa.** No se crea `domain.model.dtos`, `domain.model.helpers` ni `domain.model.utils`. La capa ya es la división; subdividir por tipo de artefacto dispersa lo que debería estar junto.

#### Acoplamiento — qué NO debe conocerse

El acoplamiento en Java se mide con los `import`. Por eso las reglas de la sección 3.4 son literalmente las reglas de acoplamiento, y por eso se verifican con ArchUnit y no con buena voluntad.

**`CONFIRMADO`**

5. **Entre microservicios el acoplamiento en compilación es cero.** Ningún repositorio importa clases de otro. Se comunican por eventos y por referencias opacas (`firebaseUid`, `idPerfilVersion`). El glosario lo respalda: *"no autorizan claves foráneas, consultas ni transacciones entre bases de contextos diferentes."*
6. **Un contexto no conoce el modelo de otro.** Lo que llega por una cola entra como réplica local con nombre propio (`CuotaPlanReplica`, `PerfilReplica`, `VacanteSnapshot`), no como la clase original del productor.

**`PROPUESTO`**

7. **Prohibido un módulo `cameia-commons` compartido entre microservicios** que contenga tipos de dominio, entidades o enums de negocio. Es el atajo que destruye las fronteras de contexto: el día que dos micros comparten el enum `EstadoCuota`, ya no se pueden desplegar ni versionar por separado. Si alguna vez hace falta compartir algo puramente técnico (un formato de error, un id de correlación), requiere ADR aprobado y **nunca** incluye tipos de negocio.
8. **La dependencia va en un solo sentido.** Si `A` importa `B`, `B` no importa `A`. Una dependencia circular entre paquetes es un defecto, no un detalle: `ArquitecturaTest` la detecta con `slices().should().beFreeOfCycles()`.
9. **Se depende de interfaces, no de implementaciones.** `application` importa `RepositorioPerfilProfesional`, nunca `RepositorioPerfilProfesionalJpaAdapter`.

#### ¿Un módulo Maven o varios?

**`TBD`** Un microservicio DDD se puede empaquetar como un solo módulo Maven o como varios (`domain`, `application`, `infrastructure`), donde el compilador impide físicamente que el dominio vea a Spring.

**Recomendación para este proyecto: un solo módulo por repositorio, con ArchUnit.** El multi-módulo da una garantía más fuerte, pero multiplica los `pom.xml`, complica el build y el CI, y el equipo todavía no tiene CI estable. ArchUnit da el 90% de la garantía al 10% del costo, y la decisión se puede revertir después sin tocar una línea de lógica. Si el equipo prefiere multi-módulo, se decide **antes** de escribir código, no a mitad del sprint.

---

## 4. Un archivo, un lugar: dónde va cada cosa

Antes de crear una clase, esta tabla dice dónde va. Si no encaja en ninguna fila, el diseño está mal, no la tabla.

| Lo que estás escribiendo | Va en | Estilo |
|---|---|---|
| Recibe un HTTP y devuelve JSON | `presentation.controller` | A y B |
| Valida formato de entrada (`@NotBlank`, tamaño) | `presentation.dto` | A y B |
| Coordina el caso de uso, abre transacción, publica evento | `application.service` | A |
| Coordina y además contiene la lógica (no hay dominio) | `service.service` | B |
| Regla que siempre debe cumplirse dentro de un agregado | `domain.model` (dentro del agregado) | A |
| Regla de negocio que no pertenece a una sola entidad | `domain.service` | A |
| Decisión de permitir/bloquear según una política | `domain.policy` | A |
| Interfaz que el dominio necesita y otro implementa | `domain.port` | A |
| Habla con PostgreSQL | `infrastructure.persistence` | A |
| Habla con RabbitMQ | `infrastructure.messaging` | A |
| Habla con Firebase, Wompi, Voz, Job Boards | `infrastructure.client` | A |
| Habla con Gemini o Kimi | `infrastructure.ia` | A |
| Configura beans, colas, timeouts | `infrastructure.config` | A |

---

## 5. Convenciones de nombres

Esta es la sección que responde la duda planteada por el equipo: **sí, el nombre de la clase depende de la capa.** El sufijo dice la capa; el núcleo dice el concepto del glosario.

### 5.1 Regla general

```text
<TérminoDelGlosarioEnEspañol><SufijoTécnicoEnInglés>
```

- Clases e interfaces: `PascalCase`.
- Métodos, campos y variables: `camelCase`.
- Constantes: `UPPER_SNAKE_CASE`.
- Paquetes: `lowercase`, sin guiones bajos, sin plurales inventados.
- **`CONFIRMADO`** Sin abreviaturas: `configuracion`, no `config`; `sesion`, no `ses`; `perfilProfesional`, no `pp`. Los identificadores cortos de los diagramas (`ctrl_ses`, `app_eval`) son etiquetas del dibujo, no nombres de clase.
- **`PROPUESTO`** Sin prefijo `I` en interfaces y sin sufijo `Impl` en implementaciones. Un puerto se llama por lo que hace (`GeneradorDeTexto`); su adaptador, por la tecnología que usa (`GeneradorDeTextoGeminiAdapter`).

### 5.2 Capa `presentation`

| Elemento | Patrón | Ejemplos reales de CAMEIA | Estado |
|---|---|---|---|
| Controlador | `<Concepto>Controller` | `SesionController`, `TurnoController`, `ReporteController`, `CuentaController`, `SuscripcionController`, `HojaDeVidaController`, `PerfilProfesionalController`, `VacanteController` | `PROPUESTO` |
| Controlador de webhook | `<Proveedor>WebhookController` | `WompiWebhookController` | `PROPUESTO` |
| DTO de entrada | `<CasoDeUso>Request` | `IniciarSesionRequest`, `EnviarRespuestaRequest`, `CrearPerfilRequest`, `ActualizarExperienciaRequest` | `PROPUESTO` |
| DTO de salida | `<Concepto>Response` | `SesionResponse`, `TurnoResponse`, `ReporteEvaluacionResponse`, `CatalogoConfiguracionResponse` | `PROPUESTO` |
| DTO anidado | `<Concepto>Dto` | `HabilidadDto`, `HallazgoDto` | `PROPUESTO` |
| Manejador de errores | `<Alcance>ExceptionHandler` | `ApiExceptionHandler` | `PROPUESTO` |
| Cuerpo de error común | `ErrorResponse` | — | `TBD` (`API-TBD-14`: el formato lo definen arquitectura y backend) |

**`CONFIRMADO`** Un controlador **nunca** devuelve una entidad JPA ni un agregado de dominio. Devuelve un `Response`. La razón está en el C4: `ReporteEvaluacion.vistaPara(plan)` recorta el reporte al leer, y ese recorte se pierde si se serializa el agregado completo.

**`CONFIRMADO`** Un controlador no decide ownership, entitlement, cuota ni transiciones de estado. Eso pertenece a `application` y `domain`. El documento de familias de endpoints lo dice del Cliente Web y aplica igual al controlador.

### 5.3 Capa `application`

| Elemento | Patrón | Ejemplos reales de CAMEIA | Estado |
|---|---|---|---|
| Servicio de aplicación | `<Concepto>AppService` | `SesionEntrevistaAppService`, `EvaluacionAppService`, `ProgresoHistoricoAppService`, `CuentaAppService`, `SuscripcionAppService`, `ConsumoLimitesAppService`, `HojaDeVidaAppService`, `PerfilProfesionalAppService`, `RolesObjetivoAppService` | `PROPUESTO` |
| Comando de entrada | `<CasoDeUso>Command` | `ConfigurarSesionCommand`, `AvanzarTurnoCommand`, `AplicarPagoCommand` | `PROPUESTO` |
| Resultado del caso de uso | `<CasoDeUso>Result` | `IniciarSesionResult` | `PROPUESTO` |

**Por qué `AppService` y no `Service`:** para que la palabra `Service` signifique una sola cosa en cada estilo. En los microservicios DDD, `AppService` = capa de aplicación, y los servicios de dominio no llevan sufijo (`PlanificadorDeTurnos`). En Empleo, que no tiene dominio, `Service` = la capa de servicios. Así el nombre solo ya dice en qué arquitectura estás parado.

**`CONFIRMADO`** El servicio de aplicación **no contiene la regla principal**. Recibe la solicitud, carga el agregado, invoca al dominio, persiste y publica. Definición literal del glosario, sección 5. Si un `AppService` tiene un `if` que decide algo de negocio, ese `if` está en la capa equivocada.

**`PROPUESTO`** `@Transactional` vive en `application.service`. Nunca en el controlador, nunca en el repositorio, nunca en el dominio.

### 5.4 Capa `domain`

| Elemento | Patrón | Ejemplos reales de CAMEIA | Estado |
|---|---|---|---|
| Raíz de agregado | término del glosario, sin sufijo | `SesionEntrevista`, `ReporteEvaluacion`, `Cuenta`, `Suscripcion`, `PerfilProfesional`, `HojaDeVida` | `CONFIRMADO` (C4) |
| Entidad dentro del agregado | término del glosario, sin sufijo | `TurnoEntrevista`, `Hallazgo` | `CONFIRMADO` (C4) |
| Objeto de valor | término del glosario, sin sufijo | `ConfiguracionSesion`, `PresupuestoTokens`, `NumeroDeTurnos`, `ResumenProgresivo`, `PuntajeStar`, `ContextoPrompt`, `EvaluacionStar`, `ResultadoMetrica`, `VistaReporte` | `CONFIRMADO` (C4) |
| Réplica de otro contexto | `<Concepto>Snapshot` | `PerfilProfesionalSnapshot`, `VacanteSnapshot`, `CuotaPlan` | `CONFIRMADO` (C4) |
| Servicio de dominio | sustantivo de agente, sin sufijo | `PlanificadorDeTurnos`, `ContextoConversacional`, `EvaluadorDeMetricasYStar`, `CatalogoDePlanes` | `CONFIRMADO` (C4) |
| Política de dominio | `Guardia…` / `Politica…` | `GuardiaDeCuota`, `PoliticaDeCuota`, `PoliticaDeProcedenciaYRevision` | `CONFIRMADO` (C3/C4) |
| Puerto de repositorio | `Repositorio<Agregado>` | `RepositorioSesionEntrevista`, `RepositorioReporteEvaluacion`, `RepositorioCuenta`, `RepositorioSuscripcion`, `RepositorioPerfilProfesional`, `RepositorioHojaDeVida` | `CONFIRMADO` (C4) |
| Puerto de capacidad | sustantivo de agente | `GeneradorDeTexto`, `ProcesadorDeVoz`, `DirectorioDeIdentidad`, `PasarelaDePagos`, `ExtractorDeTexto` | `CONFIRMADO` (C4) |
| Enumeración | término singular | `ModoSesion`, `EstadoSesion`, `TipoPregunta`, `Severidad`, `EstadoCuota`, `DecisionCuota`, `NivelDetalle`, `Tendencia`, `Procedencia`, `EstadoRevision` | `CONFIRMADO` (C4) |
| Evento de dominio | hecho pasado | `SesionFinalizada`, `EvaluacionFinalizada`, `ConsumoRegistrado`, `SuscripcionActualizada`, `CuentaEliminada`, `PerfilProfesionalActualizado`, `RolObjetivoSugerido` | `CONFIRMADO` (glosario §12.4) |
| Excepción de negocio | `<Regla>Exception` | `CuotaAgotadaException`, `TransicionInvalidaException`, `PerfilIncompletoException`, `TurnoYaRespondidoException`, `ReporteNoDisponibleException` | `PROPUESTO` |

**`CONFIRMADO`** Los puertos pertenecen al dominio y la infraestructura los implementa. Regla 8 de la nota del C4: *"El dominio no llama a infraestructura: los puertos son suyos y la infraestructura los implementa."*

**`CONFIRMADO`** Las constantes de negocio viven en la clase que las posee, con el valor exacto del C4:

```java
// TurnoEntrevista
private static final int MAX_REINTENTOS  = 2;
private static final int MAX_REPREGUNTAS = 3;

// NumeroDeTurnos
private static final int MINIMO = 5;
private static final int MAXIMO = 20;

// PresupuestoTokens
private static final BigDecimal UMBRAL_RESUMEN = new BigDecimal("0.70");

// CuotaPlan
private static final BigDecimal UMBRAL_ALERTA = new BigDecimal("0.80");

// ContextoConversacional
private static final int TURNOS_INTEGROS = 3;

// EvaluadorDeMetricasYStar
private static final int UMBRAL_RECURRENCIA = 2;
```

**`TBD`** La cantidad de preguntas planificadas **no se codifica todavía**. `GLO-TBD-01` está abierto: el backlog dice 4–6, la Entrega 1 dice exactamente 6, DevOps mencionó 5–20. `PlanificadorDeTurnos` debe recibir el rango por configuración, no como literal, hasta que Product Owner decida.

### 5.5 Capa `infrastructure`

| Elemento | Patrón | Ejemplos reales de CAMEIA | Estado |
|---|---|---|---|
| Entidad JPA | `<Concepto>Entity` | `SesionEntrevistaEntity`, `TurnoEntrevistaEntity`, `ReporteEvaluacionEntity`, `ResultadoMetricaEntity`, `HallazgoEntity`, `CuentaEntity`, `SuscripcionEntity`, `PagoEntity`, `PerfilProfesionalEntity` | `PROPUESTO` |
| Réplica local (read model) | `<Concepto>ReplicaEntity` | `CuotaPlanReplicaEntity`, `PerfilReplicaEntity`, `VacanteReplicaEntity` | `PROPUESTO` |
| Catálogo | `<Concepto>Entity` | `MetricaEvaluacionEntity`, `PlanServicioEntity`, `HabilidadEntity`, `TaxonomiaHabilidadEntity` | `PROPUESTO` |
| Repositorio Spring Data | `<Entity>JpaRepository` | `SesionEntrevistaJpaRepository`, `CuentaJpaRepository` | `PROPUESTO` |
| Adaptador de puerto | `<Puerto><Tecnología>Adapter` | `RepositorioSesionEntrevistaJpaAdapter`, `GeneradorDeTextoGeminiAdapter`, `GeneradorDeTextoKimiAdapter`, `ProcesadorDeVozHttpAdapter`, `DirectorioDeIdentidadFirebaseAdapter`, `PasarelaDePagosWompiAdapter`, `ExtractorDeTextoTikaAdapter` | `PROPUESTO` |
| Mapper de persistencia | `<Concepto>PersistenceMapper` | `SesionEntrevistaPersistenceMapper` | `PROPUESTO` |
| Mapper de API | `<Concepto>ApiMapper` | `SesionApiMapper` | `PROPUESTO` |
| Cliente HTTP externo | `<Sistema>Client` | `VozClient`, `WompiClient`, `FirebaseAdminClient`, `GeminiClient`, `KimiClient`, `JobBoardsClient` | `PROPUESTO` |
| Router de proveedores LLM | `RouterLlm` | — | `PROPUESTO` (ver `GLO-TBD-04`) |
| Publicador | `<Contexto>EventPublisher` | `EntrevistaEventPublisher`, `CuentasEventPublisher` | `PROPUESTO` |
| Consumidor | `<Evento>Listener` | `PerfilProfesionalActualizadoListener`, `SuscripcionActualizadaListener`, `CuentaEliminadaListener`, `VacanteParaSimulacionListener`, `ConsumoRegistradoListener` | `PROPUESTO` |
| Payload de mensaje | `<Evento>PayloadV<n>` | `ConsumoRegistradoPayloadV1`, `SuscripcionActualizadaPayloadV1` | `PROPUESTO` |
| Configuración | `<Tema>Config` | `RabbitConfig`, `WebClientConfig`, `PersistenceConfig` | `PROPUESTO` |
| Propiedades tipadas | `<Tema>Properties` | `LlmProperties`, `VozProperties`, `WompiProperties` | `PROPUESTO` |

**Por qué el sufijo `Entity` es obligatorio en los microservicios DDD:** el C4 dibuja `SesionEntrevista` dos veces —una como agregado de dominio y otra como clase JPA— y son cosas distintas. Cita literal de la nota del C4: *"estas clases son el modelo de persistencia de la capa de infraestructura, no el modelo de dominio. Las reglas viven en los agregados y políticas del dominio."* Sin el sufijo, ambas se llaman igual, hay que escribir nombres totalmente calificados en los mappers, y tarde o temprano alguien anota el agregado con `@Entity`, que es exactamente lo que la arquitectura prohíbe.

**`PROPUESTO`** En **Empleo** no hay colisión porque no hay dominio: las entidades conservan el nombre limpio del C4 (`OfertaLaboral`, `OfertaExterna`, `AnalisisCompatibilidad`, `BrechaHabilidad`, `RutaMejora`, `PasoRutaMejora`, `RolObjetivoSugerencia`). El sufijo `Entity` **no** se usa allí.

### 5.5.1 Persistencia: son TRES piezas, no una — `CONFIRMADO`

Este es el error más común al implementar la capa de infraestructura, y la regla 8 de la nota del
C4 lo prohíbe explícitamente: *"El dominio no llama a infraestructura: los puertos son suyos y la
infraestructura los implementa."*

**Un `JpaRepository` suelto NO es el repositorio del dominio.** Si el caso de uso inyecta
directamente un `interface X extends JpaRepository`, el dominio queda acoplado a Spring Data y a
JPA, y el `ArquitecturaTest` lo detecta y falla.

Cada agregado necesita tres piezas, cada una en su sitio:

| # | Pieza | Dónde vive | Qué es |
|---|---|---|---|
| 1 | **El puerto** | `domain/port/` | Interfaz que **escribe el dominio**, con su vocabulario. No sabe que existe JPA |
| 2 | **El repositorio Spring Data** | `infrastructure/persistence/repository/` | `interface … extends JpaRepository<…Entity, UUID>`. Lo genera Spring |
| 3 | **El adaptador** | `infrastructure/persistence/repository/` | Implementa el puerto usando el de Spring Data, y traduce entidad ↔ dominio |

```java
// 1) domain/port/ProfessionalProfileRepository.java
//    El dominio define lo que necesita. Ni una sola importación de Spring o de JPA.
public interface ProfessionalProfileRepository {
    Optional<ProfessionalProfile> findById(ProfileId id);
    List<ProfessionalProfile> findCompletedByOwner(FirebaseUid owner);
    ProfessionalProfile save(ProfessionalProfile profile);
}

// 2) infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java
//    Spring Data. Habla de entidades, no de agregados.
interface ProfessionalProfileJpaRepository
        extends JpaRepository<ProfessionalProfileEntity, UUID> {
    List<ProfessionalProfileEntity> findByFirebaseUidAndStatus(String firebaseUid, String status);
}

// 3) infrastructure/persistence/repository/ProfessionalProfileRepositoryJpaAdapter.java
//    La única clase que conoce las dos orillas. Es la que se registra como @Repository.
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
    // toDomain / toEntity: ver 5.5.2
}
```

**Qué se inyecta dónde:**

- `application.service` inyecta **el puerto** (`ProfessionalProfileRepository`), nunca el
  adaptador ni el `JpaRepository`. Es la regla 9 de la §3.5: *"se depende de interfaces, no de
  implementaciones"*.
- El `JpaRepository` y el adaptador son **package-private** cuando se puede (sin `public`): así el
  compilador impide que alguien los use desde otra capa por descuido.

**Por qué no basta con el `JpaRepository`.** Tres razones concretas, no de estilo:

1. El puerto habla el lenguaje del dominio (`findCompletedByOwner`), no el de la tabla
   (`findByFirebaseUidAndStatus`). El caso de uso se lee como el backlog.
2. El día que una consulta deje de ser JPA —una vista materializada, una llamada HTTP, una
   caché— cambia **solo el adaptador**. Ni el dominio ni la aplicación se enteran.
3. Probar `application` no requiere base de datos: se sustituye el puerto por un doble.

### 5.5.2 El mapeo entidad ↔ dominio

**`TBD`** Dónde vive exactamente el mapeo está abierto: el diagrama de paquetes de **ninguno** de
los cuatro microservicios dibuja una carpeta `mapper`, aunque la §3.2 de este documento la lista.
Hasta que el equipo lo resuelva, hay dos opciones válidas y hay que elegir una y ser consistente:

- **(a)** Métodos privados `toDomain` / `toEntity` dentro del propio adaptador. Es lo más simple
  y es lo que no exige crear una carpeta que el diagrama no muestra.
- **(b)** Una clase `<Concepto>PersistenceMapper` en una carpeta `mapper`, si el equipo aprueba
  agregarla al diagrama.

**`CONFIRMADO`** Lo que no está en discusión: **prohibido cualquier framework de mapeo automático**
que oculte la traducción dominio ↔ entidad (§10.4). El mapeo se escribe a mano y se lee.

### 5.6 Nombres de métodos

Cómo se llaman las funciones también depende de la capa, y el C4 ya fijó el estilo del dominio.

#### Dominio — el catálogo real del C4

| Intención | Forma | Ejemplos del C4 |
|---|---|---|
| Cambiar el estado del agregado | verbo imperativo | `iniciar()`, `pausar()`, `reanudar()`, `finalizar()`, `responderTurno()`, `omitirTurno()`, `reintentarTurno()`, `repreguntar()`, `consumirTokens()`, `aplicarResumen()`, `registrarPregunta()` |
| Preguntar sin cambiar nada | `puede…` / `admite…` / `permite…` / `es…` / `esta…` / `requiere…` | `puedeGenerarReporte()`, `admiteReintento()`, `admiteRepregunta()`, `permitePausa()`, `permiteRepregunta()`, `esConductual()`, `esEntreno()`, `esSimulacion()`, `estaRespondido()`, `estaVacio()`, `estaCompleto()`, `estaFueraDeRango()`, `requiereResumen()` |
| Calcular o derivar | sustantivo del resultado | `turnosCompletados()`, `disponibles()`, `desviacion()`, `cumplimiento()`, `tokensEstimados()`, `componenteMasDebil()`, `ultimoValor()`, `variacion()` |
| Crear (fábrica estática) | `crear` / `de` / `generar` / `configurar` / `vacio` | `ConfiguracionSesion.crear(...)`, `NumeroDeTurnos.de(valor)`, `PuntajeStar.de(valor)`, `ReporteEvaluacion.generar(...)`, `SesionEntrevista.configurar(...)`, `ResumenProgresivo.vacio()`, `VacanteSnapshot.deTextoLibre(...)`, `VistaReporte.basica(...)`, `ProgresoHistorico.porRol(...)` |
| Guardia privada que lanza excepción | `exigir…` | `exigirTransicion(destino)`, `exigirModoEntreno()` |
| Decisión de política | verbo `permite…`, devuelve enum | `permiteIniciar(cuota)`, `permiteContinuar(sesion, cuota)`, `permiteIdioma(config, cuota)` → `DecisionCuota` |

**`CONFIRMADO`** Una política devuelve una **decisión**, no un `boolean` y no una excepción. Regla 5 de la nota del C4: *"GuardiaDeCuota solo decide PERMITIR/BLOQUEAR. No recorta ni degrada nada."* Un `boolean` pierde el motivo; una excepción convierte una decisión de negocio en un flujo de error.

**`CONFIRMADO`** Los métodos `puede…` no tienen efectos secundarios. Si un método consulta y además muta, se parte en dos.

#### Puertos

| Intención | Forma | Ejemplos del C4 |
|---|---|---|
| Buscar por identidad | `buscarPorId`, `buscarPor<Campo>` | `buscarPorId(id)`, `buscarPorSesion(id)` |
| Buscar por condición | `<condición>De` | `enCursoDe(usuario)`, `historicoDe(usuario, rol)` |
| Persistir | `guardar` | `guardar(sesion)`, `guardar(reporte)` |
| Capacidad externa | verbo de la capacidad | `generarPregunta(contexto)`, `medirRespuesta(turno, contexto)`, `resumir(turnos, previo)`, `transcribir(audio)`, `prosodiaDe(audio)`, `sintetizar(texto, idioma)` |

**`PROPUESTO`** El puerto se nombra en el lenguaje del dominio, no en el de la tecnología: `guardar`, no `save`; `buscarPorId`, no `findById`. `findById` aparece solo en la interfaz `…JpaRepository`, que es de Spring Data.

**`PROPUESTO`** Un finder que puede no encontrar devuelve `Optional<T>`. Nunca `null`. Una colección vacía se devuelve vacía, nunca `null`. `Optional` solo como tipo de retorno: nunca como campo ni como parámetro.

#### Aplicación

**`PROPUESTO`** El método se llama como el caso de uso del backlog: `configurarSesion`, `iniciarSesion`, `avanzarTurno`, `finalizarSesion`, `generarReporte`, `registrarConsumo`, `aplicarResultadoDePago`, `cancelarRenovacion`, `cargarHojaDeVida`, `finalizarPerfil`. Un `AppService` no tiene métodos `procesar`, `manejar`, `ejecutar` ni `gestionar`: esos verbos no dicen nada.

#### Presentación

**`PROPUESTO`** El método del controlador se llama como la operación HTTP en lenguaje de negocio: `iniciarSesion`, `enviarRespuesta`, `consultarReporte`, `listarPerfiles`. No `get`, `post`, `handleRequest`.

### 5.7 Variables, campos y constantes

**`PROPUESTO`**

1. El nombre dice **qué es**, no qué tipo tiene: `sesion`, no `sesionObj`; `turnos`, no `listaTurnos`.
2. Las colecciones van en plural: `turnos`, `hallazgos`, `resultados`, `habilidades`.
3. Los booleanos se leen como afirmación: `renovacionAutomatica`, `firmaValida`, `obligatoria`, `activo`. Nunca en negativo (`noActivo` está prohibido).
4. Las fechas dicen el hecho, con sufijo `En` para instantes, tal como el C4: `iniciadaEn`, `finalizadaEn`, `creadoEn`, `actualizadoEn`, `confirmadoEn`, `recibidoEn`, `publicadaEn`.
5. Las referencias opacas a otro contexto se nombran para que se note que lo son: `firebaseUid`, `idPerfilVersion`, `idOfertaVersion`, `idSugerenciaEmpleo`. **`CONFIRMADO`** No son claves foráneas y no se declaran como `@ManyToOne`; la nota del C4 lo dice explícitamente: *"son ids sueltos, NO claves foráneas"*.
6. Nada de `data`, `info`, `temp`, `aux`, `flag`, `obj`, `list`, `manager`, `helper`, `util` sin un sustantivo delante.
7. Constantes en `UPPER_SNAKE_CASE`, y solo dentro de la clase dueña del concepto.

### 5.8 Nombres fuera de Java

| Artefacto | Convención | Ejemplo | Estado |
|---|---|---|---|
| Ruta HTTP | `/api/v1/<recurso-en-inglés-plural>` | `/api/v1/interviews`, `/api/v1/profiles` | `CONFIRMADO` (familias de endpoints) |
| Nombre del recurso | `kebab-case` | `target-roles`, `voice-service` | `PROPUESTO` |
| Parámetro de ruta | una sola convención en todo CAMEIA | — | `TBD` (`API-TBD-03`) |
| Tabla | `snake_case` singular en español | `cuenta`, `contexto_profesional`, `sesion_entrevista` | `CONFIRMADO` (DDL) |
| Columna | `snake_case` en español | `tokens_consumidos`, `fin_ciclo` | `CONFIRMADO` (DDL) |
| Clave foránea intra-contexto | `<tabla>_id` | `sesion_id`, `reporte_id` | `PROPUESTO` |
| Evento / cola | hecho pasado, `kebab-case` | `perfil-profesional-actualizado`, `consumo-registrado`, `entitlements-actualizados`, `vacante-para-simulacion`, `evaluacion-finalizada`, `cuenta-eliminada` | `CONFIRMADO` (C2) |
| Versión del evento | sufijo `.v1` en el contrato, `V1` en la clase | `consumo-registrado.v1` | `PROPUESTO` |
| Variable de entorno | `UPPER_SNAKE_CASE` | `SPRING_DATASOURCE_URL`, `LLM_TIMEOUT_MS` | `CONFIRMADO` (plantilla `.env.example`) |
| Rama | `CA-<numero>-<descripcion-kebab-case>` | `CA-102-login-usuario` | `CONFIRMADO` (branching) |
| Commit | `tipo: descripción` (Conventional Commits) | `feat: agregar validación de ofertas laborales` | `CONFIRMADO` (branching) |
| Pull Request | `CA-NNN \| tipo(scope): resultado` | — | `CONFIRMADO` (branching) |
| Documento del equipo | `DDMMAAAA_vN_nombre-kebab-case.md` | `03092026_v1_reglas-codigo-backend-cameia.md` | `CONFIRMADO` (paquete dev) |
| Migración de BD | pendiente de decidir la herramienta | — | `TBD` (`DEV-IN-07`) |

**`CONFIRMADO`** El punto final que aparece al final de varias rutas del Excel del backlog es puntuación del documento, no parte del endpoint (`API-TBD-04`). No copiar rutas del Excel al código sin quitarlo.

---

## 6. Clean Code — reglas duras y verificables

Son numéricas a propósito: una regla que no se puede medir no se puede revisar en un PR.

### 6.1 Funciones

**`PROPUESTO`**

1. Un método hace **una cosa**. Si al describirlo hay que usar "y", son dos métodos.
2. Máximo **20 líneas** de cuerpo. En agregados y objetos de valor, máximo **15**.
3. Máximo **3 parámetros**. A partir del cuarto se pasa un objeto de valor o un `Command`. `SesionEntrevista.configurar(perfil, vacante, rol, config)` ya usa esta salida: la configuración completa entra como un solo objeto.
4. **Prohibido el parámetro booleano** que cambia el comportamiento. `generarReporte(true)` no se entiende; se parte en dos métodos con nombre.
5. Máximo **2 niveles de anidamiento**. Se sale temprano con guardias (`exigir…`) en vez de anidar `if`.
6. Complejidad ciclomática ≤ **8** por método.
7. Un método o devuelve un valor o cambia el estado. No las dos cosas.

### 6.2 Clases

**`PROPUESTO`**

1. Máximo **200 líneas** por clase. Una clase que crece más ya tiene dentro otra clase.
2. Una clase pública por archivo.
3. Sin estado estático mutable. Nunca.
4. Los objetos de valor son inmutables: `record` de Java 21, o campos `final` sin setters. El C4 lo marca explícitamente con `«value object - inmutable»`.
5. Los agregados no exponen sus colecciones internas: `List<TurnoEntrevista> turnos()` devuelve una copia o una vista de solo lectura. Si el llamador puede hacer `sesion.turnos().add(...)`, la invariante ya está rota.
6. Sin setters públicos en el dominio. El estado cambia por métodos con nombre de negocio (`responderTurno`), no por `setEstado`.

### 6.3 Comentarios

**`PROPUESTO`**

1. El comentario explica **por qué**, nunca **qué**. Si hace falta explicar qué hace, el nombre está mal.
2. Prohibido el código comentado. Para eso está Git.
3. Prohibido `TODO` sin clave de Jira: `// TODO CA-137: …`. El C4 ya tiene dos TODO legítimos declarados (máquinas de estado por modo y `PuntajeGlobal`); esos se referencian, no se repiten.
4. Javadoc obligatorio en los **puertos** de `domain.port` y en los **payloads** de eventos: son contratos que otro equipo implementa o consume.
5. Cada invariante de negocio lleva una línea que cita su origen:

```java
// Regla 2 (C4 - dominio Entrevista): terminar antes de agotar los turnos NO genera reporte.
public boolean puedeGenerarReporte() { ... }
```

### 6.4 Formato

**`PROPUESTO`** Indentación 4 espacios, línea máxima 120 caracteres, imports sin comodín, orden de miembros: constantes → campos → constructores → fábricas estáticas → métodos públicos → métodos privados. El formateador se define en `DEV-IN-02` y se ejecuta en CI; hasta que exista, la regla se revisa a mano.

---

## 7. Manejo de errores

**`PROPUESTO`**

1. Las excepciones de negocio viven en `domain.exception`, extienden una raíz común por contexto (`EntrevistaException`, `CuentasException`) y son *unchecked*.
2. El nombre dice la regla violada: `CuotaAgotadaException`, no `BusinessException("cuota")`.
3. **Prohibido capturar y callar.** Un `catch` que no relanza, no traduce y no registra es un defecto.
4. **Prohibido `catch (Exception e)`** salvo en el `@RestControllerAdvice` y en el consumidor de RabbitMQ, que son las fronteras del sistema.
5. La traducción a HTTP ocurre **solo** en `presentation.advice`. El dominio no conoce códigos HTTP.
6. Un fallo del proveedor de IA no se propaga como error genérico: `HU-5.6` exige manejo controlado sin perder el progreso del usuario. El adaptador traduce timeout/5xx a una excepción de dominio con significado.
7. **`CONFIRMADO`** La cuota agotada **no corta una sesión en curso**. Regla 4 de la nota del C4: *"Cuota agotada bloquea INICIAR, nunca corta una sesión en curso: permiteContinuar() siempre PERMITIR."* Implementarlo como excepción a mitad de sesión es un defecto funcional, no una decisión de diseño.

### 7.1 Formato común de error — propuesta para `API-TBD-14`

`API-TBD-14` dice que *"no existen esquemas de request, response ni error común"* y que sin eso *"Backend, Gateway y Web no pueden integrarse de manera independiente"*. Es un hueco real y bloquea a Frontend.

**`PROPUESTO`** Adoptar **RFC 7807 — Problem Details for HTTP APIs**, en vez de inventar un formato propio.

Razones concretas:

1. Es el estándar de la industria para errores HTTP y ya existe: no hay que diseñarlo ni discutirlo campo por campo.
2. **Spring Boot lo trae de fábrica.** Se activa con `spring.mvc.problemdetails.enabled=true` y `ProblemDetail` es una clase del framework: no hay que escribir el DTO.
3. Frontend puede usar librerías que ya lo entienden, en vez de mapear un formato hecho a mano.
4. Cubre por sí solo casi todo lo que la solicitud de insumos pide en su §4.2: código HTTP, mensaje seguro, detalle de validación, ruta de la operación.

Forma del cuerpo:

```json
{
  "type": "https://cameia.dev/errors/perfil-incompleto",
  "title": "Perfil incompleto",
  "status": 422,
  "detail": "El perfil no tiene los datos mínimos para finalizarse",
  "instance": "/api/v1/profiles/3f2a.../finalize",
  "codigoCameia": "PERFIL_INCOMPLETO",
  "correlationId": "b7c1...",
  "errores": [ { "campo": "resumen", "mensaje": "obligatorio" } ]
}
```

Los tres últimos campos son extensiones propias, que el RFC permite explícitamente. `codigoCameia` es el código estable que pide la solicitud de insumos; `correlationId` amarra el error con el log.

**`CONFIRMADO`** El `detail` es **seguro para el cliente**: nunca lleva stack traces, consultas SQL, nombres de tablas ni datos de otro usuario. La solicitud de insumos lo exige: *"tratamiento de errores internos para no exponer stack traces"*.

**`PROPUESTO`** Un `type` por familia de error, no uno por excepción. Y el mismo formato en los seis repositorios Java: si cada microservicio inventa el suyo, el Gateway tiene que traducir y volvemos al problema original.

---

## 8. Logging, trazabilidad y datos sensibles

**`CONFIRMADO`** — heredado de las reglas de seguridad de la propuesta `.env.example`, sección 6:

1. **Nunca** se registran: contraseñas, tokens, llaves, credenciales, el texto del CV, transcripciones, audio, PII ni valores de variables secretas.
2. `firebaseUid` se registra; nombre y correo, no.
3. Todo log lleva identificador de correlación.
4. Niveles: `ERROR` solo cuando alguien debe actuar; `WARN` para degradación esperada (fallback entre Gemini y Kimi); `INFO` para hitos de caso de uso; `DEBUG` para desarrollo.
5. Langfuse es observabilidad técnica. **`CONFIRMADO`** No es la fuente contable del consumo: *"Langfuse es observabilidad y NO participa del cálculo de la cuota"* (nota del C4 de Cuentas). Nunca leer un contador desde Langfuse.
6. No se registra el prompt completo en el log de la aplicación; para eso está Langfuse, con su propio control de acceso.

### 8.1 Log estructurado e identificador de correlación

La regla 3 dice "todo log lleva identificador de correlación", pero no dice cómo. Sin eso, cuando un usuario reporte un error habrá que buscarlo a mano en los logs de seis servicios.

**`PROPUESTO`**

1. **Log en JSON**, no en texto plano. Un log en texto se lee bien en la consola de desarrollo y es inservible para buscar en Cloud Run.
2. **Un `correlationId` por solicitud**, generado en el API Gateway y propagado a los microservicios en una cabecera. Si una solicitud llega sin él, el microservicio genera uno en vez de rechazarla.
3. Se guarda en el **MDC** de SLF4J al entrar la petición y se limpia al salir, para que aparezca en todas las líneas sin pasarlo por parámetro.
4. **El mismo `correlationId` viaja en los eventos de RabbitMQ**, en la cabecera del mensaje. Es lo que permite seguir una operación desde el clic del usuario hasta el consumidor de otro contexto.
5. Ese `correlationId` es el que va en el cuerpo de error de §7.1: el usuario reporta un código, y con ese código se encuentra la traza completa.

**`TBD`** Formato exacto, librería y retención: es decisión de DevOps junto con arquitectura. Lo que este documento fija es que **el `correlationId` existe desde el primer día**, porque agregarlo después obliga a tocar todos los controladores y todos los consumidores.

---

## 9. SOLID aplicado a CAMEIA

No como teoría: cada principio con el caso real donde ya se cumple o se rompe en este proyecto.

### S — Responsabilidad única

**Caso CAMEIA:** `GuardiaDeCuota` decide `PERMITIR` o `BLOQUEAR` y nada más. Regla 5 de la nota del C4: *"No recorta ni degrada nada."* Si alguien le agrega "y además baja el nivel de detalle del reporte", la política pasa a tener dos motivos para cambiar (política de cuota y política de plan) y deja de ser testeable de forma aislada.

**Regla:** el recorte por plan es de `ReporteEvaluacion.vistaPara(plan)`. La cuota es de `GuardiaDeCuota`. No se mezclan.

### O — Abierto/cerrado

**Caso CAMEIA:** `RouterLlm` selecciona proveedor y modelo entre Gemini y Kimi. Agregar un tercer proveedor debe significar **una clase nueva** que implementa `GeneradorDeTexto`, no un `if` nuevo en `SesionEntrevistaAppService`.

**Regla:** si agregar un proveedor, un tipo de pregunta o una métrica obliga a tocar un caso de uso, el diseño está cerrado donde debería estar abierto.

### L — Sustitución de Liskov

**Caso CAMEIA:** `GeneradorDeTextoGeminiAdapter` y `GeneradorDeTextoKimiAdapter` deben ser intercambiables. El fallback entre proveedores que describe el C3 solo funciona si ambos cumplen el mismo contrato.

**Regla:** toda implementación de un puerto respeta el mismo contrato: mismos tipos de excepción, mismas precondiciones, nunca `null`, nunca `UnsupportedOperationException`. Si un adaptador no puede cumplir una operación, el puerto está mal partido.

### I — Segregación de interfaces

**Caso CAMEIA:** el C4 define `GeneradorDeTexto` y `ProcesadorDeVoz` **por separado**, aunque los dos sean "IA". Entrevista necesita las dos; Perfil solo la primera.

**Regla:** prohibido crear un puerto `IaPort` o `ServicioExternoPort` que agrupe capacidades no relacionadas. Un puerto = una capacidad.

### D — Inversión de dependencias

**Caso CAMEIA:** es literalmente la regla 8 de la nota del C4 y la flecha verde del diagrama de paquetes.

**Regla:** `application` y `domain` dependen de `domain.port`. La inyección del adaptador la hace Spring en `infrastructure.config`. Ninguna clase de `domain` menciona a Gemini, Firebase, Wompi, Rabbit o JPA por su nombre.

---

## 10. Patrones de diseño

### 10.1 Los que ya decidió la arquitectura — se usan, no se discuten

| Patrón | Dónde vive en CAMEIA | Evidencia |
|---|---|---|
| Puertos y Adaptadores (hexagonal) | `domain.port` + `infrastructure.*Adapter` | Diagrama de paquetes, C4 |
| Repository | `RepositorioSesionEntrevista`, `RepositorioCuenta`, … | C4 |
| Agregado / Objeto de Valor / Servicio de dominio / Política | todo `domain` | C4, glosario §5 |
| Fábrica estática | `configurar()`, `crear()`, `de()`, `generar()`, `vacio()` | C4 |
| Strategy | `RouterLlm` — selección de proveedor y modelo | C3: *"Spring Service - Strategy"* |
| Adapter | `VozClient`, `WompiClient`, `FirebaseAdminClient` | C3 |
| Publisher / Subscriber | `EntrevistaEventPublisher`, `*Listener` | C2, C3 |
| Idempotencia por mensaje (Inbox) | `idMensaje` único en `EventoConsumo`; `idEventoWompi` único en `AuditoriaPago` | C4, glosario §5 |
| Outbox | evento y cambio de negocio en la misma transacción local | glosario §5 |
| DTO + Mapper | `presentation.dto` + `infrastructure.persistence.mapper` | Diagrama de paquetes |
| Read Model / proyección | `CuotaPlanReplica`, `PerfilReplica`, `VacanteReplica` | C4, glosario §5 |

### 10.2 State — decidido pero **aplazado**

**`CONFIRMADO`** El C3 dibuja `Máquina de Estado Entreno` y `Máquina de Estado Simulación`, pero el C4 las marca como **fuera de alcance en esta versión**. Cita literal:

> *"TODO - FUERA DE ALCANCE EN ESTA VERSION. Máquina de Estado Modo Entreno y Máquina de Estado Modo Simulación (patrón State). El agregado ya valida las transiciones de EstadoSesion […]; lo que falta modelar es la VARIACIÓN POR MODO. Mientras tanto esa diferencia vive en los métodos de ConfiguracionSesion (permitePausa, permiteRepregunta) y en exigirModoEntreno() de SesionEntrevista."*

**Regla:** no se implementa el patrón State todavía. La diferencia entre modos vive donde el C4 dice que vive. Introducirlo antes es trabajo que el equipo decidió no hacer.

### 10.3 Cuándo se introduce un patrón nuevo

**`PROPUESTO`** Un patrón entra al código solo si:

- **(a)** ya está en el C3, el C4 o el diagrama de paquetes; **o**
- **(b)** resuelve un problema que ya apareció —no uno previsto— y queda registrado en un ADR enlazado desde el PR.

Ningún patrón entra "por si acaso". El costo de un patrón innecesario lo paga quien lee el código después, y en este equipo esa persona rota cada sprint.

### 10.4 Prohibidos sin ADR aprobado

Singleton hecho a mano (Spring ya gestiona el ciclo de vida), Service Locator, herencia de más de dos niveles, `BaseEntity` con lógica, clases `Utils` genéricas, reflexión para saltarse el encapsulamiento, y cualquier framework de mapeo automático que oculte la traducción dominio ↔ entidad.

---

## 11. Antipatrones que bloquean un PR

**`CONFIRMADO`** Cada uno contradice una decisión escrita de la arquitectura:

1. **Modelo de dominio anémico** en Cuentas, Perfil, Entrevista o Auditoría. La nota del diagrama de paquetes lo llama *"peor que no tenerla"*. Si el agregado solo tiene getters y setters, la lógica se fugó al `AppService`.
2. **Usar la entidad JPA como modelo de dominio.** Las notas del C4 lo prohíben en los cuatro microservicios DDD.
3. **Anotar el agregado con `@Entity`** o cualquier anotación de Spring/JPA.
4. **Lógica de negocio en el controlador.**
5. **Clave foránea entre contextos** o `join` entre bases. El glosario es explícito: *"no autorizan claves foráneas, consultas ni transacciones entre bases de contextos diferentes."*
6. **Recalcular la verdad de otro contexto.** Entrevista no recalcula el plan; consume la réplica de la Cola 3. Perfil no calcula cuota. *"Otros contextos solo mantienen referencias o proyecciones."*
7. **Devolver la entidad JPA o el agregado desde el controlador.**
8. **`Map<String, Object>` como payload de evento o cuerpo de respuesta.**
9. **Consumidor no idempotente.** El C3 exige idempotencia por identificador de mensaje en los cuatro consumidores.
10. **Convertir un `TBD` en constante.** Especialmente la cantidad de preguntas (`GLO-TBD-01`) y el multiplicador Premium (`HU-9.2`, marcado *"no debe estar hardcodeado"*).
11. **Guardar el archivo binario del CV.** El C4 es tajante: *"HojaDeVida guarda solo textoExtraido, nunca el binario."*
12. **Persistir un dato generado por IA sin `procedencia` y `estadoRevision`.** HE-02, criterio 6: nada propuesto por IA se da por válido sin revisión humana explícita.

---

## 12. Pruebas

**`PROPUESTO`**

| Capa | Tipo | Herramienta | Regla |
|---|---|---|---|
| `domain` | unitaria pura | JUnit 5 + AssertJ | Sin contexto de Spring, sin mocks de framework. Si necesita Spring para probarse, no es dominio. |
| `application` | unitaria con dobles | JUnit 5 + Mockito | Los puertos se sustituyen por dobles. |
| `infrastructure.persistence` | integración | `@DataJpaTest` (+ Testcontainers, `PROPUESTO`) | Contra PostgreSQL real, no H2: se usan `jsonb` y `@Lob`. |
| `presentation` | contrato | `@WebMvcTest` | Verifica códigos HTTP y forma del JSON. |
| arquitectura | estructural | ArchUnit | Verifica las reglas de la sección 3.4. |

**`PROPUESTO`** Nombres:

- Clase: `<ClaseBajoPrueba>Test` (unitaria), `<ClaseBajoPrueba>IT` (integración).
- Método: `<metodo>_deberia<Resultado>_cuando<Condicion>`.
- `@DisplayName` en español legible.

```java
@Test
@DisplayName("No genera reporte si la sesión terminó antes de agotar los turnos")
void puedeGenerarReporte_deberiaSerFalso_cuandoQuedanTurnosPendientes() { ... }
```

**`PROPUESTO`** Las **ocho reglas de negocio** que enumera la nota del C4 del dominio de Entrevista tienen prueba unitaria propia, cada una con su caso positivo y su caso negativo. Esa lista es el mínimo, no la meta.

**`CONFIRMADO`** No hay umbral de cobertura obligatorio todavía. El documento de branching lo dice: *"Los status checks, despliegues, firmas, análisis de código y cobertura se hacen obligatorios solo después de existir y superar pruebas positivas y negativas."*

---

## 13. Reglas específicas de Spring, JPA y RabbitMQ

**`PROPUESTO`**

**Spring**

1. Inyección **por constructor**, siempre. Prohibido `@Autowired` en campos.
2. `@Transactional` solo en `application.service` (o en `service.service` en Empleo).
3. `@Value` disperso está prohibido: la configuración entra por `@ConfigurationProperties` en una clase `…Properties`.
4. **`CONFIRMADO`** El arranque falla de forma comprensible si falta una variable obligatoria (regla 5 de la propuesta `.env.example`).
5. Todo microservicio expone `health` e `info`, tal como fija la plantilla `.env.example`.
6. **`PROPUESTO`** `health` se separa en **liveness** y **readiness**, no uno solo:

   ```yaml
   management:
     endpoint:
       health:
         probes:
           enabled: true
   ```

   Quedan disponibles `/actuator/health/liveness` y `/actuator/health/readiness`. La diferencia importa en el despliegue objetivo (Cloud Run): *liveness* responde si el proceso está vivo, y si falla se reinicia el contenedor; *readiness* responde si el servicio puede atender, e incluye la base de datos. Con un solo `health` que consulte PostgreSQL, una caída momentánea de la base hace que la plataforma **reinicie** el servicio en vez de solo dejar de mandarle tráfico. Es una línea de configuración ahora y un incidente confuso después.
7. **`PROPUESTO`** Exponer solo lo necesario en Actuator. La plantilla ya lo hace bien con `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info`: no agregar `env`, `beans`, `configprops` ni `heapdump` en ambientes desplegados, porque filtran configuración interna.

**JPA**

1. `spring.jpa.hibernate.ddl-auto=validate`. **`CONFIRMADO`** por la plantilla. Nunca `update` ni `create`.
2. Todas las relaciones `@ManyToOne` van `fetch = LAZY`, como en todo el C4.
3. `@Enumerated(EnumType.STRING)`, nunca `ORDINAL`. El C4 lo anota en cada enumeración.
4. Los `@Column(unique = true)` del C4 son las claves de idempotencia (`idMensaje`, `idEventoWompi`, `referenciaWompi`, `firebaseUid`, `correo`) y no se relajan.
5. Sin lógica de negocio dentro de una entidad JPA.

**RabbitMQ**

1. Un consumidor por evento, con nombre `<Evento>Listener`.
2. **`CONFIRMADO`** Idempotencia obligatoria: antes de aplicar, se verifica el identificador de mensaje contra la tabla de mensajes procesados.
3. El payload se versiona (`…PayloadV1`) y no viaja información sensible que el consumidor no necesite.
4. Un evento se nombra como hecho pasado y declara productor, consumidores y versión (glosario §12.4).
5. Un consumidor **no** ejecuta reglas del contexto productor: actualiza su réplica local y nada más.

---

## 14. Checklist de Pull Request

Se copia como plantilla de PR del repositorio.

```text
## Trazabilidad
- [ ] Rama con formato CA-<numero>-<descripcion-kebab-case>
- [ ] Título: CA-NNN | tipo(scope): resultado
- [ ] Cuerpo enlaza la tarea de Jira
- [ ] HU/CA que cubre este cambio: ______

## Lenguaje
- [ ] Todo término nuevo existe en 03092026_v3_glosario.md
- [ ] Si cambió un significado, el glosario se actualizó en este mismo PR
- [ ] No se resolvió ningún TBD escribiendo código

## Arquitectura
- [ ] Cada clase está en la capa que le corresponde (sección 4)
- [ ] domain no importa Spring, JPA ni RabbitMQ
- [ ] infrastructure depende de domain, no al revés
- [ ] No hay FK ni consultas entre bases de contextos distintos
- [ ] Ningún controlador devuelve entidad JPA ni agregado

## Modularidad
- [ ] Ningún import de clases de otro microservicio
- [ ] Sin dependencias circulares entre paquetes
- [ ] Se depende de la interfaz del puerto, no del adaptador
- [ ] La lógica quedó junto al dato que gobierna, no en una clase Constantes o Utils
- [ ] Ningún paquete nuevo divide por tipo técnico (dtos, helpers, utils) dentro de una capa

## Nombres
- [ ] Sufijos según la sección 5 (Controller / AppService / Entity / Adapter / Client)
- [ ] Métodos de dominio siguen el catálogo de verbos de la sección 5.6
- [ ] Sin abreviaturas y sin nombres genéricos (data, info, manager, helper)

## Clean Code
- [ ] Métodos <= 20 líneas, <= 3 parámetros, <= 2 niveles de anidamiento
- [ ] Sin parámetros booleanos de comportamiento
- [ ] Sin código comentado y sin TODO sin clave de Jira
- [ ] Sin números mágicos: las constantes viven en la clase dueña

## Seguridad y datos
- [ ] Ningún log con PII, CV, transcripciones, tokens o secretos
- [ ] .env.example y README actualizados si cambiaron variables
- [ ] Ningún valor real de variable secreta en el diff

## Pruebas
- [ ] Prueba positiva y negativa por cada regla de negocio tocada
- [ ] Las pruebas de dominio no levantan contexto de Spring
- [ ] Suite verde localmente, con el comando declarado en el README
```

---

## 15. Lo que este documento NO cierra

Estas decisiones siguen abiertas y **no se resuelven programando**. Este documento las hereda tal como están.

| ID | Tema | Quién decide |
|---|---|---|
| `GLO-TBD-01` | Cantidad de preguntas planificadas (4–6 / exactamente 6 / 5–20) | Product Owner + Entrevista + Tester |
| `GLO-TBD-02` | Catálogo definitivo de estados de Sesión | Entrevista + arquitectura + Tester |
| `GLO-TBD-04` | Nombre del cliente/proveedor LLM frente a "API Gateway" | Arquitectura + Entrevista |
| `GLO-TBD-05` | Separación entre límite nominal (Cuentas) y cuota (Auditoría) | Product Owner + Cuentas + Auditoría |
| `GLO-TBD-08` | Escalas, SLA y umbrales de IA y voz | Product Owner + Voz + Entrevista + Tester |
| `GLO-TBD-09` | Proveedores y versiones de modelos de IA | Arquitectura + Product Owner + DevOps |
| `API-TBD-01` | `/users` frente a `/accounts` | Arquitectura + Cuentas |
| `API-TBD-02` | Rutas singulares (`/session`, `/turn`) frente a plurales | Arquitectura |
| `API-TBD-03` | Convención única de parámetros de ruta | Arquitectura |
| `API-TBD-09` | `COMPLETE` frente a `COMPLETED` | Product Owner + Perfil |
| `API-TBD-10` | `idioma` frente a `idioma_id` | Entrevista + arquitectura |
| `API-TBD-11` | `feedback_turno` frente a `feedback_respuesta` | Entrevista + arquitectura |
| `API-TBD-14` | Formato común de errores HTTP — **este documento propone RFC 7807 en §7.1** | Arquitectura + Backend |
| — | Log estructurado, librería y retención (§8.1) | DevOps + arquitectura |
| — | Cabecera y generación del `correlationId` (§8.1) | Arquitectura + Gateway |
| — | Activar CI: build, pruebas, lint, análisis estático, detección de secretos y cobertura. Hoy la plantilla de PR los exige pero no existe la automatización | DevOps |
| `DEV-IN-01` | Maven o Gradle, y versiones exactas | Cada responsable de repositorio |
| `DEV-IN-07` | Herramienta de migraciones (Flyway / Liquibase / otra) | Backend + DevOps |
| — | Un módulo Maven por repositorio o multi-módulo (§3.5) | Arquitectura + Backend |
| — | Uso de Lombok: permitido, restringido o prohibido | Equipo Backend |
| — | Formateador y linter (Spotless, Checkstyle u otro) | Backend + DevOps |

---

## 16. Criterio de terminado de este documento

- [x] Estructura de paquetes derivada del diagrama de paquetes, para los dos estilos
- [x] Reglas de dependencia entre capas extraídas de las flechas del diagrama
- [x] Criterios medibles de cohesión y acoplamiento, con la decisión de módulo Maven abierta
- [x] Convenciones de nombres por capa, con ejemplos reales tomados del C3 y el C4
- [x] Catálogo de verbos de métodos derivado del C4
- [x] Reglas Clean Code verificables y numéricas
- [x] SOLID con un caso real de CAMEIA por principio
- [x] Patrones separados en "ya decididos", "aplazados" y "requieren ADR"
- [x] Antipatrones anclados a citas de la arquitectura
- [x] Checklist de PR alineado con la estrategia de branching
- [x] Decisiones TBD listadas sin resolver
- [ ] Backend revisa y aprueba las reglas marcadas `PROPUESTO`
- [ ] Arquitectura confirma paquete base y sufijos de clase
- [ ] Se decide Lombok, formateador y herramienta de migraciones
- [ ] Se instala como `AGENTS.md` / `docs/` en cada repositorio mediante rama `CA-*` y PR
- [ ] Se agrega la prueba ArchUnit que verifica la sección 3.4

---

## 17. Historial

| Versión | Fecha | Cambio | Estado |
|---|---|---|---|
| 1.0 | 3-sep-2026 | Versión inicial. Deriva estructura de paquetes, convenciones de nombres por capa, reglas Clean Code, SOLID y catálogo de patrones a partir de C1–C4, diagrama de paquetes, backlog, glosario v3, familias de endpoints y estrategia de branching. Responde a `DEV-IN-03`. | Propuesta pendiente de revisión del equipo |
| 1.1 | 3-sep-2026 | Agrega §3.5 Modularidad: criterios medibles de cohesión y acoplamiento, prohibición de `cameia-commons` con tipos de negocio, regla de no circularidad y decisión abierta de módulo único frente a multi-módulo Maven. Amplía el checklist de PR con un bloque de modularidad. | Propuesta pendiente de revisión del equipo |
| 1.3 | 5-sep-2026 | Cambia la regla de idioma de §2: todo el código en inglés y toda la explicación en español, con §2.1 registrando que eso contradice los nombres en español del C3, el C4 y el diagrama de paquetes, y la regla de mitigación por Javadoc. Agrega §5.5.1, que separa puerto, JpaRepository y adaptador como tres piezas distintas, y §5.5.2 sobre dónde vive el mapeo. | §2 PROPUESTO y §2.1 TBD: requieren decisión del equipo |
| 1.2 | 3-sep-2026 | Incorpora tres recomendaciones de industria que los documentos del equipo dejan abiertas: §7.1 RFC 7807 como respuesta concreta a `API-TBD-14`; §8.1 log estructurado y `correlationId` propagado por HTTP y RabbitMQ; §13 separación de liveness/readiness y exposición mínima de Actuator. Registra en §15 las decisiones que estas propuestas requieren. | Propuesta pendiente de revisión del equipo |
