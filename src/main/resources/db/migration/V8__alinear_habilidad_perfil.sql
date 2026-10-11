-- Alinea habilidad_perfil con las reglas de HU-2.5: nombre de 60, clave de comparación para no repetir una
-- habilidad dentro de un perfil, restricciones con nombre e índice de la clave foránea.
ALTER TABLE habilidad_perfil RENAME CONSTRAINT habilidad_perfil_pkey TO pk_habilidad_perfil;
ALTER TABLE habilidad_perfil RENAME CONSTRAINT habilidad_perfil_perfil_id_fkey TO fk_habilidad_perfil_perfil;
ALTER TABLE habilidad_perfil ALTER COLUMN nombre_habilidad TYPE VARCHAR(60);
ALTER TABLE habilidad_perfil ADD COLUMN nombre_normalizado VARCHAR(60);
-- Aproximación de la regla de la aplicación para las filas existentes: solo tildes del español y ñ
-- (PostgreSQL no quita tildes sin la extensión unaccent). La aplicación reescribe la columna en cada guardado.
UPDATE habilidad_perfil
   SET nombre_normalizado = translate(lower(regexp_replace(btrim(nombre_habilidad), '\s+', ' ', 'g')),
                                      'áéíóúüñàèìòùâêîôûäëïö', 'aeiouunaeiouaeiouaeio');
ALTER TABLE habilidad_perfil ALTER COLUMN nombre_normalizado SET NOT NULL;
ALTER TABLE habilidad_perfil
    ADD CONSTRAINT ck_habilidad_perfil_nombre_presente CHECK (length(btrim(nombre_habilidad)) > 0),
    ADD CONSTRAINT ck_habilidad_perfil_nivel CHECK (nivel IN ('BASIC', 'INTERMEDIATE', 'ADVANCED')),
    ADD CONSTRAINT ck_habilidad_perfil_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT uq_habilidad_perfil_nombre UNIQUE (perfil_id, nombre_normalizado);
CREATE INDEX ix_habilidad_perfil_perfil_id ON habilidad_perfil (perfil_id);
