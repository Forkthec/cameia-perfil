package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Formación académica del candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>Si {@code inProgress} es true, {@code endDate} debe ser null.
 * Si {@code inProgress} es false, {@code endDate} es opcional (puede desconocerse).
 */
public final class Education {

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
        this.institution = requireNonBlankMax(institution, "institution", ErrorCode.INSTITUTION_REQUIRED,
                "Ingresa la institución.", ErrorCode.INSTITUTION_TOO_LONG, "La institución");
        this.degree = requireNonBlankMax(degree, "degree", ErrorCode.DEGREE_REQUIRED,
                "Ingresa el título obtenido.", ErrorCode.DEGREE_TOO_LONG, "El título obtenido");
        this.fieldOfStudy = fieldOfStudy;
        this.level = Objects.requireNonNull(level);
        this.startDate = Objects.requireNonNull(startDate, "startDate es obligatoria");
        this.provenance = Objects.requireNonNull(provenance);
        if (inProgress && endDate != null) {
            throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        }
        this.inProgress = inProgress;
        this.endDate = endDate;
    }

    private static String requireNonBlankMax(String value, String field, ErrorCode requiredCode,
                                             String requiredMessage, ErrorCode tooLongCode, String label) {
        Objects.requireNonNull(value, field + " no puede ser nulo");
        if (value.isBlank()) {
            throw InvalidFieldsException.of(field, requiredCode, requiredMessage);
        }
        if (value.length() > MAX_TEXT_LENGTH) {
            throw InvalidFieldsException.of(field, tooLongCode,
                    label + " no puede superar los " + MAX_TEXT_LENGTH + " caracteres.");
        }
        return value;
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
