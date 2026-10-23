-- La tabla de salida admite también la instantánea del catálogo de roles profesionales. No toca datos: solo amplía los tipos permitidos.
ALTER TABLE evento_saliente
    DROP CONSTRAINT ck_evento_saliente_tipo,
    ADD CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('perfil.profesional-actualizado', 'perfil.catalogo-roles-actualizado'));

COMMENT ON COLUMN evento_saliente.tipo IS 'Tipo del evento y clave de enrutamiento: perfil.profesional-actualizado (agregado: el perfil) o perfil.catalogo-roles-actualizado (agregado: UUID nulo, el catalogo no pertenece a un perfil)';
