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
    /** El rol objetivo no existe en el perfil. */
    TARGET_ROLE_NOT_FOUND,
    /** La experiencia laboral no existe en el perfil. */
    WORK_EXPERIENCE_NOT_FOUND,
    /** La formación académica no existe en el perfil. */
    EDUCATION_NOT_FOUND,
    /** La habilidad no existe en el perfil. */
    SKILL_NOT_FOUND,
    /** Falta el nombre del perfil o está en blanco. */
    PROFILE_NAME_REQUIRED,
    /** El nombre del perfil supera su largo máximo. */
    PROFILE_NAME_TOO_LONG,
    /** Un perfil activo no puede quedarse sin resumen profesional. */
    SUMMARY_NOT_ALLOWED,
    /** El resumen profesional supera su largo máximo. */
    SUMMARY_TOO_LONG,
    /** La expectativa salarial está fuera del rango permitido. */
    SALARY_EXPECTATION_OUT_OF_RANGE,
    /** La modalidad de trabajo preferida no es una de las opciones. */
    PREFERRED_MODALITY_INVALID_VALUE,
    /** La procedencia del dato no es una de las opciones. */
    PROVENANCE_INVALID_VALUE,
    /** El estado laboral no es una de las opciones. */
    EMPLOYMENT_STATUS_INVALID_VALUE,
    /** El nivel de la formación académica no es una de las opciones. */
    EDUCATION_LEVEL_INVALID_VALUE,
    /** El nivel de la habilidad no es una de las opciones. */
    SKILL_LEVEL_INVALID_VALUE,
    /** La fecha de inicio no tiene el formato AAAA-MM o no existe. */
    START_DATE_INVALID_FORMAT,
    /** La fecha de fin no tiene el formato AAAA-MM o no existe. */
    END_DATE_INVALID_FORMAT,
    /** La experiencia terminó y no tiene fecha de fin. */
    END_DATE_REQUIRED,
    /** Llega fecha de fin en una experiencia en curso o sin fin conocido, o en una formación en curso. */
    END_DATE_NOT_ALLOWED,
    /** La fecha de fin es anterior a la de inicio. */
    END_DATE_BEFORE_START_DATE,
    /** La empresa supera su largo máximo. */
    COMPANY_TOO_LONG,
    /** El cargo supera su largo máximo. */
    POSITION_TOO_LONG,
    /** La institución supera su largo máximo. */
    INSTITUTION_TOO_LONG,
    /** El título obtenido supera su largo máximo. */
    DEGREE_TOO_LONG,
    /** El nombre de la habilidad supera su largo máximo. */
    SKILL_NAME_TOO_LONG,
    /** La descripción de la experiencia supera su largo máximo. */
    DESCRIPTION_TOO_LONG,
    /** El área de estudio de la formación supera su largo máximo. */
    FIELD_OF_STUDY_TOO_LONG,
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
    PROFESSIONAL_ROLE_ID_REQUIRED,
    /** Falta la institución de la formación académica. */
    INSTITUTION_REQUIRED,
    /** Falta el título de la formación académica. */
    DEGREE_REQUIRED,
    /** Falta el nivel de la formación académica. */
    EDUCATION_LEVEL_REQUIRED,
    /** Falta el monto de la expectativa salarial. */
    SALARY_EXPECTATION_REQUIRED
}
