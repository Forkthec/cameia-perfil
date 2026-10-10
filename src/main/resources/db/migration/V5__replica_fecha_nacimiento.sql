-- Réplica local de la fecha de nacimiento de cada Usuario, alimentada por el evento cuenta.creada del servicio de cuentas
-- y borrada por cuenta.eliminada. Solo se guardan la identidad y la fecha: lo mínimo que necesitan las reglas de fechas de la
-- experiencia laboral y la formación. Sin clave foránea: los datos son de otro servicio.
CREATE TABLE fecha_nacimiento_usuario (
    firebase_uid     VARCHAR(128) NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    CONSTRAINT pk_fecha_nacimiento_usuario PRIMARY KEY (firebase_uid),
    CONSTRAINT ck_fecha_nacimiento_usuario_firebase_uid CHECK (btrim(firebase_uid) <> '')
);
COMMENT ON TABLE fecha_nacimiento_usuario IS 'Réplica de la fecha de nacimiento por Usuario (evento cuenta.creada de Cuentas).';

-- Inbox: cada evento de cuenta ya procesado, por identificador de mensaje, para que una reentrega no tenga efecto. No guarda
-- datos personales y las filas se conservan sin plazo: unas dos por Usuario en toda la vida de la cuenta.
CREATE TABLE evento_procesado (
    message_id   UUID        NOT NULL,
    tipo         VARCHAR(32) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_evento_procesado PRIMARY KEY (message_id),
    CONSTRAINT ck_evento_procesado_tipo CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'))
);
COMMENT ON TABLE evento_procesado IS 'Inbox de eventos de cuenta ya procesados (identificador del mensaje, sin datos personales).';
