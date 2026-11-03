-- Estado del catálogo: un rol deja de ofrecerse sin borrarse, porque los Roles Objetivo existentes lo referencian.
ALTER TABLE rol_profesional
    ADD COLUMN estado VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_rol_profesional_estado CHECK (estado IN ('ACTIVE', 'INACTIVE'));
CREATE INDEX ix_rol_profesional_estado ON rol_profesional (estado);

-- Rol Objetivo: el esquema repite las reglas del dominio (rol del catálogo obligatorio, sin repetir en el perfil).
ALTER TABLE rol_objetivo
    ALTER COLUMN rol_profesional_id SET NOT NULL,
    ADD CONSTRAINT uq_rol_objetivo_perfil_rol_profesional UNIQUE (perfil_id, rol_profesional_id),
    ADD CONSTRAINT ck_rol_objetivo_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT ck_rol_objetivo_estado_revision CHECK (estado_revision IN ('PENDING_REVIEW', 'REVIEWED'));
CREATE INDEX ix_rol_objetivo_rol_profesional_id ON rol_objetivo (rol_profesional_id);

-- Un rol del catálogo no se borra mientras un Rol Objetivo lo use: se desactiva.
ALTER TABLE rol_objetivo DROP CONSTRAINT fk_rol_objetivo_rol_profesional;
ALTER TABLE rol_objetivo
    ADD CONSTRAINT fk_rol_objetivo_rol_profesional
        FOREIGN KEY (rol_profesional_id) REFERENCES rol_profesional (id) ON DELETE RESTRICT;
