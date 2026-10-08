package co.edu.unicauca.cameia.perfil.presentation.advice;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

/**
 * Catálogo de las respuestas de error de Perfil.
 *
 * <p>Es la única tabla que traduce un {@link ErrorCode} a HTTP: para cada código guarda su estado,
 * su título y, cuando el texto no lo pone la excepción de negocio, su mensaje. También guarda los
 * códigos de los campos rechazados por Bean Validation y los de los identificadores de la ruta.
 * Un código nuevo se agrega aquí; las pruebas del catálogo fallan si alguno queda sin fila.</p>
 */
final class ErrorCatalog {

    /**
     * Respuesta de un código.
     *
     * @param status estado HTTP
     * @param title  título corto del problema
     * @param detail mensaje para la persona cuando no lo pone la excepción de negocio
     */
    record Definition(HttpStatus status, String title, String detail) { }

    private static final String SELECT_OPTION = "Selecciona una opción.";
    private static final String INVALID_ID = "Identificador no válido";

    /** Mensaje del campo rechazado cuando su restricción no tiene código propio. */
    static final String DEFAULT_FIELD_MESSAGE = "Revisa este campo.";

    static final Map<ErrorCode, Definition> RESPONSES = Map.ofEntries(
            // Errores de la petición, con mensaje fijo.
            entry(VALIDATION_FAILED, UNPROCESSABLE_ENTITY, "Datos no válidos", "Revisa los campos marcados."),
            entry(REQUEST_BODY_INVALID_FORMAT, UNPROCESSABLE_ENTITY, "Datos no válidos",
                    "Revisa el formato de los datos enviados."),
            entry(REQUEST_INVALID_VALUE, UNPROCESSABLE_ENTITY, "Valor no válido", "Revisa los datos enviados."),
            entry(IDENTITY_REQUIRED, UNAUTHORIZED, "Identidad requerida", "Tu sesión expiró. Inicia sesión de nuevo."),
            entry(ROUTE_NOT_FOUND, NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada."),
            entry(METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                    "Método no permitido."),
            entry(MEDIA_TYPE_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                    "Tipo de contenido no admitido."),
            entry(MEDIA_TYPE_NOT_ACCEPTABLE, NOT_ACCEPTABLE, "Tipo de respuesta no admitido",
                    "Tipo de respuesta no admitido."),
            entry(PROFILE_ID_INVALID_FORMAT, UNPROCESSABLE_ENTITY, INVALID_ID,
                    "El identificador del perfil no es válido."),
            entry(WORK_EXPERIENCE_ID_INVALID_FORMAT, UNPROCESSABLE_ENTITY, INVALID_ID,
                    "El identificador de la experiencia no es válido."),
            entry(EDUCATION_ID_INVALID_FORMAT, UNPROCESSABLE_ENTITY, INVALID_ID,
                    "El identificador de la formación no es válido."),
            entry(SKILL_ID_INVALID_FORMAT, UNPROCESSABLE_ENTITY, INVALID_ID,
                    "El identificador de la habilidad no es válido."),
            entry(TARGET_ROLE_ID_INVALID_FORMAT, UNPROCESSABLE_ENTITY, INVALID_ID,
                    "El identificador del rol objetivo no es válido."),
            entry(INTERNAL_ERROR, INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error. Inténtalo de nuevo."),
            // Errores de negocio: el mensaje lo pone la excepción.
            entry(PROFILE_NOT_FOUND, NOT_FOUND, "Perfil no encontrado", null),
            entry(PROFILE_NOT_ALLOWED, FORBIDDEN, "Acceso denegado", null),
            entry(PROFILE_LIMIT_REACHED, CONFLICT, "Cupo del plan alcanzado", null),
            entry(PROFILE_ALREADY_COMPLETED, CONFLICT, "Perfil ya completado", null),
            entry(PROFILE_INCOMPLETE, UNPROCESSABLE_ENTITY, "Finalización incompleta", null),
            entry(PROFESSIONAL_ROLE_NOT_FOUND, NOT_FOUND, "Rol profesional no encontrado", null),
            entry(PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE, UNPROCESSABLE_ENTITY, "Idioma no disponible", null),
            entry(TARGET_ROLE_LIMIT_REACHED, UNPROCESSABLE_ENTITY, "Máximo de roles objetivo alcanzado", null),
            entry(TARGET_ROLE_ALREADY_EXISTS, CONFLICT, "Rol objetivo duplicado", null),
            entry(TARGET_ROLE_NOT_ALLOWED, UNPROCESSABLE_ENTITY, "No se puede eliminar el último rol objetivo", null),
            entry(SKILL_ALREADY_EXISTS, CONFLICT, "Habilidad duplicada", null),
            entry(TARGET_ROLE_NOT_FOUND, NOT_FOUND, "Rol objetivo no encontrado", null),
            entry(WORK_EXPERIENCE_NOT_FOUND, NOT_FOUND, "Experiencia no encontrada", null),
            entry(EDUCATION_NOT_FOUND, NOT_FOUND, "Formación no encontrada", null),
            entry(SKILL_NOT_FOUND, NOT_FOUND, "Habilidad no encontrada", null));

    /** Clave {@code ClaseDelDto.campo.Restricción} → código del campo rechazado. */
    static final Map<String, ErrorCode> FIELD_CODES = Map.ofEntries(
            Map.entry("AddWorkExperienceRequest.company.NotBlank", COMPANY_REQUIRED),
            Map.entry("AddWorkExperienceRequest.position.NotBlank", POSITION_REQUIRED),
            Map.entry("AddWorkExperienceRequest.startDate.NotBlank", START_DATE_REQUIRED),
            Map.entry("AddWorkExperienceRequest.employmentStatus.NotNull", EMPLOYMENT_STATUS_REQUIRED),
            Map.entry("AddWorkExperienceRequest.provenance.NotNull", PROVENANCE_REQUIRED),
            Map.entry("AddSkillRequest.provenance.NotBlank", PROVENANCE_REQUIRED),
            Map.entry("AddTargetRoleRequest.provenance.NotNull", PROVENANCE_REQUIRED),
            Map.entry("AddSkillRequest.skillName.NotBlank", SKILL_NAME_REQUIRED),
            Map.entry("AddSkillRequest.level.NotBlank", SKILL_LEVEL_REQUIRED),
            Map.entry("UpdateTargetRoleRequest.professionalRoleId.NotNull", PROFESSIONAL_ROLE_ID_REQUIRED),
            Map.entry("AddTargetRoleRequest.professionalRoleId.NotNull", PROFESSIONAL_ROLE_ID_REQUIRED),
            Map.entry("AddEducationRequest.institution.NotBlank", INSTITUTION_REQUIRED),
            Map.entry("AddEducationRequest.degree.NotBlank", DEGREE_REQUIRED),
            Map.entry("AddEducationRequest.level.NotBlank", EDUCATION_LEVEL_REQUIRED),
            Map.entry("AddEducationRequest.startDate.NotBlank", START_DATE_REQUIRED),
            Map.entry("AddEducationRequest.provenance.NotBlank", PROVENANCE_REQUIRED),
            Map.entry("UpdateSalaryExpectationRequest.amount.NotNull", SALARY_EXPECTATION_REQUIRED));

    /** Código del campo → mensaje para la persona. */
    static final Map<ErrorCode, String> FIELD_MESSAGES = Map.ofEntries(
            Map.entry(COMPANY_REQUIRED, "Ingresa la empresa."),
            Map.entry(POSITION_REQUIRED, "Ingresa el cargo."),
            Map.entry(START_DATE_REQUIRED, "Ingresa la fecha de inicio."),
            Map.entry(EMPLOYMENT_STATUS_REQUIRED, SELECT_OPTION),
            Map.entry(PROVENANCE_REQUIRED, SELECT_OPTION),
            Map.entry(SKILL_NAME_REQUIRED, "Ingresa una habilidad."),
            Map.entry(SKILL_LEVEL_REQUIRED, "Elige un nivel."),
            Map.entry(PROFESSIONAL_ROLE_ID_REQUIRED, SELECT_OPTION),
            Map.entry(INSTITUTION_REQUIRED, "Ingresa la institución."),
            Map.entry(DEGREE_REQUIRED, "Ingresa el título obtenido."),
            Map.entry(EDUCATION_LEVEL_REQUIRED, "Elige un nivel educativo."),
            Map.entry(SALARY_EXPECTATION_REQUIRED, "Ingresa la expectativa salarial."));

    /** Nombre de la variable de ruta → código cuando su valor no es un UUID. */
    static final Map<String, ErrorCode> PATH_ID_CODES = Map.of(
            "id", PROFILE_ID_INVALID_FORMAT,
            "expId", WORK_EXPERIENCE_ID_INVALID_FORMAT,
            "eduId", EDUCATION_ID_INVALID_FORMAT,
            "skillId", SKILL_ID_INVALID_FORMAT,
            "roleId", TARGET_ROLE_ID_INVALID_FORMAT);

    private ErrorCatalog() { }

    /**
     * Devuelve la respuesta de un código.
     *
     * @param code código del error
     * @return su estado, título y mensaje fijo
     * @throws IllegalStateException si el código no tiene fila: es un error de programación
     */
    static Definition of(ErrorCode code) {
        var definition = RESPONSES.get(code);
        if (definition == null) {
            throw new IllegalStateException("Código de error sin respuesta en el catálogo: " + code);
        }
        return definition;
    }

    /**
     * @param key clave {@code ClaseDelDto.campo.Restricción}
     * @return el código del campo, o {@code VALIDATION_FAILED} si la restricción no tiene uno propio
     */
    static ErrorCode fieldCode(String key) {
        return FIELD_CODES.getOrDefault(key, VALIDATION_FAILED);
    }

    /**
     * @param code código del campo
     * @return el mensaje para la persona, o uno genérico si el código no tiene mensaje propio
     */
    static String fieldMessage(ErrorCode code) {
        return FIELD_MESSAGES.getOrDefault(code, DEFAULT_FIELD_MESSAGE);
    }

    /**
     * @param pathVariable nombre de la variable de ruta
     * @return el código del identificador mal escrito, o {@code REQUEST_INVALID_VALUE} si no es un identificador conocido
     */
    static ErrorCode pathIdCode(String pathVariable) {
        return PATH_ID_CODES.getOrDefault(pathVariable, REQUEST_INVALID_VALUE);
    }

    private static Map.Entry<ErrorCode, Definition> entry(
            ErrorCode code, HttpStatus status, String title, String detail) {
        return Map.entry(code, new Definition(status, title, detail));
    }
}
