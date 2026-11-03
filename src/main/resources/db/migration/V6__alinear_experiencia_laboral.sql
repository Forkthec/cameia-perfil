-- Alinea experiencia_laboral con las reglas de HU-2.4: largos de 100/100/500, inicio opcional,
-- restricciones con nombre e índice de la clave foránea.
ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_pkey TO pk_experiencia_laboral;
ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_perfil_id_fkey TO fk_experiencia_laboral_perfil;
ALTER TABLE experiencia_laboral ALTER COLUMN empresa TYPE VARCHAR(100);
ALTER TABLE experiencia_laboral ALTER COLUMN cargo TYPE VARCHAR(100);
ALTER TABLE experiencia_laboral ALTER COLUMN descripcion TYPE VARCHAR(500);
ALTER TABLE experiencia_laboral ALTER COLUMN fecha_inicio DROP NOT NULL;
ALTER TABLE experiencia_laboral
    ADD CONSTRAINT ck_experiencia_laboral_empresa_presente CHECK (length(btrim(empresa)) > 0),
    ADD CONSTRAINT ck_experiencia_laboral_cargo_presente CHECK (length(btrim(cargo)) > 0),
    ADD CONSTRAINT ck_experiencia_laboral_estado_empleo CHECK (estado_empleo IN ('CURRENT', 'ENDED', 'UNKNOWN_END')),
    ADD CONSTRAINT ck_experiencia_laboral_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT ck_experiencia_laboral_fin_segun_estado CHECK ((estado_empleo = 'ENDED') = (fecha_fin IS NOT NULL)),
    ADD CONSTRAINT ck_experiencia_laboral_fechas CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio),
    ADD CONSTRAINT ck_experiencia_laboral_primer_dia
        CHECK ((fecha_inicio IS NULL OR extract(day FROM fecha_inicio) = 1)
           AND (fecha_fin IS NULL OR extract(day FROM fecha_fin) = 1));
CREATE INDEX ix_experiencia_laboral_perfil_id ON experiencia_laboral (perfil_id);
