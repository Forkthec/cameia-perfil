package co.edu.unicauca.cameia.perfil.domain.exception;

/** Códigos estables de error de Perfil: son el contrato con el cliente, no se renombran. */
public enum ErrorCode {

    /** Falló la validación de uno o más campos de la petición. */
    VALIDATION_FAILED,
    /** El cuerpo de la petición no se pudo leer como JSON o tiene un tipo incorrecto. */
    REQUEST_BODY_INVALID_FORMAT,
    /** Un valor rechazado por un objeto de valor, sin campo asociado. */
    REQUEST_INVALID_VALUE,
    /** Falta el encabezado de identidad del usuario. */
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
