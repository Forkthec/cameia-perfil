package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Formación académica del candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>Si {@code inProgress} es true, {@code endDate} debe ser null.
 * Si {@code inProgress} es false, {@code endDate} es opcional (puede desconocerse) y, si llega, no es
 * anterior a {@code startDate}.
 */
public final class Education {

    /** Rechazo de una institución con un carácter de control. */
    public static final FieldError INSTITUTION_CHARACTERS =
            new FieldError("institution", ErrorCode.INSTITUTION_INVALID_CHARACTERS, "La institución tiene caracteres no permitidos.");
    /** Rechazo de un título obtenido con un carácter de control. */
    public static final FieldError DEGREE_CHARACTERS =
            new FieldError("degree", ErrorCode.DEGREE_INVALID_CHARACTERS, "El título obtenido tiene caracteres no permitidos.");

    private static final int MAX_TEXT_LENGTH = 500;

    private final UUID id;
    private final String institution;
    private final String degree;
    private final String fieldOfStudy;
    private final EducationLevel level;
    private final YearMonth startDate;
    private final YearMonth endDate;
    private final boolean inProgress;
    private final DataProvenance provenance;

    public Education(UUID id, String institution, String degree, String fieldOfStudy,
                     EducationLevel level, YearMonth startDate, YearMonth endDate,
                     boolean inProgress, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.institution = FieldRules.requiredText(institution, "institution", ErrorCode.INSTITUTION_REQUIRED,
                "Ingresa la institución.", ErrorCode.INSTITUTION_TOO_LONG, "La institución", MAX_TEXT_LENGTH);
        this.degree = FieldRules.requiredText(degree, "degree", ErrorCode.DEGREE_REQUIRED,
                "Ingresa el título obtenido.", ErrorCode.DEGREE_TOO_LONG, "El título obtenido", MAX_TEXT_LENGTH);
        this.fieldOfStudy = FieldRules.optionalText(fieldOfStudy, "fieldOfStudy",
                ErrorCode.FIELD_OF_STUDY_TOO_LONG, "El área de estudio", MAX_TEXT_LENGTH);
        this.level = Objects.requireNonNull(level);
        this.startDate = Objects.requireNonNull(startDate, "startDate es obligatoria");
        this.provenance = Objects.requireNonNull(provenance);
        if (inProgress && endDate != null) {
            throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_BEFORE_START_DATE,
                    "La fecha de fin no puede ser anterior a la de inicio.");
        }
        this.inProgress = inProgress;
        this.endDate = endDate;
    }

    public UUID getId() { return id; }
    public String getInstitution() { return institution; }
    public String getDegree() { return degree; }
    public String getFieldOfStudy() { return fieldOfStudy; }
    public EducationLevel getLevel() { return level; }
    public YearMonth getStartDate() { return startDate; }
    public YearMonth getEndDate() { return endDate; }
    public boolean isInProgress() { return inProgress; }
    public DataProvenance getProvenance() { return provenance; }
}
