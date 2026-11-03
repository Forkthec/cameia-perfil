# Docker en `cameia-perfil`

Este documento explica cómo se ejecuta el microservicio con Docker, qué convenciones se
adoptaron y **qué decisiones siguen abiertas**. Se escribió porque ningún documento del equipo
cubre todavía contenedores, puertos ni ejecución local: no hay `reglas-docker` ni matriz de
puertos aprobada.

> **Estado:** `PROPUESTO`. Nada de lo que hay aquí está aprobado por arquitectura ni por DevOps.
> Se propone para que el equipo lo revise, no para darlo por decidido.

---

## 1. Para qué sirve

Para que quien clone el repositorio pueda compilar, probar y levantar el servicio **sin instalar
Java, ni Maven, ni PostgreSQL**. Solo Docker.

Antes de esto, arrancar exigía: instalar un JDK 21, instalar Maven, instalar PostgreSQL 16, crear
a mano la base `cameia_perfil` y su rol, y escribir un `.env`. Cinco pasos manuales, cada uno una
oportunidad de que a dos personas les funcione distinto.

## 2. Los tres comandos

```bash
# Levantar la base de datos y el servicio
docker compose up --build

# Compilar y ejecutar toda la suite de pruebas
docker compose run --rm verify

# Apagar y borrar los datos
docker compose down -v
```

Con el servicio arriba:

| URL | Qué es |
|---|---|
| `http://localhost:8082/actuator/health` | Estado general |
| `http://localhost:8082/actuator/health/liveness` | ¿El proceso está vivo? |
| `http://localhost:8082/actuator/health/readiness` | ¿Puede atender? Incluye la base de datos |
| `http://localhost:8082/swagger-ui.html` | Interfaz de OpenAPI |
| `http://localhost:8082/v3/api-docs` | El JSON de OpenAPI, para generar clientes |

## 3. Qué hay dentro

| Archivo | Qué hace |
|---|---|
| `Dockerfile` | Imagen del servicio. Multi-etapa: compila con Maven + JDK 21, publica solo el JRE y el jar |
| `docker-compose.yml` | Entorno local: `db`, `app` y `verify` |
| `.dockerignore` | Evita que `.env`, `target/`, `.git/` y `application-local.yml` entren al contexto de build |

### Los tres servicios

- **`db`** — PostgreSQL 16. Base y rol propios del contexto, como exige el glosario: los
  contextos no comparten tablas ni claves foráneas.
- **`app`** — el microservicio. Espera a que `db` pase su *healthcheck* antes de arrancar.
- **`verify`** — contenedor de Maven a demanda. No se levanta con `up`. Ejecuta
  `mvn clean verify` contra la base real de al lado, que es lo que pide la sección 12 de las
  reglas de código: pruebas de persistencia contra **PostgreSQL real, no H2**.

---

## 4. Puertos

**Esto es lo que no está decidido en ningún documento del equipo.** `DEV-IN-05` pide "puerto,
contexto/base path y health check" y sigue sin respuesta.

Lo único que existe hoy es la plantilla del API Gateway
(`03092026_v1_env-example-gateway-spring.md`), que propone unos valores de ejemplo. El documento
multirepo aclara en su sección 4 que *"los puertos incluidos son ejemplos locales y no
constituyen una decisión arquitectónica aprobada"*.

Matriz derivada de esa plantilla, **`PROPUESTO`, para que el equipo la confirme**:

| Servicio | Puerto propuesto | Origen |
|---|---:|---|
| `cameia-web` | 5173 / 3000 | Bundler; no está en la plantilla |
| `cameia-gateway` | 8080 | `SERVER_PORT=8080` de su plantilla |
| `cameia-cuentas` | 8081 | `CUENTAS_BASE_URL` |
| **`cameia-perfil`** | **8082** | `PERFIL_BASE_URL` |
| `cameia-entrevista` | 8083 | `ENTREVISTA_BASE_URL` |
| `cameia-auditoria` | 8084 | `AUDITORIA_BASE_URL` |
| `cameia-voz` | 8000 | `VOZ_BASE_URL` |
| `cameia-empleo` | **sin asignar** | No aparece en ninguna plantilla |

> **Hallazgo:** `cameia-empleo` no está en el inventario de la propuesta multirepo, que enumera
> siete repositorios y no lo incluye — aunque el C3, el C4 y el diagrama de paquetes sí lo
> modelan, y las reglas de código lo listan en su alcance. O falta su plantilla, o el repositorio
> no existe todavía. Hay que preguntarlo.

**Convención propuesta:** el puerto **dentro** del contenedor siempre es el mismo (8082 aquí) y
lo que se negocia es el puerto **publicado** en la máquina, que se cambia con `SERVER_PORT` sin
tocar la imagen:

```bash
SERVER_PORT=9090 docker compose up
```

Cuando llegue el puerto real, buscar la palabra `PROVISIONAL` en el repositorio. Aparece en los
seis archivos que mencionan el puerto: `.env.example`, `application.yml`, `docker-compose.yml`,
`Dockerfile`, `README.md` y este documento.

```bash
grep -rn "PROVISIONAL" .
```

---

## 5. Credenciales y secretos

Las credenciales del `docker-compose.yml` son de **desarrollo local**, de un contenedor que solo
escucha en `localhost`, y están puestas ahí a propósito para que `docker compose up` funcione sin
configurar nada:

```
POSTGRES_DB=cameia_perfil
POSTGRES_USER=cameia_perfil
POSTGRES_PASSWORD=cameia_local
```

**No son secretos y no aplican a ningún otro ambiente.** Las reglas de la propuesta multirepo se
respetan íntegras:

1. El `.env.example` se versiona y **todos sus valores sensibles están vacíos**.
2. El `.env` real está ignorado por `.gitignore` y `.dockerignore`.
3. Los secretos de CI van a GitHub Actions Secrets; los de runtime, al mecanismo aprobado de la
   plataforma, que **sigue pendiente de decisión operativa**.
4. Ninguna imagen lleva secretos horneados: todo entra por variable de entorno.

Para cambiarlas sin tocar Git, crea un `.env` en la raíz:

```dotenv
POSTGRES_PASSWORD=lo-que-quieras
SERVER_PORT=9090
```

---

## 6. Decisiones que se tomaron, y por qué

| Decisión | Por qué | Alternativa descartada |
|---|---|---|
| Multi-etapa en el `Dockerfile` | La imagen final no lleva Maven, ni el JDK completo, ni el código fuente | Una sola etapa: imagen mucho más grande y con el fuente dentro |
| `eclipse-temurin:21-jre-alpine` en runtime | Java 21 es la línea base del equipo; `jre` y `alpine` reducen superficie | `-jdk`: no hace falta compilar en runtime |
| Usuario `cameia` sin privilegios | Un contenedor no debería correr como `root` | `root`: es el defecto y es peor |
| `MaxRAMPercentage=75` | Sin esto la JVM lee la memoria de la máquina anfitriona, no la del contenedor, y Cloud Run la mata por exceso | Dejarlo al defecto |
| Las pruebas **no** corren en el `Dockerfile` | Necesitan PostgreSQL, que en tiempo de build no existe | Levantar una base dentro del build: frágil y lento |
| `healthcheck` en `db` y en `app` | Sin él, `app` arranca antes que PostgreSQL y falla de forma intermitente | `depends_on` a secas: no espera a que esté listo |
| `readiness` en el healthcheck de `app` | Es el que incluye la base de datos; `liveness` solo dice que el proceso vive | `/actuator/health` a secas |
| Volumen `m2cache` | La segunda ejecución de `verify` no vuelve a descargar dependencias | Descargar cada vez |
| Red propia `cameia-perfil-net` | Este repositorio se levanta solo; no asume una red compartida que todavía no existe | Red `default` o una red externa compartida |

## 7. Lo que NO está aquí, a propósito

- **`Dockerfile` de producción, registry y despliegue a Cloud Run.** Es de DevOps. El documento
  multirepo lo dice: *"DevOps propone el mecanismo, revisa que no se versionen secretos y
  automatiza las comprobaciones."*
- **CI.** El `README.md` del repositorio pide *"activar CI únicamente con comandos comprobados
  por el responsable"*. Los comandos ya están comprobados y en el README; automatizarlos es otra
  tarea.
- **Un `docker-compose` que levante los siete repositorios juntos.** Requiere que existan y que
  la matriz de puertos esté aprobada.
- **RabbitMQ.** No entra en Sprint 1 para Perfil, y los nombres de las colas se contradicen entre
  hojas del C2. No se levanta infraestructura de algo que todavía no tiene contrato.

## 8. Preguntas abiertas para el equipo

1. ¿Se confirma la matriz de puertos de la sección 4? (`DEV-IN-05`)
2. ¿Qué pasa con `cameia-empleo`, que no está en el inventario de repositorios?
3. ¿Docker es responsabilidad de cada repositorio o la centraliza DevOps?
4. ¿Hay convención de nombres de imagen y registry? Aquí se usó `cameia-perfil:local`.
5. ¿Se adopta este archivo como base de un `reglas-docker` para los siete repositorios?
