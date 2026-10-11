-- Alinea educacion con las reglas de HU-2.4: largos de 150, restricciones con nombre e índice de la
-- clave foránea. Elimina area_estudio porque el campo salió del contrato y nada más lo lee.
ALTER TABLE educacion RENAME CONSTRAINT educacion_pkey TO pk_educacion;
ALTER TABLE educacion RENAME CONSTRAINT educacion_perfil_id_fkey TO fk_educacion_perfil;
ALTER TABLE educacion ALTER COLUMN institucion TYPE VARCHAR(150);
ALTER TABLE educacion ALTER COLUMN titulo TYPE VARCHAR(150);
ALTER TABLE educacion DROP COLUMN area_estudio;
ALTER TABLE educacion
    ADD CONSTRAINT ck_educacion_institucion_presente CHECK (length(btrim(institucion)) > 0),
    ADD CONSTRAINT ck_educacion_titulo_presente CHECK (length(btrim(titulo)) > 0),
    ADD CONSTRAINT ck_educacion_nivel CHECK (nivel IN ('TECHNICAL', 'UNDERGRADUATE', 'POSTGRADUATE')),
    ADD CONSTRAINT ck_educacion_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT ck_educacion_fin_en_curso CHECK (NOT en_curso OR fecha_fin IS NULL),
    ADD CONSTRAINT ck_educacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    ADD CONSTRAINT ck_educacion_primer_dia
        CHECK (extract(day FROM fecha_inicio) = 1 AND (fecha_fin IS NULL OR extract(day FROM fecha_fin) = 1));
CREATE INDEX ix_educacion_perfil_id ON educacion (perfil_id);
