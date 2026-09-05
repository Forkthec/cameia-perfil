# Handoff — implementación de las Historias de Usuario de `cameia-perfil`

> **Cómo se usa:** se pega este archivo completo al inicio de una conversación nueva.
> Sustituye a `04092026_v1_handoff-cm-102.md`, que describía la construcción de la base técnica
> y ya está terminada. Este describe **lo que sigue: escribir las HU**.

- **Versión:** 1.1
- **Fecha:** 5 de septiembre de 2026
- **Responsable humana:** Ana Sofía
- **Estado:** base técnica construida, verificada y **publicada en la rama `CM-102-base-tecnica`**,
  pendiente de Pull Request hacia `develop`. Ninguna HU implementada.

---

## 0. LO PRIMERO — las reglas que mandan

### 0.1 Nada sale del computador sin autorización explícita

Prohibido, salvo que Ana Sofía lo pida en ese momento y con esas palabras:
`git push`, abrir un Pull Request, hacer merge, cualquier comando `gh` que escriba en GitHub,
y tocar `main` o `develop`. **Commits locales sí.**

La autorización para publicar `CM-102-base-tecnica` ya se dio y se usó. **No se extiende a las
ramas de las HU:** cada una se vuelve a pedir.

### 0.2 No asumir. Preguntar.

1. Si falta un dato, se pregunta. No se elige un valor por defecto en silencio.
2. Si un documento contradice a otro, se detiene y se avisa.
3. Si algo se hace "porque así se suele hacer", se dice en voz alta que es una suposición.
4. **Un `TBD` del equipo nunca se cierra escribiendo código.**
5. Prohibido inventar comandos, versiones, puertos o rutas. **Si no se ejecutó, no se escribe
   como si se hubiera ejecutado.**

### 0.3 Explicar mientras se trabaja

En español, dirigido a alguien que está aprendiendo la arquitectura y que tiene que sustentar
esto en un Code Walkthrough. Qué archivo, por qué ahí, de qué documento sale la decisión.

### 0.4 La norma de código de este repositorio es `AGENTS.md`

`Repo\cameia-perfil\AGENTS.md`, **722 líneas, se lee completo antes de escribir la primera
línea de código.** Contiene la estructura, los 17 componentes del C3 mapeados a clase y sprint,
los nombres por capa, el patrón de persistencia de tres piezas, los límites de Clean Code, las
prácticas de Spring Boot (§6.1) y los 12 antipatrones que bloquean un PR.

### 0.5 La bitácora de IA se llena el mismo día

`Entregables\05092026_01_BitacoraIA_Codigo_E2.xlsx`, hoja `Bitacora_Codigo_E2_Sofia`.
Filas 4 a 12 llenas (`S-IA-C-1` a `S-IA-C-9`). **La siguiente entrada va en la fila 13**, que ya
trae precargado el ID `S-IA-C-10`: el ID no se escribe. La columna K, "Cambios humanos", es
obligatoria cuando la IA generó código. La hoja `Tabla11_Codigo_E2_Sofia` no se toca.

---

## 1. Contexto

**CAMEIA** — plataforma web de práctica de entrevistas laborales con IA. Proyecto II,
Universidad del Cauca, Grupo 4. Sprint 1. Jira: `f0rktech.atlassian.net`.

Microservicios Java 21 / Spring Boot 4.1.1 detrás de un API Gateway, cada uno con su base
PostgreSQL, comunicados por RabbitMQ.

**Ana Sofía es responsable del Microservicio de Perfil Profesional (`cameia-perfil`).**

- **Es dueño de:** perfiles profesionales, experiencia, educación, habilidades, roles objetivo y
  la revisión humana de todo lo que sugiere la IA.
- **NO es dueño de:** credenciales, pagos, sesiones de entrevista ni el ledger de consumo.
- **No verifica tokens** (eso es del Gateway) y **no calcula cuota** (consume la réplica).

```text
C:\Users\Ana_Sofia\OneDrive\Documentos\UNI\Proyecto 2 E2\
├── Obligatorio\     LA ENTRADA. Solo se lee, NUNCA se modifica.
│   └── Reglas_Codigo_Prs\03092026_paquete_dev\   <- los 16 archivos de arquitectura
├── Entregables\     LA SALIDA hacia el equipo, y la bitácora de IA
├── Memoria\         Continuidad entre chats (este archivo vive aquí)
└── Repo\cameia-perfil\   EL CÓDIGO
```

---

## 2. Estado real del repositorio — verificado el 5-sep-2026

```text
Rama:     CM-102-base-tecnica       <- convención confirmada por el equipo, ver §6.1
Upstream: origin/CM-102-base-tecnica
Árbol:    limpio
```

**18 commits** por encima de `origin/develop`, rebasados sobre el `develop` actual (`22aa21d`).
En `origin` hay `main`, `develop` y esta rama. `CA-99-configuracion-base` ya se eliminó tras
integrarse, que es lo que manda la estrategia de branching.

### Lo que ya existe y funciona

```text
pom.xml                  Java 21, Spring Boot 4.1.1, 9 dependencias justificadas una a una
mvnw / mvnw.cmd          wrapper only-script, sin .jar (choca con .gitignore si lo trae)
.env.example             catálogo de variables, valores sensibles vacíos
Dockerfile               multi-etapa: build con Maven+JDK21, runtime solo JRE, usuario no root
docker-compose.yml       db + app + verify (verify con perfil "tools")
docs/DOCKER.md           convenciones de contenedores
README.md · AGENTS.md · CONTRIBUTING.md

src/main/java/co/edu/unicauca/cameia/perfil/
  PerfilApplication.java
  infrastructure/config/OpenApiConfig.java
  presentation/advice/ApiExceptionHandler.java     <- vacío a propósito
src/main/resources/application.yml
src/test/java/.../ArquitecturaTest.java            <- 8 reglas ArchUnit
src/test/java/.../PerfilApplicationTest.java
20 archivos .gitkeep en las carpetas vacías
```

### Verificación ejecutada el 5-sep-2026 — salida real

```text
docker compose run --rm verify
  Tests run: 1, Failures: 0 -- PerfilApplicationTest
  Tests run: 8, Failures: 0 -- ArquitecturaTest
  Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS

docker compose up -d --build   (levanta solo db y app)
  GET /actuator/health            200  {"groups":["liveness","readiness"],"status":"UP"}
  GET /actuator/health/readiness  200
  GET /swagger-ui.html            302  -> /swagger-ui/index.html
  GET /v3/api-docs                200  openapi 3.1.0, paths {}
  GET /api/v1/profiles            404  application/problem+json
  GET /actuator/env               404  (correcto: no expuesto)
```

**Sin secretos**, ni en el árbol ni en el historial completo. Comprobado con `git log --all -p`.

---

## 3. La estructura de paquetes — NO se cambia sin decisión

Sale de la hoja "Paquetes - Perfil Profesional" del diagrama. Verificada carpeta por carpeta
contra el XML: coincidencia exacta.

```text
co.edu.unicauca.cameia.perfil
├── presentation      controller · dto · advice
├── application       service · command
├── domain            model · service · policy · port · event · exception
└── infrastructure    persistence(entity · repository)
                      messaging(consumer · publisher · payload)
                      ia · config
```

**Tres ausencias deliberadas que hay que saber defender:**

- **Sin `infrastructure/client`.** El diagrama de Perfil no lo dibuja; Cuentas, Entrevista y
  Empleo sí. Perfil habla con LLM (`ia`), no con Firebase ni Wompi.
- **Sin `persistence/mapper`.** No aparece en ninguna hoja del diagrama. **Esto NO es una
  desobediencia:** las reglas de código §5.5.2 autorizan literalmente la opción (a), métodos
  privados `toDomain`/`toEntity` dentro del adaptador, que es lo que se hace. Si alguien
  pregunta, se cita "§5.5.2, opción (a)".
- **`domain/service` existe pero está vacío.** El C3 de Perfil no dibuja ningún servicio de
  dominio. Si aparece la necesidad, **se pregunta antes de crear la clase.**

---

## 4. ANTES de escribir la primera clase — cuatro cosas sin resolver

**Ninguna se cierra escribiendo código.** Son las que bloquean CM-16.

### 4.1 Perfil no tiene modelo de dominio en el C4 — lo más importante

El C4 tiene "Clases Dominio" **solo para Entrevista**. Perfil solo tiene "Clases JPA". Y las
notas del propio C4 **prohíben** copiar el modelo JPA y llamarlo dominio (antipatrón 2).

Hay que **derivar** los agregados `PerfilProfesional` y `HojaDeVida`, sus objetos de valor y sus
invariantes, desde el C3, el diagrama JPA y el glosario — **y esa derivación la revisa
arquitectura antes de codificar.** No es un paso opcional ni algo que se improvise en el
`AppService`.

Pista que ya da el C3 y que cambia el diseño: *"Invariantes del Perfil Profesional y de los roles
objetivo que contiene"* → **los roles objetivo viven DENTRO del agregado**, no son un agregado
aparte. Por eso la regla de 1 a 5 roles y la de no eliminar el último se validan en el agregado,
no en el servicio de aplicación.

### 4.2 Falta el campo `estado` en el C4

El C4 de `PerfilProfesional` tiene `estadoRevision` pero **no `estado`**. El backlog exige
`contexto_profesional.estado` con `IN_PROGRESS → IN_REVIEW/COMPLETED`, y
`GET /profiles?status=COMPLETED` filtra por él. **Todas las HU dependen de este campo.**

### 4.3 Cómo llega la identidad del usuario

El backlog dice "verifica ownership del `profile_id` (JWT)"; el C3 dice que **el Gateway**
verifica el token. Falta definir si llega por cabecera propagada o si el microservicio valida.
De eso depende si entra `spring-boot-starter-oauth2-resource-server` al `pom.xml`.

### 4.4 La primera migración de Flyway

`src/main/resources/db/migration` está vacío. Con `ddl-auto=validate`, en cuanto exista la
primera entidad JPA hace falta el `V1__*.sql` o **el servicio no arranca**. La migración se
escribe en el mismo commit que la entidad, nunca después.

---

## 5. Cómo se escribe el código — resumen operativo

**Esto es un resumen. La norma completa es `AGENTS.md`, y hay que leerla.**

### 5.1 El principio rector

> **El código no inventa vocabulario. Lo toma del glosario.**

Si un concepto no está en `03092026_v3_glosario.md`, no se le pone nombre en el código todavía:
se abre la discusión, no el `.java`.

### 5.2 Idioma — DECIDIDO el 5 de septiembre de 2026

**Todo el código en inglés. Todo lo que explica el código, en español.** Lo decidió Ana Sofía y
aplica desde la primera clase. Está desarrollado en `AGENTS.md §2`, con la tabla completa.

En inglés: paquetes, clases, interfaces, métodos, variables, constantes, nombres de las clases de
prueba, rutas HTTP y variables de entorno.
En español: comentarios y Javadoc, `@DisplayName`, mensajes de log, mensajes de excepción, README,
descripciones de OpenAPI y mensajes de commit y de PR.

> **La regla de mitigación es OBLIGATORIA, no un adorno.** Esta decisión se aparta de los nombres
> en español del C3 y el C4, que las reglas del equipo §5.4 marcan `CONFIRMADO`. Cada clase de
> dominio lleva un Javadoc que dice de qué término del glosario y del C4 sale, y la tabla de
> equivalencias de `AGENTS.md §2` se actualiza **antes** de usar un concepto nuevo. Sin eso se
> pierde la trazabilidad con el diagrama, que es lo que preguntan en el Code Walkthrough.

Lo que **no se traduce en ningún caso**: los códigos de enum y estados (`PENDING`,
`IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`, `MANUAL`, `AI_SUGGESTED`, `AI_EDITED`), porque son el
contrato con Frontend y con Entrevista; y los nombres de tablas y columnas, que van en español
`snake_case` porque salen del DDL.

### 5.3 Persistencia: son TRES piezas, no una

Un `JpaRepository` suelto **no** es el repositorio del dominio.

| # | Pieza | Dónde |
|---|---|---|
| 1 | **Puerto** — interfaz del dominio, sin una importación de Spring ni JPA | `domain/port/` |
| 2 | **Spring Data** — `extends JpaRepository<…Entity, UUID>` | `infrastructure/persistence/repository/` |
| 3 | **Adaptador** — implementa el puerto usando el de Spring Data | `infrastructure/persistence/repository/` |

`application.service` inyecta **el puerto**, nunca el adaptador ni el `JpaRepository`. El mapeo
se escribe a mano; prohibido cualquier framework de mapeo automático. **Hay una regla de ArchUnit
que hace fallar la suite si se incumple.**

### 5.4 Prácticas de Spring Boot — `AGENTS.md §6.1`

Lo que cambia respecto a lo obvio:

- Dependencias `private final`, por constructor. Con un solo constructor, **sin `@Autowired`**.
- **`package-private` por defecto** en controllers, `@Configuration`, `@Bean` y `@Service`.
  `public` solo el dominio, los puertos, los DTO y los `Command`.
- `@ConfigurationProperties` **validadas**, nunca `@Value` disperso. Y si el valor sale de un
  `TBD`, no se le pone valor por defecto: que falle al arrancar.
- `@Transactional(readOnly = true)` en lecturas, `@Transactional` en escrituras, **solo** en
  `application.service`.
- **Ninguna llamada a un LLM ni a RabbitMQ dentro de una transacción abierta.**
- `ResponseEntity<T>` con el código explícito: `201 Created` en `POST /profiles`.
- Pruebas de integración con `@SpringBootTest(webEnvironment = RANDOM_PORT)`. El puerto 8082 es
  provisional: una prueba clavada a él se rompe cuando el equipo asigne el real.
- SLF4J siempre. **Nunca se registra el contenido de un CV, un resumen ni ningún dato del
  perfil**: son datos personales. Se registra el identificador y el resultado.

### 5.5 Clean Code — límites que se revisan en el PR

Método ≤ 20 líneas (≤ 15 en agregados y objetos de valor). Máximo 3 parámetros. Máximo 2 niveles
de anidamiento. Complejidad ciclomática ≤ 8. Clase ≤ 200 líneas. Objetos de valor inmutables
(`record`). **Sin setters públicos en el dominio.** Sin `TODO` sin clave de Jira.

### 5.6 Los antipatrones que bloquean un PR

Modelo de dominio anémico · usar la entidad JPA como dominio · anotar el agregado con `@Entity` ·
lógica de negocio en el controlador · devolver la entidad JPA o el agregado desde el controlador ·
clave foránea hacia otro contexto · **Perfil calculando cuota** · `Map<String, Object>` como
respuesta o payload · **convertir un `TBD` en constante** · guardar el binario del CV ·
persistir un dato de IA sin `procedencia` y `estadoRevision` · **inyectar un `JpaRepository`
desde `application`**.

### 5.7 Pruebas

| Capa | Tipo | Regla |
|---|---|---|
| `domain` | unitaria pura | **Sin contexto de Spring.** Si lo necesita, no es dominio |
| `application` | unitaria con dobles | Los puertos se sustituyen por mocks |
| `infrastructure.persistence` | integración | Contra **PostgreSQL real, nunca H2** |
| `presentation` | contrato | `@WebMvcTest` |

Clase `<Class>Test` / `<Class>IT`, método `<method>_should<Result>_when<Condition>`,
`@DisplayName` en español. **Cada HU llega con prueba positiva y negativa de cada regla de
negocio que toca.** Datos sintéticos, nunca reales.

```bash
docker compose run --rm verify     # compila y ejecuta la suite, sin instalar Java ni Maven
docker compose up -d --build       # levanta db + app
docker compose down -v             # apaga y borra datos
```

---

## 6. Lo que está abierto — no cerrar por cuenta propia

### 6.1 Nombre de rama y título de PR — RESUELTO el 5 de septiembre de 2026

```text
rama:    CM-<numero>-<descripcion-kebab-case>      sin prefijo de tipo
título:  CM-NNN | tipo(scope): resultado           + [IA-ASISTIDO] si hubo IA
```

Estuvo en disputa. **El equipo lo unificó en `CM`**: Paula actualizó el `README.md` en el
[PR #2](https://github.com/Forkthec/cameia-perfil/pull/2) — *"Update branch naming conventions in
README"*, commit `22aa21d` sobre `develop`.

> **La estrategia de branching, `github.txt` y las reglas de código §5.8 y §14 todavía dicen
> `CA-<numero>` y NO se han actualizado.** No son la decisión vigente. Si alguien las cita, la
> respuesta es el PR #2.

**Leccion operativa:** esta convención se comprobó una vez contra una copia local del remoto con
doce horas de antigüedad, y por eso se llegó a la conclusión contraria. **`git fetch` antes de
nombrar una rama o abrir un PR:** las convenciones del equipo cambian en `develop`, no en los
documentos de arquitectura.

### 6.2 BLOQUEANTE para publicar — el puerto

No existe ningún documento del equipo que fije los puertos. La única fuente que asigna alguno es
la plantilla del Gateway (`PERFIL_BASE_URL=http://localhost:8082`), y el documento multirepo
aclara que esos puertos *"no constituyen una decisión arquitectónica aprobada"*. Es `DEV-IN-05`,
sin responder.

`cameia-perfil` usa **8082 como valor provisional**, marcado con la palabra `PROVISIONAL` en los
seis archivos versionados que lo mencionan. Para encontrarlos: `grep -rn "PROVISIONAL" .`
En `.env.example`, `SERVER_PORT` se entrega **vacío**.

### 6.3 Decisiones abiertas que afectan al código

| Tema | Estado |
|---|---|
| **Modelo de dominio de Perfil** | No existe en el C4. Hay que derivarlo y que arquitectura lo revise |
| Campo `estado` | El C4 no lo tiene, el backlog lo exige |
| Identidad del usuario | Define si entra una dependencia de seguridad |
| **Versionado nativo de API** (Spring 7) | Verificado que existe en `spring-web` 7.0.9, pero el contrato del equipo fija `/api/v1/` en la URL. Decisión de arquitectura |
| **Testcontainers** | La guía de Spring Boot lo pide; no está en el `pom.xml`. Preguntar si va en los 6 repos o en ninguno |
| **i18n con `ResourceBundles`** | Choca con la regla de mensajes en español. Sin HU que lo pida |
| `persistence/mapper` | Opción (a) de §5.5.2 elegida. Se revisa si un adaptador se acerca a 200 líneas |
| Nombres de las colas | El C2 se contradice entre hojas |
| `API-TBD-05, 06, 07, 09, 18` | Decisiones de producto que afectan a CM-16..CM-20 |

---

## 7. Las Historias de Usuario — el trabajo que sigue

**Cada HU es una tarea de Jira aparte, va en su propia rama y se planifica igual: leer,
preguntar, ejecutar por bloques, explicar.** Orden por dependencias funcionales:

### CM-16 · HU-2.2 — Selección del método de configuración (3 pts)

Es la puerta de entrada: sin esto no hay perfil que editar.

- `POST /api/v1/profiles`, responde **`201 Created`**.
- Entrada: método `MANUAL` o `AI_SUGGESTED`.
- El backend **revalida el límite de perfiles por plan** antes de insertar
  (`entitlements_perfil.max_contextos >= 1`). **Perfil no calcula la cuota: consume la réplica.**
- Crea el perfil en estado `IN_PROGRESS`. `origen_dato` registra `MANUAL` o `AI_SUGGESTED`.
- **`TBD`:** la obligatoriedad de `nombre_perfil` (`VARCHAR(120)`) — el backlog deja la frase
  incompleta. Es decisión de Product Owner.
- Prototipo `PRT-02.02`.

### CM-17 · HU-2.3 — Información general y resumen (3 pts)

- `PATCH /api/v1/profiles/{profile_id}`
- Columnas: `nombre_perfil VARCHAR(120) NOT NULL`, `resumen_profesional TEXT NULL`,
  `resumen_procedencia CHECK IN ('MANUAL','AI_SUGGESTED','AI_EDITED')`,
  `resumen_ejecucion_origen_id UUID NULL`.
- Constraint `ck_contexto_resumen_origen`: al editar un campo `AI_SUGGESTED` pasa a `AI_EDITED`.
- Se puede guardar como borrador sin campos obligatorios; la exigencia es al finalizar.
- Verifica ownership del `profile_id` y sanitiza XSS. Depende de CM-16.

### CM-18 · HU-2.4 — Experiencia laboral y educación (5 pts)

- `PATCH /api/v1/profiles/{profile_id}/experience` y `.../education`
- `ck_experiencia_estado_fecha`: `ACTUAL` o `FIN_DESCONOCIDO` → `fecha_fin IS NULL`;
  `FINALIZADA` → `fecha_fin NOT NULL`. Constraint análogo para educación.
- Frontend valida `fecha_inicio <= fecha_fin`; el backend **revalida**.
- **`API-TBD-06` bloquea:** no está definido si el `PATCH` reemplaza la colección, actualiza un
  elemento o hace upsert. **Decidir antes de codificar.** Depende de CM-17.

### CM-19 · HU-2.5 — Habilidades, expectativas y finalización (5 pts)

- `PATCH /api/v1/profiles/{profile_id}/skills` y `POST /api/v1/profiles/{profile_id}/finalize`
- `habilidad_perfil` con clave alterna `(contexto_profesional_id, habilidad_id)`.
- Al finalizar: valida campos mínimos **y que exista al menos 1 rol objetivo**.
  Transición `IN_PROGRESS → IN_REVIEW` o `COMPLETED`.
- **`API-TBD-05` bloquea:** no existe endpoint para las expectativas, aunque la HU las menciona.
- **`API-TBD-18`:** la HU dice redirigir a HU-2.7 si faltan roles, pero los roles se gestionan en
  HU-2.10/2.11. La referencia está mal.
- **`API-TBD-09`:** el backlog usa `COMPLETE`, el glosario y el DDL usan `COMPLETED`.
  Recomendación: `COMPLETED`. Depende de CM-17, CM-18 y CM-20.

### CM-20 · HU-2.11 — Gestión de roles objetivo (5 pts)

- `GET` y `POST /api/v1/profiles/{profile_id}/roles`
- `PATCH` y `DELETE /api/v1/profiles/{profile_id}/roles/{role_id}`
- Reglas duras, **y viven en el agregado, no en el `AppService`**: entre **1 y 5** roles por
  perfil; **no se puede eliminar el último**; sin duplicados (`uq_rol_objetivo_contexto_rol`).
- Editar un rol sugerido → `AI_EDITED`, conservando `ejecucion_origen_id`. Agregar a mano →
  `MANUAL`.
- **Sin ninguna invocación a IA en esta HU.**
- **`API-TBD-07`:** falta decidir si el recurso se llama `roles` o `target-roles`.
- Prototipo `PRT-02.07`.

### Las dos lecturas que consume Entrevista (HU-4.2)

- `GET /api/v1/profiles?status=COMPLETED` — perfiles completos del usuario autenticado.
  **Falta definir la paginación, y es una colección sin cota: hay que paginarla.**
- `GET /api/v1/roles/suggestions?q={texto}` — autocompletar rol objetivo.
  Falta límite, normalización y qué devolver cuando no hay resultados.

### Lo que NO entra en Sprint 1

Spring AI, Apache Tika y PDFBox (todo el procesamiento de CV es **Sprint 2**),
`spring-boot-starter-amqp` (RabbitMQ es `POSTERIOR`), Firebase Admin SDK (**nunca en Perfil**),
Wompi y Lombok.

---

## 8. Dónde está cada fuente

Todo cuelga de `Proyecto 2 E2\Obligatorio\Reglas_Codigo_Prs\03092026_paquete_dev\`
salvo lo indicado.

| Archivo | Para qué se consulta |
|---|---|
| `29082026_Paquetes.drawio.xml` | **La estructura de carpetas.** Hoja "Paquetes - Perfil Profesional" |
| `29082026_1_Componentes(C3).txt` | Hoja "C3 - Perfil Profesional": 17 componentes con tecnología |
| `29082026_1_Clases(C4).txt` | Hoja "Clases JPA - Perfil Profesional" |
| `27082026_01_Contenedores(C2).txt` | Colas y comunicación. **Se contradice entre hojas** |
| `30082026_01_Backlog.xlsx` | Requisitos. Leer con `openpyxl`, no como texto |
| `03092026_v3_glosario.md` | Lenguaje ubicuo. §6.2 Perfil, §6.5 estados |
| `03092026_v1_familias-endpoints-sprint-1.md` | §4 endpoints de Perfil; §10 los 18 `API-TBD` |
| `03092026_v1_estrategia-branching-pull-requests.md` | Ramas y PR |
| `03092026_v1_env-example-gateway-spring.md` | **De aquí sale el puerto 8082** |
| `…\Obligatorio\Clean Code\03092026_v1_reglas-codigo-backend-cameia.md` | **La norma del equipo.** §2 idioma, §3 paquetes, §5 nombres, §5.5.1 persistencia, §5.5.2 mapper |
| `Repo\cameia-perfil\AGENTS.md` | **La norma de ESTE repositorio.** Se lee completa |

**Cómo leer los diagramas:** son XML de draw.io y los datos están en los **atributos**, no en el
texto visible: `c4Name`, `c4Type`, `c4Technology`, `c4Description` de cada `<object>`.

---

## 9. Resumen en cinco líneas

1. La base técnica está **construida y verificada**: 9 pruebas en verde, servicio arriba,
   Swagger cargando, sin secretos.
2. **Publicado** en la rama `CM-102-base-tecnica`, pendiente de Pull Request hacia `develop`.
3. **Ninguna HU implementada**, y eso es lo correcto: la base técnica no implementa HU.
4. Antes de CM-16 hay que **derivar el modelo de dominio de Perfil**, que el C4 no tiene, y que
   arquitectura lo revise. El idioma ya está decidido (§5.2).
5. Sigue abierto el puerto (§6.2), marcado `PROVISIONAL` en los seis archivos que lo citan.
