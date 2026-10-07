# CM-283 · Alinear los documentos y el estándar de calidad — cameia-perfil

| Campo | Valor |
|---|---|
| Jira | CM-283 (Tarea, Sprint 2) |
| Repositorio | cameia-perfil |
| Rama | `CM-283-alinear-estandar` |
| Base | `origin/develop` @ `42320d9` (5 de octubre de 2026) |
| Estado | Pendiente de aprobación |
| Documentos hermanos | [plan.md](plan.md) · [tasks.md](tasks.md) |

## 1. Contexto y alcance

Los cuatro microservicios Spring de CAMEIA (`cameia-cuentas`, `cameia-gateway`, `cameia-perfil` y `cameia-entrevista`) se escribieron con documentos de agente distintos que se contradicen entre sí, con `CONTRIBUTING.md`, con el validador de PR y con el código. Esta spec los lleva a un mismo conjunto de reglas de código, de documentación y de configuración técnica, de modo que cada repositorio se pueda leer solo y todos sean consistentes. Hay una spec completa en cada repositorio; las secciones 1 a 5 y 8 a 10 son iguales en los cuatro, y las secciones 6 y 7 son propias de este.

**Parte 1, documentos.** Tres piezas por repositorio, cada una en su PR y de no más de 1000 líneas entre agregadas y eliminadas:

| Pieza | Contenido |
|---|---|
| A | `specs/CM-283-AlinearEstandar/` con `spec.md`, `plan.md` y `tasks.md` |
| B | Estándar común: `docs/estandar-backend.md`, `docs/errores.md`, `docs/adr/` y `docs/bitacora-ia/` (en Perfil, `docs/constitution.md` va en la pieza C1b porque enlaza `CLAUDE.md`) |
| C | Documentos de agente: `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md`, `README.md` y los documentos que se retiran. Se parte en las piezas C1, C2, etc. que hagan falta para no pasar de 1000 líneas; la sección 3 del plan indica el reparto de este repositorio |

**Parte 2, configuración técnica**, del 13 al 23 de octubre de 2026: una pieza por PR y por repositorio (sección 7).

**Fuente única de cada regla.** Cada regla se define en un solo documento y los demás lo enlazan sin copiarla (REQ-DOC-10). Es lo que impide que dos documentos vuelvan a contradecirse.

## 2. Decisiones

Todas son decisiones humanas de Backend, tomadas el 5 de octubre de 2026. Cada una indica por qué y qué se descartó.

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-01 | `CONTRIBUTING.md` manda en rama, commit y título de PR; los cuatro quedan idénticos: el commit lleva siempre `CM-NNN \| tipo(scope): resultado` | Un solo flujo para los cuatro y el criterio «no se contradicen» sin matices | Dejar los tres que solo recomiendan Conventional Commits |
| D-02 | Una spec completa en cada repositorio | Cada repositorio debe poder leerse solo | Una sola spec en Cuentas |
| D-03 | Se trabaja en `git worktree` | No se tocan las ramas abiertas de otras tareas | Cambiar de rama en cada repositorio |
| D-04 | `docs/errores.md` lista solo los códigos que el código emite hoy | Documentar códigos sin implementar afirmaría una capacidad pendiente | Crear el catálogo con códigos futuros |
| D-05 | El ADR del código de error y del `requestId` entra en la Parte 1 | Es documentación y no depende de código | Escribirlo en la Parte 2 |
| D-06 | `[IA-ASISTIDO]` va solo al final del título del PR; el commit no lo lleva | Con Squash and merge el título del PR es el commit que queda en `develop` | La marca en cada commit |
| D-07 | Una sola fuente de verdad: se retira el documento de reglas del equipo de Perfil y lo que lo duplica | Un documento antiguo por encima del estándar produce contradicciones | Conservarlo con una nota de «no vigente» |
| D-08 | `guidelines.md` se retira de Cuentas y Entrevista; lo que tenga de más pasa al estándar | Perfil y Gateway no lo tienen y choca con decisiones tomadas | Conservarlo en los cuatro |
| D-09 | Perfil de producción `prod`, bandera `API_DOCUMENTATION_ENABLED` y puerto único (el mismo dentro y fuera del contenedor) en los cuatro, sin romper nada y avisando a DevOps solo lo necesario | Mismas variables y mismo comportamiento en todos | Que cada servicio conserve el suyo |
| D-10 | ArchUnit en Entrevista como pieza de la Parte 2 | La prueba de arquitectura ya existe en los otros tres | Dejar el `CLAUDE.md` de Entrevista con la prueba como pendiente |
| D-11 | Perfil conserva el paquete base `co.edu.unicauca.cameia.perfil`; la diferencia se documenta | Renombrar toca todos los archivos y supera con mucho las 1000 líneas | Tarea aparte para renombrarlo |
| D-12 | Todo hallazgo tiene un destino: un PR, una pieza de la Parte 2, una tarea existente o un pendiente con responsable | Nada queda sin resolver | — |
| D-13 | Los documentos del repositorio nombran roles (Backend, DevOps, Frontend, Product Owner), no personas | Una regla no depende de quien la dijo | Nombres propios |
| D-14 | Las pruebas nuevas se nombran `metodo_shouldResultado_whenCondicion` en inglés, con `@DisplayName` en español; las existentes se renombran cuando una tarea las toca | Los nombres de método son identificadores y van en inglés; hoy hay cuatro convenciones | Renombrar todas ahora (diff enorme sin valor funcional) |
| D-15 | No se usa `TODO` en el código; lo pendiente se registra en Jira o en el apartado de pendientes del `CLAUDE.md` | El estándar prohíbe claves Jira en comentarios y un `TODO` sin clave no se puede seguir | `TODO` con clave Jira (regla actual de Perfil) |

## 3. Requisitos comunes (EARS)

**REQ-DOC-01.** El repositorio deberá tener un `CLAUDE.md` en la raíz con exactamente diez secciones de nivel 2, en este orden y con estos títulos: `## 1. Servicio`, `## 2. Estructura y dependencias`, `## 3. Límites de confianza`, `## 4. Contrato y errores`, `## 5. Datos`, `## 6. Seguridad`, `## 7. Pruebas`, `## 8. Verificación`, `## 9. Contribución`, `## 10. Pendientes`.

**REQ-DOC-02.** El repositorio deberá tener un `AGENTS.md` en la raíz cuyo único contenido sea la línea `Las reglas de este repositorio están en [CLAUDE.md](CLAUDE.md).`

**REQ-DOC-03.** El repositorio deberá tener `docs/estandar-backend.md` con las quince secciones de la sección 4.1, y su suma SHA-256 deberá ser idéntica en los cuatro repositorios.

**REQ-DOC-04.** El repositorio deberá tener `docs/constitution.md` con los principios de la sección 4.3, cada uno con la forma en que se comprueba.

**REQ-DOC-05.** El repositorio deberá tener `docs/errores.md` con la estructura de la sección 4.4, que lista solo lo que el código emite en el SHA de `origin/develop` indicado en el encabezado de esta spec.

**REQ-DOC-06.** El repositorio deberá tener `docs/adr/0001-codigo-de-error-y-request-id.md` con el contenido de la sección 4.5.

**REQ-DOC-07.** El repositorio deberá tener `docs/bitacora-ia/README.md` y `docs/bitacora-ia/01_alinear-estandar-backend_prompt.md`, con el contenido de la sección 4.6.

**REQ-DOC-08.** Cuando el `CONTRIBUTING.md` de un repositorio difiera del de `cameia-cuentas`, el repositorio deberá quedar con un `CONTRIBUTING.md` byte a byte igual al de `cameia-cuentas`.

**REQ-DOC-09.** El `README.md` del repositorio deberá remitir a `CONTRIBUTING.md` para rama, commit, tipos y revisión, sin repetirlos; deberá usar `./mvnw.cmd clean verify` como comando de verificación; y no deberá contener enlaces rotos.

**REQ-DOC-10.** Cada regla deberá definirse en el documento que la sección 4.7 le asigna; los demás documentos deberán enlazarlo y no copiarla.

**REQ-DOC-11.** Ningún documento del repositorio deberá: nombrar a una persona como fuente de una regla; citar hojas de bitácora personales ni rutas fuera del repositorio; afirmar datos que envejecen (cantidad de pruebas, porcentajes de cobertura, estado de ramas o de PR, fechas de adopción); ni afirmar como existente algo que el código no tiene.

**REQ-DOC-12.** Todo enlace relativo de los documentos que esta spec crea o modifica deberá apuntar a un archivo que exista.

**REQ-DOC-13.** Mientras exista un documento retirado por esta spec, ningún otro documento deberá enlazarlo.

**REQ-DOC-14.** El `CLAUDE.md` del repositorio deberá indicar que las specs nuevas viven en `specs/CM-NNN-Descripcion/` (`Descripcion` en PascalCase), y las carpetas de spec existentes con otro nombre no se renombrarán.

**REQ-DOC-15.** Cada PR de la Parte 1 deberá tener título `CM-283 | docs(scope): resultado [IA-ASISTIDO]`, no superar 1000 líneas de diff entre agregadas y eliminadas, llenar los ocho campos de la plantilla de PR del repositorio y dejar «Control humano» en pendiente.

## 4. Contenido de los documentos

### 4.1 `docs/estandar-backend.md` (idéntico en los cuatro)

Está escrito en español, es autosuficiente (no depende de ningún archivo fuera del repositorio) y no contiene datos que envejecen. Quince secciones, en este orden:

| # | Sección | Qué debe contener |
|---|---|---|
| 1 | Alcance y precedencia | A quién aplica. Precedencia: la seguridad y los contratos ya publicados; este estándar en calidad de código; `CONTRIBUTING.md` en rama, commit y PR; `docs/constitution.md` en principios no negociables; `CLAUDE.md` en lo propio del servicio. Si dos fuentes chocan, se detiene el trabajo y se corrige el documento en un PR |
| 2 | Idioma y nombres | Identificadores en inglés; documentación, Javadoc, OpenAPI, logs, mensajes, `@DisplayName`, commits y PR en español; tablas y columnas en `snake_case` español y singular; sin abreviaturas salvo siglas técnicas establecidas (`dto`, `id`, `uid`, `url`, `api`, `jwt`); sufijo por capa (`Controller`, `Request`/`Response`, `AppService`, `Command`, `Policy`, `Entity`, `JpaRepository`, `Listener`, `PayloadV<n>`, `Config`); verbos (`can…`/`is…`/`has…` sin efectos, `create`/`of` para fábricas, `require…` para guardias, políticas que devuelven un enumerado y no un `boolean`); nombre de pruebas nuevas `metodo_shouldResultado_whenCondicion` con `@DisplayName` en español; sin `I` ni `Impl` |
| 3 | Arquitectura y dependencias | Capas `presentation`, `application`, `domain`, `infrastructure` y sus reglas; `domain` sin Spring, JPA, Rabbit ni Google; persistencia en tres piezas (puerto, Spring Data, adaptador); mapeo a mano; `@Transactional` solo en `application.service` y `readOnly = true` en lecturas; ninguna llamada externa dentro de una transacción; la prueba ArchUnit del repositorio en verde; prohibido `Utils`, Singleton manual, Service Locator, herencia de más de dos niveles, `Map<String,Object>` como payload, importar clases de otro microservicio |
| 4 | Código limpio y mantenible | Lo mínimo que cumple la spec; reutilizar, extender, crear; método ≤ 20 líneas (15 en agregados y objetos de valor); ≤ 3 parámetros; anidamiento ≤ 2; complejidad ciclomática ≤ 8; clase ≤ 200 líneas; sin parámetro booleano que cambie el comportamiento; inyección por constructor con dependencias `private final` y sin `@Autowired`; visibilidad de paquete por defecto en controladores, `@Configuration`, `@Bean`, servicios, `JpaRepository` y adaptadores, y pública solo para modelo de dominio, puertos, DTO y comandos; objetos de valor inmutables (`record`); sin setters públicos en el dominio; agregados que no exponen sus colecciones; `ResponseEntity<T>` con el estado explícito (201 en la creación); raíz JSON siempre un objeto; paginación obligatoria en colecciones sin cota; sin `TODO` en el código; logs con SLF4J y guarda o `Supplier` en los costosos de `DEBUG` y `TRACE`; antipatrones que bloquean un PR (dominio anémico, entidad JPA como modelo de dominio, agregado anotado con `@Entity`, lógica de negocio en el controlador, entidad o agregado devuelto por el controlador, clave foránea hacia otro contexto, `JpaRepository` inyectado desde `application`) |
| 5 | Validación y casos borde | Validación en dos capas (DTO y dominio); la lista de casos borde por grupo (presencia, texto en NFC, números, fechas en UTC, enumerados, duplicados, estado, propiedad, colecciones, concurrencia, falla parcial, dependencias, carga) con la regla «cada celda aplicable es una prueba; las que no aplican se justifican en la spec» |
| 6 | Errores | Respuesta `application/problem+json; charset=UTF-8` con `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId` y `errors[{field,code,message}]`; códigos `<SUJETO>_<CAUSA>` con vocabulario de causas cerrado; reglas del catálogo (un código ↔ un estado ↔ un mensaje ↔ una excepción ↔ una prueba); mapa base de estados; `INTERNAL_ERROR` con mensaje genérico; el Gateway conserva su formato `{code,message}`; los códigos `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` están reservados por el contrato del proyecto y se publican cuando el código los emita; cada error se registra una vez con `code`, `requestId` e identificador interno, sin datos personales |
| 7 | Base de datos | Solo por migración Flyway; esquema que repite el código (`NOT NULL`, `CHECK`, `UNIQUE`, `VARCHAR(n)` del mismo tamaño que el límite validado); tipos (`uuid`, `timestamptz` en UTC, `date`, `numeric`); claves foráneas solo dentro del servicio con `ON DELETE` explícito; índices; `@Version` y bloqueo pesimista; nombres con prefijos `pk_`, `fk_`, `uq_`, `ck_`, `ix_`; datos sensibles; una prueba por restricción |
| 8 | Seguridad | ASVS nivel 1 y OWASP API Security Top 10 (tabla API1 a API10 con qué se comprueba); identidad solo de los encabezados `X-User-*` del Gateway; consultas parametrizadas; respuestas por DTO; `Content-Type` con charset; Actuator público solo en `health` e `info`; secretos solo por variable de entorno y `.env.example` sin valores; nada sensible en logs, URL ni errores |
| 9 | Pruebas y cobertura | Tipos por capa; PostgreSQL real con Testcontainers y puerto aleatorio en todo servicio con base de datos; H2 prohibido; `*Test` con Surefire y `*IT` con Failsafe; cobertura de lo nuevo o modificado ≥ 90 % de líneas y de ramas medida con JaCoCo, con cada línea sin cubrir justificada; la cobertura global del repositorio no baja; build terminado = `./mvnw.cmd clean verify` con salida real |
| 10 | Documentación | OpenAPI/Swagger completo (cada endpoint, parámetro, respuesta de error y DTO con descripción, ejemplo, obligatoriedad, límites, patrón y valores permitidos, coherentes con la validación y la columna); Javadoc en toda clase, interfaz, `record`, enumerado y método; `package-info.java` por paquete; comentarios de bloque en la lógica importante y de línea cortos; ADR por decisión de contrato o arquitectura; `docs/errores.md`; `README.md` y `.env.example` al día; sin `CM-NNN` ni referencias a otros archivos en los comentarios |
| 11 | Reglas del proyecto | Idempotencia, LLM y APIs externas, eventos, datos entre servicios, propiedad (el dueño sale del token; Perfil 403 y 404, Entrevista 404 en ambos), fechas en UTC con reloj inyectable, texto libre, rendimiento (p95 ≤ 2 s en operaciones propias sin IA, 5xx propios < 1 %), privacidad (Ley 1581), servicio desplegable (`Dockerfile` de dos etapas, usuario sin privilegios y `HEALTHCHECK`; `.env.example`; ingreso interno), evidencia del PR |
| 12 | Desarrollo guiado por especificación | Análisis, spec, plan y tareas con aprobación entre fases; sin spec aprobada no hay código; `specs/CM-NNN-Descripcion/` con `spec.md` (requisitos EARS numerados, cada campo con regla, límites, mensaje, código y estado HTTP), `plan.md` y `tasks.md` (cada tarea ≤ 30 minutos, marcada `[x]` en el momento de terminarla); decisiones con porqué, alternativas descartadas y decisión humana; lo que no se sabe se pregunta (hasta 6 preguntas por ronda) y lo sin respuesta queda como pendiente con su responsable; un PR no pasa de 1000 líneas |
| 13 | Contribución y entrega | Remite a `CONTRIBUTING.md`; `[IA-ASISTIDO]` solo al final del título del PR; la IA puede abrir un PR y nunca fusionarlo; el control humano lo marca quien revisa; plantilla de PR de ocho campos; bitácora de IA solo con la entrada de las decisiones con peso humano en `docs/bitacora-ia/` |
| 14 | Guía de Spring Boot: qué se adopta | Tabla de los catorce puntos de la guía de Spring Boot: se adoptan inyección por constructor, visibilidad de paquete, propiedades tipadas y validadas, fronteras de transacción, `open-in-view=false`, separación web y persistencia, comandos, manejo centralizado de excepciones, Actuator mínimo, Testcontainers, puerto aleatorio y logging; **no se adopta** el versionado nativo de API (la versión va en la ruta `/api/v1/...`, contrato consumido por Gateway, Web y Entrevista) ni la internacionalización con `ResourceBundles` (el producto es monolingüe en español y no hay requisito); sobre configuración por variables de entorno frente a perfiles, rige el perfil `prod` de la decisión D-09 |
| 15 | Atributos de calidad | Tabla de los siete atributos (seguridad, fiabilidad, rendimiento, mantenibilidad, testabilidad, observabilidad, compatibilidad de contrato) con la evidencia típica; cada PR declara los que toca |

**Límite de la sección 4.1.** Las reglas propias de un servicio (por ejemplo, el modelo de seguridad del Gateway o la regla de no calcular cuota en Perfil) no entran aquí: viven en su `CLAUDE.md`.

### 4.2 `CLAUDE.md`

Sigue la estructura de REQ-DOC-01. Lo común:

- La sección 8 abre con: «Verificación completa: `./mvnw.cmd clean verify`. Genera el informe de cobertura en `target/site/jacoco/index.html`.»
- La sección 9 es idéntica en los cuatro y dice exactamente:

```
## 9. Contribución

Rama, commit, tipos, título de PR, revisión y merge: rige [CONTRIBUTING.md](CONTRIBUTING.md). Lo que este repositorio añade:

- El título del PR lleva `[IA-ASISTIDO]` al final cuando hubo IA; el commit no lo lleva. Un commit asistido por IA lleva el trailer `Co-Authored-By` con el modelo.
- Una IA puede abrir un PR; nunca lo fusiona. El control humano lo marca la persona que revisa.
- Un PR cubre una pieza reconocible y no pasa de 1000 líneas entre agregadas y eliminadas.
- Antes del código hay una spec aprobada en `specs/CM-NNN-Descripcion/` (`spec.md`, `plan.md` y `tasks.md`); el flujo está en la sección 12 de [docs/estandar-backend.md](docs/estandar-backend.md).
- Las decisiones con peso humano se registran en [docs/bitacora-ia/](docs/bitacora-ia/README.md).
```

- La sección 10 lista cada pendiente con su responsable (rol) y lo que bloquea. Solo lo que sigue abierto en el código y en Jira; nada resuelto.
- Todo hecho que afirma (puertos, perfiles, rutas, tablas, componentes) se comprobó contra el código de `origin/develop`.
- El contenido propio de cada servicio sale del documento de agente actual y se detalla en la sección 6.

### 4.3 `docs/constitution.md`

Lista numerada de principios no negociables. Cada uno termina con `→` y la forma de comprobarlo. Los comunes, en este orden:

1. **Stack:** Java 21, Spring Boot 4.1.1 y Maven Wrapper (más PostgreSQL 16 si el servicio tiene base de datos); subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo lo que es del servicio; base y rol propios y sin claves foráneas hacia otros servicios (cuando haya base). → revisión del PR y del esquema.
3. **Capas y dependencias:** las reglas de la sección 3 del estándar. → prueba de arquitectura en verde.
4. **Entrada de confianza:** solo se acepta el tráfico del Gateway; la identidad llega en los encabezados `X-User-*` y nunca del cuerpo ni de la ruta. → configuración de despliegue y pruebas del controlador.
5. **Datos sensibles:** secretos solo por variable de entorno o gestor de secretos; nada sensible en logs, URL ni errores. → escaneo de secretos en CI y búsqueda de `System.out`.
6. **Errores:** formato común con `code` y `requestId`; cada error previsible con código, estado, mensaje y prueba. → `docs/errores.md` y pruebas del manejador.
7. **Base de datos:** solo por migración; el esquema repite lo que valida el código. → migración probada con Testcontainers (cuando haya base).
8. **Desarrollo guiado por especificación:** nada se implementa sin spec aprobada. → revisión del PR contra la spec.
9. **Puerta de ambigüedad:** ante una ambigüedad de seguridad, contrato, datos o arquitectura se detiene el trabajo y se pregunta (hasta 6 preguntas por ronda). → decisión en la spec.
10. **Pruebas:** JUnit 5, un caso por camino y por rama, PostgreSQL real con Testcontainers y puerto aleatorio, cobertura de lo nuevo ≥ 90 %. → informe de JaCoCo.
11. **Verde antes del PR:** `./mvnw.cmd clean verify` pasa. → salida del comando.
12. **Tamaño del cambio:** un PR no pasa de 1000 líneas entre agregadas y eliminadas. → `git diff --shortstat`.
13. **Idioma y nombres:** identificadores en inglés y documentación en español. → revisión del PR.
14. **Documentación formal:** OpenAPI, Javadoc y comentarios completos. → revisión del PR y OpenAPI generado.
15. **Contribución:** rige `CONTRIBUTING.md`; la revisión la hace una persona distinta del autor y el merge siempre lo hace una persona. → reglas de rama y validadores de PR.

Además, los principios propios del servicio (sección 6), numerados a continuación. El detalle de cada principio vive en el estándar y en el `CLAUDE.md`; la constitución solo lo enuncia y enlaza.

### 4.4 `docs/errores.md`

Cuatro apartados: (1) «Formato», un párrafo que remite a la sección 6 del estándar; (2) «Códigos que el servicio emite» con la tabla `Código | HTTP | Mensaje | Origen | Prueba` (solo códigos que el código emite hoy); (3) «Respuestas sin código» con la tabla `HTTP | Título | Cuándo`, para los servicios que responden `ProblemDetail` sin `code`; (4) «Cómo se agrega un código»: con la spec que lo introduce, junto a su excepción y su prueba, sin reutilizar ni renombrar uno publicado.

### 4.5 `docs/adr/0001-codigo-de-error-y-request-id.md`

Secciones: `Estado` (Aceptada), `Contexto`, `Decisión`, `Consecuencias` y `Alternativas descartadas`. Contexto: el contrato base de error es `ProblemDetail` (RFC 9457) con `errors[{field,message}]` y JSON en `camelCase`, y los servicios necesitan un identificador estable para depurar y para que Frontend actúe por causa. Decisión: se agregan `code`, `requestId` y `errors[].code` como miembros de extensión, sin reemplazar nada de lo publicado; `requestId` es el `X-Request-Id` que pone el Gateway. Consecuencias: cambio aditivo y compatible; Frontend puede empezar a leer los campos nuevos cuando cada servicio los emita; cada servicio los adopta en su primera tarea de código. Alternativas descartadas: `codigoCameia` y `correlationId` (nombres propios sin ventaja sobre los miembros de extensión de la norma) y un formato propio distinto de `ProblemDetail`. La variante del Gateway describe que conserva `{code,message}` y que es quien genera el `requestId`. Decisión humana: Backend, 5 de octubre de 2026.

### 4.6 `docs/bitacora-ia/`

`README.md` explica, en un párrafo, que la carpeta guarda solo la entrada (el prompt armado con rol, contexto, tarea, condiciones y formato) de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA, que la salida es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones) y que el nombre de cada archivo es `NN_tema_prompt.md`. `01_alinear-estandar-backend_prompt.md` contiene la fecha, la herramienta y el prompt de la decisión de alinear los cuatro repositorios, redactado de forma autosuficiente y con el enlace a `specs/CM-283-AlinearEstandar/spec.md`.

### 4.7 Fuente única de cada regla

| Regla | Documento donde se define | Los demás |
|---|---|---|
| Rama, commit, tipos, título de PR, revisión, merge | `CONTRIBUTING.md` | Enlazan |
| Ubicación de `[IA-ASISTIDO]`, merge de la IA, tamaño del PR | `CLAUDE.md` sección 9 | Enlazan |
| Calidad de código, errores, base de datos, seguridad, pruebas, documentación | `docs/estandar-backend.md` | Enlazan |
| Principios no negociables | `docs/constitution.md` | Enlazan |
| Rutas, estructura, datos y seguridad propios del servicio | `CLAUDE.md` | — |
| Códigos de error del servicio | `docs/errores.md` | Enlazan |
| Decisiones de contrato o arquitectura | `docs/adr/` y la spec que las toma | Enlazan |
| Instalación, comandos y puertos de uso | `README.md` | — |

## 5. Hallazgos comunes y su destino

Cada hallazgo se comprobó contra `origin/develop` y tiene un destino. «P1» es la Parte 1 y «P2» la Parte 2.

| ID | Hallazgo | Evidencia | Destino |
|---|---|---|---|
| H-01 | `CONTRIBUTING.md` de gateway, perfil y entrevista no exige la clave Jira en el commit; el de Cuentas sí | `CONTRIBUTING.md` líneas 33 a 35 y 41 | P1 C (REQ-DOC-08) |
| H-02 | `perf` figura como tipo en READMEs y documentos de agente; el validador de PR acepta ocho tipos sin `perf` | `.github/scripts/pr_policy.py` línea 9 | P1 C |
| H-03 | La identidad se describe como `firebase_uid, email, roles, request_id`; el código usa `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-User-Plan`, `X-User-Email-Verified` y `X-Request-Id` | controladores y filtro del Gateway | P1 B y C |
| H-04 | RFC 7807 y JSON `snake_case` en documentos; el código usa RFC 9457 y `camelCase` | documentos de agente | P1 C |
| H-05 | Personas nombradas como fuente de reglas y hojas de bitácora personales fuera del repositorio | documentos de agente | P1 C (REQ-DOC-11) |
| H-06 | Perfil y Gateway ponen un «documento de reglas del equipo» por encima de su propio documento | encabezado de sus `AGENTS.md` | P1 C |
| H-07 | `TODO` con clave Jira: regla de Perfil y 8 en su código de producción | `AGENTS.md` §6; `git grep TODO` | Regla: P1 B. Código: P2 de Perfil |
| H-08 | «Sin abreviaturas» con ejemplos en español, frente a identificadores en inglés | `CLAUDE.md` de Cuentas y Entrevista | P1 B |
| H-09 | Verificación con `test` y `clean package`, sin la cobertura de `clean verify` | `CLAUDE.md`, constituciones, READMEs | P1 B y C |
| H-10 | Carpetas de spec con tres formas de nombre | `specs/` de cada repositorio | P1 C (REQ-DOC-14) |
| H-11 | Cuatro convenciones de nombre de prueba | `src/test` de cada repositorio | P1 B (D-14) |
| H-12 | `guidelines.md` y los `AGENTS.md` de Perfil y Gateway traen reglas que el estándar no recogía (inyección por constructor con `final`, visibilidad de paquete, `ResponseEntity` explícito, raíz JSON, paginación, guardas de log, sufijos por capa, verbos, antipatrones) | documentos de agente | P1 B (sección 4.1) |
| H-13 | `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` se describían como publicados; ningún código fuente los emite | `git grep` en los cuatro repositorios | P1 B (sección 6 del estándar) |
| H-14 | Ningún servicio emite `code`, `requestId` ni `errors[].code` | `git grep` | Cada servicio, en su primera tarea de código: Cuentas CM-36, Perfil CM-271, Entrevista en la pieza de 15 de octubre; el Gateway conserva su formato |

## 6. Cambios propios de cameia-perfil

### 6.1 Archivos

| Archivo | Acción | Detalle |
|---|---|---|
| `CLAUDE.md` | Crear | Diez secciones (REQ-DOC-01) con el contenido de REQ-PF-01 a REQ-PF-05; el repositorio hoy no tiene `CLAUDE.md` |
| `AGENTS.md` | Reducir a una línea | REQ-DOC-02; su contenido pasa a `CLAUDE.md` y al estándar |
| `docs/estandar-backend.md` | Crear | Sección 4.1 |
| `docs/constitution.md` | Crear | Principios comunes (4.3) y los propios de REQ-PF-06 |
| `docs/errores.md` | Crear | Sección 4.4; REQ-PF-07 |
| `docs/adr/0001-codigo-de-error-y-request-id.md` | Crear | Sección 4.5 |
| `docs/bitacora-ia/README.md` y `01_alinear-estandar-backend_prompt.md` | Crear | Sección 4.6 |
| `docs/03092026_v1_reglas-codigo-backend-cameia.md` | Eliminar | D-07 |
| `docs/05092026_v1_handoff-implementacion-hu.md` | Eliminar | Derivado de D-07: es un traspaso con reglas de publicación, de bitácora y de ramas que contradicen el estándar |
| `docs/sdd.md` | Eliminar | Derivado de D-07: su metodología queda definida en la sección 12 del estándar |
| `docs/README.md` | Reescribir | REQ-PF-08 |
| `README.md` | Modificar | REQ-DOC-09 y REQ-PF-09 |
| `docs/insumos/` (3 archivos) | Eliminar | Derivado de D-07: son propuestas de septiembre dirigidas al equipo (contenedores, puertos y respuestas) que el estándar y las decisiones vigentes reemplazan; citan la RFC 7807, la norma retirada y personas como responsables |
| `docs/DOCKER.md` | Revisar | Se modifican solo las líneas que contradigan el estándar (REQ-PF-10) |
| `CONTRIBUTING.md` | Reemplazar | REQ-DOC-08 |

### 6.2 Requisitos

- **REQ-PF-01 (sección 1 del `CLAUDE.md`).** El `CLAUDE.md` deberá describir Perfil como el dueño de los perfiles profesionales, la experiencia, la educación, las habilidades, los roles objetivo y la revisión humana de lo que sugiere la IA; deberá decir que no es dueño de credenciales, pagos, sesiones de entrevista ni del consumo; que no verifica tokens (eso es del Gateway) ni usa Firebase Admin SDK; y que no calcula cuota sino que consume su réplica.
- **REQ-PF-02 (sección 2).** El `CLAUDE.md` deberá describir el paquete base `co.edu.unicauca.cameia.perfil` y decir que difiere de `tech.cameia.<servicio>` de los demás servicios y por qué se conserva (D-11); el árbol de paquetes sin `infrastructure/client` ni `persistence/mapper` (el mapeo es privado en el adaptador); las reglas de dependencia y la prueba de arquitectura que las vigila; la lista de componentes sin la columna de sprint ni estados que envejecen; y la tabla de equivalencias entre el glosario del proyecto (español) y los nombres del código (inglés), con la regla de que cada clase de dominio lleva en su Javadoc el término del que sale.
- **REQ-PF-03 (secciones 3 y 4).** El `CLAUDE.md` deberá describir que la identidad llega en `X-User-Id`, que falta de identidad responde 401 y recurso ajeno 403, y que todo endpoint cuelga de `/api/v1/profiles/**` porque es el único predicado de ruta que el Gateway enruta hacia Perfil. Deberá declarar el JSON en `camelCase`, los errores `ProblemDetail` según RFC 9457 activados con `spring.mvc.problemdetails.enabled`, la excepción de forma de la respuesta de finalización (`CompletionErrorResponse`, que conserva `missingRequirements`) y la tabla de rutas del servicio comprobada contra los controladores.
- **REQ-PF-04 (secciones 5 y 6).** La sección 5 deberá listar las tablas y las migraciones Flyway reales, la base propia `cameia_perfil`, la ausencia de claves foráneas hacia otros servicios y la convención de nombres. La sección 6 deberá conservar las reglas propias: nunca registrar el contenido de un CV, un resumen, una experiencia ni dato alguno del perfil; guardar solo el texto extraído de un CV y nunca el archivo; y no persistir un dato generado por IA sin `procedencia` y `estadoRevision`.
- **REQ-PF-05 (secciones 7, 8 y 10).** La sección 7 deberá describir los tipos de prueba por capa y el nombre `*Test` para unitarias y `*IT` para integración. La sección 8 deberá dar `docker compose run --rm verify`, `./mvnw.cmd clean verify` y las URL locales con el puerto 8082. La sección 10 deberá listar solo lo que sigue abierto, cada punto con su evidencia en código o en Jira y su responsable; lo que el código ya resolvió se retira. La falta de Testcontainers y de Failsafe se lista como pendiente de esta tarea.
- **REQ-PF-06 (constitución).** La constitución deberá incluir además: «16. **Perfil no calcula cuota:** consume la réplica del plan. → revisión del PR», «17. **No se guarda el archivo de un CV:** solo el texto extraído. → revisión del PR y del esquema», «18. **Procedencia y revisión:** todo dato generado por IA se persiste con `procedencia` y `estadoRevision`. → pruebas del dominio» y «19. **Rutas:** todo endpoint cuelga de `/api/v1/profiles/**`. → prueba de contrato de rutas».
- **REQ-PF-07 (errores).** `docs/errores.md` deberá listar en «Respuestas sin código» cada estado que producen `ApiExceptionHandler` y la respuesta de finalización en `origin/develop`, leídos del código, y deberá decir que Perfil aún no emite `code`.
- **REQ-PF-08 (índice de `docs/`).** `docs/README.md` deberá indexar solo documentos que existan, deberá presentar `CLAUDE.md` como la norma del repositorio y `docs/estandar-backend.md` como el estándar común, y no deberá mencionar los tres documentos que se retiran.
- **REQ-PF-09 (README).** El `README.md` deberá enlazar `CLAUDE.md` donde enlaza `AGENTS.md`, deberá nombrar las pruebas con el formato de la decisión D-14 (hoy dice `<metodo>_deberia<Resultado>_cuando<Condicion>`), deberá reemplazar sus líneas de contribución por un enlace a `CONTRIBUTING.md`, y deberá usar `./mvnw.cmd clean verify` como comando de verificación.
- **REQ-PF-10 (documentos de apoyo).** Cuando `docs/DOCKER.md` contradiga el estándar, deberá corregirse solo la línea en conflicto; si no lo contradice, no se modifica. `docs/insumos/` deberá eliminarse completo y ningún documento deberá enlazarlo.
- **REQ-PF-11.** El `CLAUDE.md` no deberá conservar: las reglas de publicación sin autorización, la sección de bitácora por hoja personal, los nombres de personas, la jerarquía que pone el documento del equipo por encima del repositorio, el ciclo de siete fases (pasa a la sección 12 del estándar), la regla de `TODO` con clave Jira, la tabla de estado de historias de usuario con números de PR, ni el formato de ramas, commits y PR (rige `CONTRIBUTING.md`).

### 6.3 Hallazgos propios y destino

| ID | Hallazgo | Destino |
|---|---|---|
| H-P1 | `AGENTS.md` contradice a `CONTRIBUTING.md` y al estándar: jerarquía, nombres de personas, `snake_case`, `TODO CM-NNN`, `CA-NNN`, publicación, bitácora personal | P1 C1 |
| H-P2 | Cinco documentos de `docs/` con reglas obsoletas, nombres de personas y la RFC 7807 (la norma del equipo, el traspaso, la guía SDD y las propuestas de `insumos/`) | P1 C1a y C2 |
| H-P3 | `README.md` nombra pruebas con otro formato que `AGENTS.md`, enlaza `AGENTS.md` y usa `clean package` | P1 C1 |
| H-P4 | Ocho `TODO` con clave Jira en el código de producción | P2-06 (los de RabbitMQ) y P2-12 (los demás) |
| H-P5 | Swagger sin bandera y abierto en producción; sin perfil `prod` | P2-07 |
| H-P6 | Una clase `*IT` que ninguna fase ejecuta | P2-02 |
| H-P7 | Pruebas de base de datos contra el PostgreSQL de `docker-compose`, sin Testcontainers | P2-09 |
| H-P8 | Paquete base distinto de los demás servicios | Documentado (D-11) |
| H-P9 | Cobertura global por debajo de la meta de 70 % de la rúbrica y lejos del 90 % | P2-08 (más de 70 %); 90 % en PD-07 |
| H-P10 | La API y el consumidor de RabbitMQ comparten proceso | P2-04, y el pedido 10 a DevOps para el despliegue |
| H-P11 | La prueba de arquitectura se llama `ArquitecturaTest` (identificador en español) | P2-12 (se renombra a `LayeredArchitectureTest`) |

## 7. Parte 2 en cameia-perfil

Una pieza por PR, del 13 al 23 de octubre de 2026, después de los bloques de historias del cronograma. Cada PR cumple REQ-DOC-15 (título `CM-283 | tipo(scope): resultado [IA-ASISTIDO]`, no más de 1000 líneas y plantilla de ocho campos). Los requisitos de cada pieza van a continuación y los valores propios de este repositorio, al final.

#### P2-02 · Failsafe para separar las pruebas de integración (13 de octubre)

- **REQ-P2-02-1.** El `pom.xml` deberá declarar `maven-failsafe-plugin`, con la versión que gestiona `spring-boot-starter-parent`, y sus objetivos `integration-test` y `verify`.
- **REQ-P2-02-2.** Surefire deberá ejecutar las clases `*Test` y Failsafe las clases `*IT`.
- **REQ-P2-02-3.** Cuando una clase de prueba arranque el contexto completo de Spring (`@SpringBootTest`) o use Testcontainers, deberá llamarse `*IT`; las existentes que lo cumplan se renombran en esta pieza. La lista sale de `git grep -lE "@SpringBootTest|@Testcontainers" -- src/test`.
- **REQ-P2-02-4.** JaCoCo deberá medir las dos fases: la cobertura de líneas y de ramas del informe de `clean verify` no deberá ser menor que la medida en el SHA de partida.
- **Verificación.** `./mvnw.cmd clean verify` en verde; la salida lista `maven-surefire-plugin:test` y `maven-failsafe-plugin:integration-test`; la cobertura de antes y de después se reporta con números; `Skipped: 0` en ambas fases.
- **Trampa conocida.** Una clase `*IT` no la ejecuta Surefire: sin Failsafe quedaría sin correr, como hoy pasa con la prueba `*IT` de Perfil.

#### P2-03 · Plugin de formato (decisión el 13 de octubre; aplicación el 21 de octubre)

- **REQ-P2-03-1.** Cuando Backend apruebe un plugin de formato (PD-01), el `pom.xml` deberá declararlo con una ejecución que falle `clean verify` si el código no cumple el formato de `.editorconfig` (4 espacios en Java, LF), y el código existente se formateará en un commit aparte que no cambie comportamiento.
- **REQ-P2-03-2.** Si Backend descarta el plugin, esta pieza se cierra sin cambios y la decisión se registra en el apartado «Pendientes» del `CLAUDE.md`.
- **Verificación.** `./mvnw.cmd clean verify` en verde; una línea con formato incorrecto hace fallar la verificación; el diff del formateo se revisa por separado.
- **Bloqueada por PD-01.**

#### P2-05 · `requestId` en el `MDC` y registros sin datos personales (15 de octubre)

- **REQ-P2-05-1.** Cuando llegue una petición, un filtro de máxima precedencia deberá leer `X-Request-Id`; si falta o no cumple el formato de PD-02, deberá generar uno nuevo; deberá poner el valor en el `MDC` con la clave de PD-02, devolverlo en el encabezado `X-Request-Id` de la respuesta y limpiar el `MDC` al terminar la petición, también si falla.
- **REQ-P2-05-2.** El patrón de log (`logging.pattern.level`) deberá incluir el `requestId` en cada línea.
- **REQ-P2-05-3.** Ningún registro de log deberá contener contraseñas, tokens, correos, nombres, CV ni cuerpos de petición; los registros actuales que lo incumplan se corrigen en esta pieza. La lista sale de `git grep -nE "log(ger)?\.(trace|debug|info|warn|error)\(" -- src/main`, revisada línea por línea.
- **Pruebas por servicio:** (a) con `X-Request-Id: abc-123`, la respuesta lo devuelve y el log lo contiene; (b) sin encabezado, se genera un UUID y se devuelve; (c) con un valor de 65 caracteres o con espacios, se genera uno nuevo; (d) tras la petición el `MDC` queda vacío; (e) cuando la petición falla, el `MDC` también se limpia.
- **Bloqueada por PD-02**.

#### P2-07 · `.env.example`, perfiles y propiedades tipadas (19 de octubre)

- **REQ-P2-07-1.** `.env.example` deberá listar cada variable que lee la aplicación o el `docker-compose.yml`, con un comentario, sin valores sensibles y en este orden: puerto, perfil, documentación de la API, base de datos, mensajería, integraciones.
- **REQ-P2-07-2.** La configuración propia del servicio deberá enlazarse con `@ConfigurationProperties` y `@Validated`, de modo que la aplicación no arranque si falta o es inválida. El Gateway conserva `@Value` donde su `CLAUDE.md` lo justifica.
- **REQ-P2-07-3.** La aplicación deberá tener un perfil `prod` que fije `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` con valor rígido, de modo que ninguna variable de entorno los encienda.
- **REQ-P2-07-4.** Cuando el perfil no sea `prod`, la documentación de la API deberá publicarse si y solo si `API_DOCUMENTATION_ENABLED=true`, con valor por defecto `false`; el `docker-compose.yml` local la enciende.
- **REQ-P2-07-5.** El puerto de escucha deberá resolverse como `${PORT:${SERVER_PORT:<puerto>}}`, el `Dockerfile` deberá declarar `ENV SERVER_PORT=<puerto>` y `EXPOSE <puerto>`, y el `docker-compose.yml` deberá publicar `${SERVER_PORT:-<puerto>}:${SERVER_PORT:-<puerto>}`. Puertos: gateway 8080, cuentas 8081, perfil 8082, entrevista 8083.
- **REQ-P2-07-6.** Antes de abrir el PR, Backend deberá comprobar que las pruebas de arranque, de bandera y de resolución de puerto del repositorio pasan; que `docker compose config` valida; y que el despliegue no cambia de comportamiento (en Cloud Run manda la variable `PORT` que inyecta la plataforma). Lo que no se pueda ejecutar se reporta.
- **Casos borde.** `API_DOCUMENTATION_ENABLED` ausente, vacía, `true`, `false`, `TRUE`, `1` y `yes`; con perfil `prod` y bandera `true` (la documentación sigue cerrada); `PORT` y `SERVER_PORT` ausentes, solo una definida y ambas definidas.
- **Aviso a DevOps solo si hace falta:** únicamente el cambio de nombre del perfil de Entrevista (pedido 14).

#### P2-10 · Umbral de cobertura de JaCoCo (22 de octubre)

- **REQ-P2-10-1.** Cuando se mida la cobertura el 22 de octubre con `clean verify`, el `pom.xml` deberá declarar la ejecución `check` de JaCoCo con una regla de líneas y otra de ramas (`COVEREDRATIO`), cuyo mínimo sea la cobertura medida ese día redondeada según PD-06, y nunca menor que la cobertura del SHA de partida.
- **REQ-P2-10-2.** Cuando la cobertura baje del mínimo, `clean verify` deberá fallar con un mensaje que nombre la regla y el valor.
- **Verificación.** Se reportan los valores medidos de líneas y de ramas y el mínimo fijado; una prueba temporal de que el build falla al subir el mínimo por encima de lo medido (no se commitea).
- **Bloqueada por PD-06.** Se informa a DevOps del valor fijado en cada repositorio, sin pedido, porque su CI ejecuta `verify`.

#### P2-11 · Revisión de los PR de Dependabot (16 y 23 de octubre)

- **REQ-P2-11-1.** Cuando haya PR de Dependabot abiertos que modifiquen `pom.xml`, Backend deberá entregar un informe con, por PR: la dependencia, la versión anterior y la nueva, el tipo de salto (parche, menor o mayor), si es compatible con Spring Boot 4.1.1 y el resultado de `clean verify` sobre una copia de la rama.
- **REQ-P2-11-2.** Ningún PR de Dependabot se integra por la IA: la integración la decide y la hace una persona.
- **Verificación.** El informe cubre todos los PR abiertos de ese día; los que no se pudieron probar se marcan `BLOQUEADO` con la razón.

#### P2-14 · Verificación final (23 de octubre)

- **REQ-P2-14-1.** Cuando termine el 23 de octubre, cada servicio deberá cumplir los casos V-01 a V-13 de la sección 8 y los de cada pieza de esta sección; lo que no cumpla se lista con su responsable y su tarea en Jira.

### Valores propios de cameia-perfil

| Pieza | Aplica | Valor propio |
|---|---|---|
| P2-01 `HEALTHCHECK` | Sí | Ver más abajo |
| P2-02 Failsafe | Sí | Renombrar `ProfessionalProfileRepositoryAdapterIT` (ya es `*IT`) y las pruebas con `@SpringBootTest` que lo requieran |
| P2-03 Plugin de formato | Condicionada a PD-01 | — |
| P2-04 Modo de arranque | Sí | Ver más abajo |
| P2-05 `requestId` | Sí | Filtro servlet |
| P2-06 RabbitMQ | Sí | Ver más abajo |
| P2-07 Configuración | Sí | Puerto 8082; crear el perfil `prod` y la bandera `API_DOCUMENTATION_ENABLED` (hoy no existen); el flujo de despliegue de Perfil no define perfil, así que la bandera apagada por defecto cierra Swagger sin pedir nada a DevOps |
| P2-08 Cobertura | Sí | Ver más abajo |
| P2-09 Testcontainers | Sí | Ver más abajo |
| P2-10 JaCoCo | Sí | — |
| P2-11 Dependabot | Sí | — |
| P2-12 `TODO` y nombres | Sí | Ver más abajo |
| P2-14 Verificación | Sí | — |

#### P2-01 · `HEALTHCHECK` en el `Dockerfile` (13 de octubre)

- **REQ-P2-01-1.** La etapa de ejecución del `Dockerfile`, después de `USER cameia`, deberá declarar exactamente:

```
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --spider "http://127.0.0.1:${PORT:-${SERVER_PORT:-8082}}/actuator/health/liveness" || exit 1
```

- **Por qué esos valores.** Son los mismos que usan Cuentas y Entrevista; `liveness` no consulta la base de datos, de modo que una caída de PostgreSQL no reinicia el contenedor; `wget` viene en busybox y no se instala nada.
- **Verificación.** `docker build -t cameia-perfil:p2 .` y `docker inspect --format "{{json .Config.Healthcheck}}" cameia-perfil:p2` muestra el comando. Si Docker no está disponible, se reporta como no ejecutado.

#### P2-04 · Modo de arranque y `docker-compose.yml` con `app` y `worker` (14 de octubre)

- **REQ-P2-04-1.** Cuando el modo sea «solo API», ningún `@RabbitListener` deberá iniciarse y los endpoints `/api/v1/profiles/**` deberán responder.
- **REQ-P2-04-2.** Cuando el modo sea «solo consumidores», los controladores no deberán registrarse (`/api/v1/profiles/**` responde 404) y `/actuator/health/liveness` y `/actuator/health/readiness` deberán seguir respondiendo.
- **REQ-P2-04-3.** Cuando el modo sea «ambos», el comportamiento deberá ser el actual; es el valor por defecto, para que el despliegue actual no cambie.
- **REQ-P2-04-4.** Si el valor del modo no es uno de los tres, la aplicación deberá fallar al arrancar con un mensaje que nombre la propiedad y los valores admitidos.
- **REQ-P2-04-5.** El `docker-compose.yml` deberá tener los servicios `app` (solo API, contenedor `cameia-perfil-app`, puerto 8082, que es el que usa el Gateway) y `worker` (solo consumidores) con la misma imagen.
- **Casos borde.** Valor ausente, vacío, en minúsculas y mayúsculas, desconocido; el consumidor no consume cuando el modo es «solo API»; los controladores no responden cuando el modo es «solo consumidores».
- **Bloqueada por PD-04.**

#### P2-06 · RabbitMQ de Perfil (16 de octubre)

- **REQ-P2-06-1.** Los nombres de la cola, el intercambio y la clave de enrutamiento deberán leerse de propiedades tipadas y validadas, sin valor por defecto, de modo que la aplicación no arranque si faltan.
- **REQ-P2-06-2.** Cuando un mensaje falle tras agotar los reintentos, deberá ir a una cola de errores declarada junto a la cola principal.
- **REQ-P2-06-3.** Cuando un mensaje falle, deberá reintentarse con espera creciente un número acotado de veces.
- **REQ-P2-06-4.** Cuando el mismo mensaje llegue dos veces, el consumidor deberá procesarlo una sola vez (idempotencia por identificador de mensaje).
- **REQ-P2-06-5.** Los `TODO` de `RabbitConfig` y de `AccountDeletedListener` deberán resolverse con esta pieza y retirarse del código.
- **Casos borde.** Mensaje duplicado, mensaje mal formado, falla en el último reintento, broker caído al arrancar.
- **Bloqueada por PD-05.**

#### P2-08 · Cobertura global por encima de 70 % (19 y 20 de octubre)

- **REQ-P2-08-1.** Cuando termine la pieza, la cobertura global de líneas y de ramas de Perfil medida con `./mvnw.cmd clean verify` deberá ser mayor que 70 %; la cobertura de partida y la final se reportan con números.
- **REQ-P2-08-2.** Cada clase nueva o modificada por esta pieza deberá quedar con cobertura de líneas y de ramas ≥ 90 %, y cada línea o rama sin cubrir se reporta con su razón.
- **Orden de trabajo.** Las clases con menor cobertura primero, según `target/site/jacoco/jacoco.csv`; las pruebas siguen el estándar (un caso por camino y por rama, valores literales, nombres de la decisión D-14).

#### P2-09 · Testcontainers en Perfil (20 y 21 de octubre)

- **REQ-P2-09-1.** El `pom.xml` deberá declarar, en alcance de prueba, `spring-boot-testcontainers`, `testcontainers-postgresql` y `testcontainers-junit-jupiter`, las mismas que usa Cuentas.
- **REQ-P2-09-2.** Cada prueba que necesite base de datos deberá declarar `@Testcontainers` y `@ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")`, y arrancar el servidor en puerto aleatorio.
- **REQ-P2-09-3.** Las pruebas de base de datos no deberán depender del PostgreSQL de `docker-compose.yml`; el servicio `db` de ese archivo queda solo para uso local.
- **REQ-P2-09-4.** El informe de `clean verify` deberá mostrar `Skipped: 0`: una prueba omitida por falta de Docker no cuenta como verificada.
- **REQ-P2-09-5.** El servicio `verify` de `docker-compose.yml` deberá seguir funcionando con Testcontainers (montando el socket de Docker) o retirarse y documentarse `./mvnw.cmd clean verify` como único camino (PD-09).
- **Casos borde.** Docker apagado; imagen sin descargar; dos ejecuciones simultáneas en la misma máquina; migraciones Flyway sobre la base vacía.
- **Bloqueada por PD-09.**

#### P2-12 · `TODO` de producción y nombre de la prueba de arquitectura (20 de octubre)

- **REQ-P2-12-1.** El código de producción de Perfil no deberá contener `TODO`: la lista sale de `git grep -n "TODO" -- src/main`.
- **REQ-P2-12-2.** Cuando el código o el contrato ya resuelva lo que un `TODO` preguntaba (por ejemplo, el encabezado de identidad es `X-User-Id`), el comentario deberá retirarse y el hecho deberá quedar en el Javadoc. Cuando siga siendo una duda abierta, deberá registrarse en la sección 10 del `CLAUDE.md` con su responsable y su evidencia, y el comentario se retira.
- **REQ-P2-12-3.** La prueba `ArquitecturaTest` deberá llamarse `LayeredArchitectureTest`, y los documentos que la mencionen deberán actualizarse.
- **Bloqueada por P2-06** para los `TODO` de RabbitMQ.

Pendientes propios:

- **PD-09.** Cómo corre la verificación en contenedor cuando Perfil use Testcontainers: montar el socket de Docker en el servicio `verify` (recomendado) o retirar `verify`. Responsable: Backend, 20 de octubre. Bloquea P2-09.
- **PD-10.** Si se pide a DevOps activar `SPRING_PROFILES_ACTIVE=prod` en el despliegue de Perfil, hoy sin perfil. Sin ese cambio, Swagger queda cerrado por el valor por defecto apagado, pero una variable `API_DOCUMENTATION_ENABLED=true` en Cloud Run lo abriría; con el cambio, el cierre es rígido como en Cuentas. Recomendación: pedirlo en el mismo mensaje del pedido 14. Responsable: Backend, 19 de octubre. Bloquea el cierre rígido de P2-07 en Perfil.

## 8. Casos de verificación

Cada caso se ejecuta con salida real y se reporta en el PR. Los comandos son de Git Bash sobre la raíz del repositorio.

| ID | Qué comprueba | Comando | Resultado esperado |
|---|---|---|---|
| V-01 | `docs/estandar-backend.md` idéntico en los cuatro (REQ-DOC-03) | `git show HEAD:docs/estandar-backend.md \| sha256sum` en cada repositorio | La misma suma en los cuatro |
| V-02 | `CONTRIBUTING.md` idéntico en los cuatro (REQ-DOC-08) | `git show HEAD:CONTRIBUTING.md \| sha256sum` en cada repositorio | La misma suma en los cuatro |
| V-03 | Diez secciones del `CLAUDE.md` (REQ-DOC-01) | `grep -nE '^## ' CLAUDE.md` | Exactamente 10 líneas, con los títulos del REQ-DOC-01 |
| V-04 | `AGENTS.md` de una línea (REQ-DOC-02) | `cat AGENTS.md` | La línea exacta del REQ-DOC-02 |
| V-05 | Sin nombres de personas, hojas personales ni reglas obsoletas (REQ-DOC-11) | `git grep -nEi "Sof[ií]a\|Vela\|Paula\|Bitacora_Codigo\|Entregables\|CA-[0-9N]\|RFC ?7807\|<tipo>/CM\|snake_case" -- "*.md" ":!specs"` | Sin coincidencias, salvo `snake_case` de tablas y columnas de base de datos |
| V-06 | Sin datos que envejecen (REQ-DOC-11) | `git grep -nEi "[0-9]+ pruebas\|hoy son\|27/27\|adoptad[oa] el" -- "*.md" ":!specs"` | Sin coincidencias |
| V-07 | Enlaces relativos válidos (REQ-DOC-12 y REQ-DOC-13) | Comprobación de enlaces de las tarjetas de verificación (T-08, T-13 y T-15) | Cero enlaces rotos |
| V-08 | Tipo `perf` ausente de los documentos (H-02) | `git grep -nE "perf" -- "*.md" ":!specs"` | Sin coincidencias de tipo de commit o de PR |
| V-09 | Título y rama válidos para el validador de PR | `python .github/scripts/pr_policy.py <json>` con el título del PR y la rama `CM-283-alinear-estandar` | `errors` vacío |
| V-10 | Tamaño del PR (REQ-DOC-15) | `git diff --shortstat origin/develop` | Agregadas + eliminadas ≤ 1000 |
| V-11 | Ningún documento enlaza uno retirado (REQ-DOC-13) | `git grep -nE "guidelines\.md\|AMBIGUIDADES\.md\|reglas-codigo-backend\|handoff-implementacion\|docs/sdd\.md" -- "*.md"` | Sin coincidencias, salvo en la propia spec |
| V-12 | Afirmaciones del `CLAUDE.md` comprobadas contra el código | Para cada puerto, perfil, ruta, tabla y componente del `CLAUDE.md`, el comando `git grep` de la tarjeta T-05 | Cada afirmación tiene su línea de código |
| V-13 | La Parte 1 no cambia código | `git diff --name-only origin/develop` | Solo archivos `*.md`, `CONTRIBUTING.md`, `docs/**` y `specs/**` |

Casos de la Parte 2: los de cada pieza en la sección 7.

**Parte 1 terminada** = V-01 a V-13 en verde con su salida real en el PR de cada repositorio. **Secciones comunes:** las secciones que repiten las cuatro specs se cambian en las cuatro en el mismo bloque de trabajo; V-01 compara el estándar, no la redacción de las specs.

**V-14 (solo lectura, no condiciona la Parte 1).** Con el servicio en local, `curl -si` a una ruta que responda un error (por ejemplo, un 404 o un 422) y anotar el `Content-Type`. Si no declara `charset=UTF-8` (ASVS 4.1.1), el hallazgo se registra con su evidencia y se corrige en la primera tarea de código de este servicio, no en CM-283.

## 9. Fuera de alcance

- Cambios de lógica de negocio y de contrato público (los cambios aditivos de error se adoptan en las tareas de código de cada servicio, no aquí).
- El workflow de despliegue, los recursos de Cloud Run y todo lo de `/.github/`: son de DevOps y se piden por separado. La única petición que esta spec genera es la de la pieza P2-07 de Entrevista.
- `cameia-web`, `cameia-infra` y `cameia-metricas`.
- Renombrar paquetes, carpetas de spec o pruebas existentes (D-11, D-14 y REQ-DOC-14).
- Llevar la cobertura de Perfil al 90 % (la Parte 2 la lleva a más de 70 %).

## 10. Preguntas respondidas y pendientes

### Respondidas (Backend, 5 de octubre de 2026)

| # | Pregunta | Respuesta |
|---|---|---|
| Q-01 | Regla de commit en los cuatro `CONTRIBUTING.md` | Los cuatro iguales, con la clave Jira (D-01) |
| Q-02 | Dónde vive la spec | Una completa en cada repositorio (D-02) |
| Q-03 | Cómo trabajar sin tocar las ramas abiertas | `git worktree` (D-03) |
| Q-04 | Contenido de `docs/errores.md` | Solo los códigos que el código emite hoy (D-04) |
| Q-05 | ADR del código de error y del `requestId` | En la Parte 1 (D-05) |
| Q-06 | `[IA-ASISTIDO]` en el commit | Solo en el título del PR (D-06) |
| Q-07 | Documento de reglas del equipo de Perfil | Se retira; una sola fuente de verdad (D-07) |
| Q-08 | `guidelines.md` | Se retira de Cuentas y Entrevista y lo que tenga de más pasa al estándar (D-08) |
| Q-09 | Perfil de producción, bandera de Swagger y puertos | `prod`, `API_DOCUMENTATION_ENABLED` y puerto único en los cuatro; sin romper nada y avisando a DevOps solo lo necesario (D-09) |
| Q-10 | ArchUnit en Entrevista | Sí, en la Parte 2 (D-10) |
| Q-11 | Paquete base de Perfil | Se conserva y se documenta (D-11) |

### Pendientes

| ID | Qué falta decidir | Responsable | Fecha | Recomendación | Bloquea |
|---|---|---|---|---|---|
| PD-01 | Si se aprueba el plugin de formato (dependencia nueva) y cuál | Backend | 13 oct | Decidir con una comparación de alternativas reales y su compatibilidad con Spring Boot 4.1.1 | P2-03 |
| PD-02 | Formato del `X-Request-Id` entrante y nombre de la clave del `MDC` | Backend | 15 oct | Aceptar `^[A-Za-z0-9._-]{1,64}$`; si no cumple, generar un UUID v4; clave `requestId` | P2-05 |
| PD-04 | Nombre y valores de la propiedad del modo de arranque de Perfil | Backend | 13 oct | Una propiedad con tres valores: solo API, solo consumidores y ambos | P2-04 |
| PD-05 | Nombres de colas, cola de errores, número de reintentos y espera de RabbitMQ en Perfil | Backend con la arquitectura del proyecto | 16 oct | Tres intentos con espera creciente, igual que la regla del LLM | P2-06 |
| PD-06 | Redondeo del umbral de JaCoCo: al entero inferior o a dos decimales | Backend | 22 oct | Al entero inferior | P2-10 |
| PD-07 | Tarea en Jira para llevar Perfil de más de 70 % a 90 % | Product Owner | 6 oct | Pedirla por la comunicación al Product Owner | Meta de cobertura de Perfil |
