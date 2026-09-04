# Imagen del Microservicio de Perfil Profesional.
#
# Multi-etapa: la etapa 'build' trae Maven y el JDK 21, asi que nadie necesita
# instalarlos en su maquina. La imagen final solo lleva el JRE y el jar.
#
# A proposito NO se usa la directiva '# syntax=docker/dockerfile:1': obliga a
# descargar una imagen de frontend adicional desde Docker Hub en cada entorno
# nuevo, y el frontend que ya trae Docker soporta 'RUN --mount=type=cache'.
# Una dependencia de red menos entre clonar y compilar.
#
# Convenciones y decisiones abiertas: docs/DOCKER.md

# ---------------------------------------------------------------------------
# Etapa 1 — construccion
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# El pom se copia solo primero para que la capa de dependencias se reutilice
# mientras no cambie: editar codigo no vuelve a descargar medio Maven Central.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src

# Las pruebas NO corren aqui: PerfilApplicationTest levanta el contexto de Spring y
# necesita PostgreSQL, que en tiempo de build no existe. La suite se ejecuta con el
# servicio 'verify' de docker-compose.yml, que si tiene la base al lado:
#   docker compose run --rm verify
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# ---------------------------------------------------------------------------
# Etapa 2 — ejecucion
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sin privilegios: la imagen no corre como root.
RUN addgroup -S cameia && adduser -S cameia -G cameia

WORKDIR /app
COPY --from=build --chown=cameia:cameia /build/target/*.jar /app/app.jar

USER cameia

# 8082 es PROVISIONAL (DEV-IN-05). Ver docs/DOCKER.md.
EXPOSE 8082

# Deja que la JVM respete los limites de CPU y memoria del contenedor en vez de
# leer los de la maquina anfitriona.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
