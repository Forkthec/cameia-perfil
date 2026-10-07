package co.edu.unicauca.cameia.perfil.domain.exception;

/** Códigos estables de error de Perfil: son el contrato con el cliente, no se renombran. */
public enum ErrorCode {

    /** Falló la validación de uno o más campos de la petición. */
    VALIDATION_FAILED,
    /** El cuerpo de la petición no se pudo leer como JSON o tiene un tipo incorrecto. */
    REQUEST_BODY_INVALID_FORMAT,
    /** Un valor rechazado por un objeto de valor, sin campo asociado. */
    REQUEST_INVALID_VALUE,
    /** Falta la identidad del usuario, o no es válida. */
    IDENTITY_REQUIRED,
    /** El perfil solicitado no existe. */
    PROFILE_NOT_FOUND,
    /** El perfil existe pero pertenece a otro usuario. */
    PROFILE_NOT_ALLOWED,
    /** El usuario alcanzó el cupo de perfiles de su plan. */
    PROFILE_LIMIT_REACHED,
    /** El perfil ya está finalizado y no admite más cambios de estado. */
    PROFILE_ALREADY_COMPLETED,
    /** El perfil no cumple los requisitos mínimos para finalizar. */
    PROFILE_INCOMPLETE,
    /** El rol profesional del catálogo no existe. */
    PROFESSIONAL_ROLE_NOT_FOUND,
    /** El perfil ya tiene el máximo de roles objetivo. */
    TARGET_ROLE_LIMIT_REACHED,
    /** El rol objetivo ya está agregado al perfil. */
    TARGET_ROLE_ALREADY_EXISTS,
    /** No se puede eliminar el último rol objetivo del perfil. */
    TARGET_ROLE_NOT_ALLOWED,
    /** La habilidad ya está asociada al perfil. */
    SKILL_ALREADY_EXISTS,
    /** El idioma pedido al catálogo de roles profesionales no está disponible. */
    PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE,
    /** La ruta solicitada no existe. */
    ROUTE_NOT_FOUND,
    /** El método HTTP no está permitido en la ruta. */
    METHOD_NOT_ALLOWED,
    /** El cuerpo llega en un formato distinto de JSON. */
    CONTENT_TYPE_NOT_ALLOWED,
    /** El cliente pide la respuesta en un formato distinto de JSON. */
    ACCEPT_TYPE_NOT_ALLOWED,
    /** El identificador del perfil en la ruta no es un UUID. */
    PROFILE_ID_INVALID_FORMAT,
    /** El identificador de la experiencia laboral en la ruta no es un UUID. */
    WORK_EXPERIENCE_ID_INVALID_FORMAT,
    /** El identificador de la formación académica en la ruta no es un UUID. */
    EDUCATION_ID_INVALID_FORMAT,
    /** El identificador de la habilidad en la ruta no es un UUID. */
    SKILL_ID_INVALID_FORMAT,
    /** El identificador del rol objetivo en la ruta no es un UUID. */
    TARGET_ROLE_ID_INVALID_FORMAT,
    /** Error interno no previsto. */
    INTERNAL_ERROR,
    /** Falta la empresa de la experiencia laboral. */
    COMPANY_REQUIRED,
    /** Falta el cargo de la experiencia laboral. */
    POSITION_REQUIRED,
    /** Falta la fecha de inicio. */
    START_DATE_REQUIRED,
    /** Falta el estado laboral. */
    EMPLOYMENT_STATUS_REQUIRED,
    /** Falta la procedencia del dato. */
    PROVENANCE_REQUIRED,
    /** Falta el nombre de la habilidad. */
    SKILL_NAME_REQUIRED,
    /** Falta el nivel de la habilidad. */
    SKILL_LEVEL_REQUIRED,
    /** Falta el identificador del rol profesional. */
    PROFESSIONAL_ROLE_ID_REQUIRED
}
