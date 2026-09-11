-- CM-21: eliminar columna seniority de experiencia_laboral y rol_objetivo
-- La columna seniority ya no forma parte del contrato del MVP.

ALTER TABLE experiencia_laboral DROP COLUMN IF EXISTS seniority;
ALTER TABLE rol_objetivo DROP COLUMN IF EXISTS seniority;
