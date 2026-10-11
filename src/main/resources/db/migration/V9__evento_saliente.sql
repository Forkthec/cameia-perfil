-- Tabla de salida de eventos de Perfil: el evento se guarda en la misma transacción que el cambio y se publica después.
CREATE TABLE evento_saliente (
    id                UUID          NOT NULL,
    tipo              VARCHAR(64)   NOT NULL,
    version           SMALLINT      NOT NULL,
    agregado_id       UUID          NOT NULL,
    carga             JSONB         NULL,
    id_correlacion    VARCHAR(64)   NOT NULL,
    fecha_creacion    TIMESTAMPTZ   NOT NULL,
    fecha_publicacion TIMESTAMPTZ   NULL,
    intentos          INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_evento_saliente PRIMARY KEY (id),
    CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('perfil.profesional-actualizado')),
    CONSTRAINT ck_evento_saliente_version CHECK (version >= 1),
    CONSTRAINT ck_evento_saliente_id_correlacion CHECK (id_correlacion ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_evento_saliente_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_evento_saliente_carga_pendiente CHECK ((fecha_publicacion IS NULL) = (carga IS NOT NULL)),
    CONSTRAINT ck_evento_saliente_fecha_publicacion CHECK (fecha_publicacion IS NULL OR fecha_publicacion >= fecha_creacion)
);
-- Sin clave foránea a perfil_profesional: el evento es inmutable y sobrevive al borrado del perfil.
CREATE INDEX ix_evento_saliente_pendiente ON evento_saliente (fecha_creacion) WHERE fecha_publicacion IS NULL;
CREATE INDEX ix_evento_saliente_agregado_id ON evento_saliente (agregado_id);
