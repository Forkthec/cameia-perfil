# Propuesta de convenciones de contenedores para CAMEIA

- **Versión:** 1.1 — matriz de puertos completa: aplicaciones, bases de datos e infraestructura
- **Fecha:** 4 de septiembre de 2026
- **Estado:** `PROPUESTO`. No aprobado por arquitectura ni por DevOps
- **Preparado por:** Ana Sofía, desde `cameia-perfil` (CM-102)
- **Destinatarios:** DevOps, arquitectura y responsables de cada repositorio
- **Responde parcialmente a:** `DEV-IN-02`, `DEV-IN-05`, `DEV-IN-07` y `DEV-IN-09`

---

## 1. Por qué existe este documento

Ningún documento del equipo cubre contenedores. No hay `reglas-docker`, no hay matriz de puertos
aprobada, y la única mención de puertos son ejemplos locales dentro de la plantilla `.env.example`
del API Gateway, que el propio documento multirepo marca como *"ejemplos locales que no
constituyen una decisión arquitectónica aprobada"*.

Mientras tanto cada repositorio va a necesitar levantarse. Si cada quien elige su puerto, su
nombre de imagen y su forma de arrancar la base de datos, integrar los ocho componentes va a
costar una tarde que nadie presupuestó.

Esto es una **propuesta para discutir**, escrita desde la implementación real de `cameia-perfil`.
No pretende cerrar nada: pretende que la discusión empiece con algo concreto encima de la mesa.

## 2. Matriz de puertos — `DEV-IN-05`

Derivada de `03092026_v1_env-example-gateway-spring.md`, que es la única fuente que hoy asigna
puertos a servicios.

**Esta es la tabla que hay que aprobar.** Cubre todo lo que ocupa un puerto en CAMEIA, no solo
las aplicaciones: si dos personas levantan sus microservicios a la vez y ambas bases publican el
5432, chocan.

### 2.1 Aplicaciones

| Repositorio | Puerto | Variable | Origen |
|---|---:|---|---|
| `cameia-web` | 5173 | `—` | Vite por defecto. **No está en ninguna plantilla**: hay que confirmarlo con Frontend |
| `cameia-gateway` | 8080 | `SERVER_PORT` | `SERVER_PORT=8080` de su propia plantilla |
| `cameia-cuentas` | 8081 | `SERVER_PORT` | `CUENTAS_BASE_URL=http://localhost:8081` |
| `cameia-perfil` | 8082 | `SERVER_PORT` | `PERFIL_BASE_URL=http://localhost:8082` |
| `cameia-entrevista` | 8083 | `SERVER_PORT` | `ENTREVISTA_BASE_URL=http://localhost:8083` |
| `cameia-auditoria` | 8084 | `SERVER_PORT` | `AUDITORIA_BASE_URL=http://localhost:8084` |
| `cameia-empleo` | **8085** | `SERVER_PORT` | **Propuesto aquí.** No aparece en ninguna plantilla — ver 2.4 |
| `cameia-voz` | 8000 | `APP_PORT` | `VOZ_BASE_URL=http://localhost:8000` |

### 2.2 Bases de datos

Cada contexto tiene base propia; el glosario prohíbe compartir tablas entre contextos. Estos son
los puertos **publicados hacia la máquina**: dentro de su contenedor todas siguen escuchando en
el 5432.

| Base del repositorio | Puerto publicado | Variable |
|---|---:|---|
| `cameia-cuentas` | 5433 | `POSTGRES_PORT` |
| `cameia-perfil` | 5434 | `POSTGRES_PORT` |
| `cameia-entrevista` | 5435 | `POSTGRES_PORT` |
| `cameia-auditoria` | 5436 | `POSTGRES_PORT` |
| `cameia-empleo` | 5437 | `POSTGRES_PORT` |

`cameia-gateway` no tiene base propia y `cameia-web` tampoco.

> **Nota sobre el estado actual:** `cameia-perfil` hoy publica el 5432 por defecto, no el 5434,
> porque mientras se trabaja en un solo repositorio no hay conflicto. Está parametrizado con
> `POSTGRES_PORT`, así que adoptar esta tabla no exige tocar ni una línea de código: basta un
> `.env`. Si el equipo la aprueba, cada repositorio cambia su valor por defecto.

### 2.3 Infraestructura compartida

No se levanta todavía —RabbitMQ no tiene contratos aprobados y Langfuse no está en Sprint 1—
pero conviene reservar los puertos ahora para que nadie los ocupe:

| Servicio | Puerto | Para qué |
|---|---:|---|
| RabbitMQ | 5672 | Protocolo AMQP |
| RabbitMQ | 15672 | Consola de administración |
| Langfuse | 3000 | Interfaz web |

Estos son los puertos por defecto de cada herramienta, no una decisión propia.

### 2.4 Lo que hay que resolver antes de aprobar

1. **`cameia-empleo` no está en el inventario de repositorios.** La propuesta multirepo enumera
   siete y no lo incluye, pero el C3, el C4, el diagrama de paquetes y las reglas de código sí lo
   contemplan — de hecho es el único con estilo de capas clásicas, sin `domain`. **O falta su
   plantilla `.env.example`, o el repositorio todavía no existe.** Aquí se le propone el 8085,
   pero eso depende de que exista.
2. **`cameia-web` no tiene puerto en ninguna fuente.** El 5173 sale del valor por defecto de
   Vite, y ni siquiera está confirmado que el proyecto use Vite: su plantilla ofrece Vite y
   Create React App como alternativas sin decidir. Lo tiene que confirmar Frontend.
3. **Rangos, para que no haya que volver a esta conversación.** Se propone reservar
   **8080–8099** para aplicaciones Java, **5430–5449** para bases de datos, y dejar el 8000 de
   Voz y el 5173 del Frontend como excepciones documentadas.

### 2.5 La regla que evita el 90% del problema

> **El puerto DENTRO del contenedor no se negocia; el puerto PUBLICADO sí.**

Cada servicio escucha siempre en el mismo puerto dentro de su contenedor. Lo que cambia es el
mapeo hacia la máquina, y se cambia con una variable de entorno:

```bash
SERVER_PORT=9090 docker compose up
```

Así, dos personas con conflictos distintos no necesitan tocar código, ni imágenes, ni pedirle
permiso a nadie. Y el día que el equipo apruebe otra matriz, adoptarla es editar un `.env`.

## 3. Nombres

| Elemento | Convención propuesta | Ejemplo |
|---|---|---|
| Imagen local | `<repositorio>:local` | `cameia-perfil:local` |
| Imagen publicada | `<registry>/<proyecto>/<repositorio>:<tag>` | **`TBD`**: no hay registry decidido |
| Proyecto de Compose | el nombre del repositorio | `name: cameia-perfil` |
| Contenedor | `<repositorio>-<servicio>` | `cameia-perfil-db`, `cameia-perfil-app` |
| Red | `<repositorio>-net` | `cameia-perfil-net` |
| Volumen de datos | `pgdata` dentro del proyecto | `cameia-perfil_pgdata` |
| Servicio de la app | `app` | — |
| Servicio de la base | `db` | — |
| Servicio de pruebas | `verify` | — |

Nombrar el proyecto de Compose importa: sin `name:`, Docker usa el nombre de la carpeta, y dos
personas que clonen en carpetas distintas obtienen volúmenes y redes distintos.

## 4. Estructura mínima por repositorio

```text
<repositorio>/
├── Dockerfile             imagen del servicio, multi-etapa
├── docker-compose.yml     entorno local: db + app + verify
├── .dockerignore          impide que .env, target/ y .git/ entren al contexto
└── docs/DOCKER.md         cómo se usa y qué decisiones quedaron abiertas
```

## 5. Reglas propuestas

### 5.1 Imagen

1. **Multi-etapa siempre.** La imagen final no lleva Maven, ni el JDK completo, ni el código
   fuente. Solo el JRE y el artefacto.
2. **Nunca `root`.** Un usuario sin privilegios, creado en el `Dockerfile`.
3. **Versiones fijas en los `FROM`.** `eclipse-temurin:21-jre-alpine`, no `latest`. Una imagen
   que cambia sola no es reproducible.
4. **Límite de memoria de la JVM.** `-XX:MaxRAMPercentage=75.0`. Sin esto la JVM lee la memoria
   de la máquina anfitriona y no la del contenedor, y en Cloud Run el proceso muere por exceso.
5. **Las pruebas no corren en el `Dockerfile`.** Necesitan la base de datos, que en tiempo de
   build no existe. Corren en un servicio de Compose que sí la tiene al lado.
6. **Ningún secreto horneado en la imagen.** Todo entra por variable de entorno.

### 5.2 Compose

7. **`healthcheck` en la base y en la aplicación.** `depends_on` a secas no espera a que
   PostgreSQL acepte conexiones: produce fallos intermitentes que se diagnostican mal.
8. **La aplicación usa `readiness`, no `health`, en su `healthcheck`.** `liveness` solo dice que
   el proceso vive; `readiness` incluye la base de datos.
9. **Red propia por repositorio.** Ninguno asume una red compartida que todavía no existe.
10. **Volumen para la caché de Maven.** La segunda ejecución de las pruebas no vuelve a
    descargar dependencias.
11. **Un servicio de pruebas a demanda**, que no se levanta con `up`.

### 5.3 Secretos

12. Las credenciales del `docker-compose.yml` son de **desarrollo local**, de contenedores que
    solo escuchan en `localhost`. Se ponen ahí para que `docker compose up` funcione sin
    configurar nada, y **no aplican a ningún otro ambiente**.
13. El `.env.example` se versiona con **todos los valores sensibles vacíos**. El `.env` real se
    ignora en `.gitignore` **y** en `.dockerignore`.
14. Se marca explícitamente en un comentario que esas credenciales son locales, para que nadie
    las confunda con un secreto filtrado durante una revisión.

### 5.4 Pruebas

15. Las pruebas de persistencia corren contra **PostgreSQL real, no H2**, como pide la sección 12
    de las reglas de código: el modelo usa `jsonb` y `@Lob`, que H2 no reproduce igual.

## 6. Lo que esta propuesta NO cubre

- **Registry, tags de publicación y despliegue a Cloud Run.** Es de DevOps.
- **CI.** El `README.md` de los repositorios pide activar CI solo con comandos comprobados. Los
  de `cameia-perfil` ya lo están; automatizarlos es otra tarea.
- **Un `docker-compose` que levante todos los repositorios juntos.** Requiere que existan todos y
  que la matriz de puertos esté aprobada. Es el paso natural siguiente.
- **RabbitMQ y Langfuse.** No se levanta infraestructura de algo que todavía no tiene contrato
  aprobado.
- **Requisitos de CPU y memoria (`DEV-IN-09`).** Sin medición real no se inventan.

## 7. Preguntas para el equipo

1. ¿Se aprueba la matriz de puertos de la sección 2 — aplicaciones, bases de datos e infraestructura — y los rangos reservados de 2.4?
2. ¿Qué pasa con `cameia-empleo`? ¿Existe el repositorio? ¿Le corresponde el 8085?
3. ¿Frontend confirma el 5173 y el bundler? Hoy su plantilla ofrece Vite y CRA sin decidir.
3. ¿Los contenedores son responsabilidad de cada repositorio o los centraliza DevOps?
4. ¿Hay registry decidido, y qué convención de tags se usa?
5. ¿Se adopta este documento como base de un `reglas-docker` para todos los repositorios?
6. ¿Quién arma el `docker-compose` integrador cuando existan más repositorios?

## 8. Implementación de referencia

Todo lo de este documento está implementado y **verificado** en `cameia-perfil`:
`Dockerfile`, `docker-compose.yml`, `.dockerignore` y `docs/DOCKER.md`.

Evidencia de que funciona:

```text
docker compose run --rm verify
  Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS

docker compose up -d
  cameia-perfil-db   Up (healthy)
  cameia-perfil-app  Up (healthy)   0.0.0.0:8082->8082/tcp

GET /actuator/health/readiness  ->  200  {"status":"UP"}
```
