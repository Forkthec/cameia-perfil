-- V1: tablas del microservicio de Perfil Profesional.
-- Fuente: C4 "Clases JPA - Microservicio de Perfil Profesional".
--
-- DESVIACIONES DOCUMENTADAS respecto al C4:
--   1. perfil_profesional añade columna `estado` (ProfileStatus) — ausente en el C4,
--      requerida por el glosario §6.5 y todas las HU del Sprint 1.
--   2. experiencia_laboral usa `estado_empleo VARCHAR(20)` en lugar de `actual BOOLEAN`
--      del C4, porque el prototipo de Frontend exige tres estados: CURRENT, UNKNOWN_END, ENDED.
--   3. habilidad_perfil usa `nombre_habilidad VARCHAR(255)` en lugar de FK a la tabla
--      `habilidad` (catálogo), que no se implementa en Sprint 1.
--   4. Las columnas de fechas en experiencia_laboral y educacion son tipo DATE (primer
--      día del mes); el dominio usa YearMonth y el adaptador convierte.

CREATE TABLE perfil_profesional (
    id                   UUID         PRIMARY KEY,
    firebase_uid         VARCHAR(128) NOT NULL,
    nombre               VARCHAR(255),
    titular              VARCHAR(255),
    resumen              VARCHAR(2000),
    expectativa_salarial NUMERIC(15, 2),
    modalidad_preferida  VARCHAR(20),
    procedencia          VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    estado_revision      VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    estado               VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    creado_en            TIMESTAMPTZ  NOT NULL,
    actualizado_en       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_perfil_firebase_uid ON perfil_profesional (firebase_uid);

CREATE TABLE experiencia_laboral (
    id             UUID         PRIMARY KEY,
    perfil_id      UUID         NOT NULL REFERENCES perfil_profesional (id) ON DELETE CASCADE,
    empresa        VARCHAR(500) NOT NULL,
    cargo          VARCHAR(500) NOT NULL,
    descripcion    VARCHAR(2000),
    fecha_inicio   DATE         NOT NULL,
    fecha_fin      DATE,
    estado_empleo  VARCHAR(20)  NOT NULL,
    seniority      VARCHAR(20),
    procedencia    VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'
);

CREATE TABLE educacion (
    id           UUID         PRIMARY KEY,
    perfil_id    UUID         NOT NULL REFERENCES perfil_profesional (id) ON DELETE CASCADE,
    institucion  VARCHAR(500) NOT NULL,
    titulo       VARCHAR(500) NOT NULL,
    area_estudio VARCHAR(500),
    nivel        VARCHAR(20)  NOT NULL,
    fecha_inicio DATE         NOT NULL,
    fecha_fin    DATE,
    en_curso     BOOLEAN      NOT NULL DEFAULT FALSE,
    procedencia  VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'
);

CREATE TABLE rol_objetivo (
    id                   UUID         PRIMARY KEY,
    perfil_id            UUID         NOT NULL REFERENCES perfil_profesional (id) ON DELETE CASCADE,
    nombre_rol           VARCHAR(255) NOT NULL,
    seniority            VARCHAR(20)  NOT NULL,
    id_sugerencia_empleo UUID,
    procedencia          VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    estado_revision      VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    creado_en            TIMESTAMPTZ  NOT NULL
);

CREATE TABLE habilidad_perfil (
    id               UUID         PRIMARY KEY,
    perfil_id        UUID         NOT NULL REFERENCES perfil_profesional (id) ON DELETE CASCADE,
    nombre_habilidad VARCHAR(255) NOT NULL,
    nivel            VARCHAR(20)  NOT NULL,
    procedencia      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'
);
