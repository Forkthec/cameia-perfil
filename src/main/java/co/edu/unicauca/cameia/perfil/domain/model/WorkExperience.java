package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Experiencia laboral del candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>La relación entre {@link EmploymentStatus} y {@code endDate} es una invariante de dominio:
 * <ul>
 *   <li>CURRENT y UNKNOWN_END → endDate debe ser null.
 *   <li>ENDED → endDate requerida y debe ser mayor o igual a startDate.
 * </ul>
 */
public final class WorkExperience {

    private static final int MAX_TEXT_LENGTH = 500;

    private final UUID id;
    private final String company;
    private final String position;
    private final String description;
    private final YearMonth startDate;
    private final YearMonth endDate;
    private final EmploymentStatus employmentStatus;
    private final DataProvenance provenance;

    public WorkExperience(UUID id, String company, String position, String description,
                          YearMonth startDate, YearMonth endDate,
                          EmploymentStatus employmentStatus,
                          DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.company = requireNonBlankMax(company, "company",
                ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.", ErrorCode.COMPANY_TOO_LONG, "La empresa");
        this.position = requireNonBlankMax(position, "position",
                ErrorCode.POSITION_REQUIRED, "Ingresa el cargo.", ErrorCode.POSITION_TOO_LONG, "El cargo");
        this.description = description;
        this.startDate = Objects.requireNonNull(startDate, "startDate es obligatoria");
        this.employmentStatus = Objects.requireNonNull(employmentStatus);
        this.provenance = Objects.requireNonNull(provenance);
        validateEndDate(employmentStatus, startDate, endDate);
        this.endDate = endDate;
    }

    private static void validateEndDate(EmploymentStatus status, YearMonth start, YearMonth end) {
        if (status == EmploymentStatus.ENDED) {
            if (end == null) {
                throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_REQUIRED, "Ingresa la fecha de fin.");
            }
            if (end.isBefore(start)) {
                throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_BEFORE_START_DATE,
                        "La fecha de fin no puede ser anterior a la de inicio.");
            }
        } else if (end != null) {
            throw InvalidFieldsException.of("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        }
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
    public String getCompany() { return company; }
    public String getPosition() { return position; }
    public String getDescription() { return description; }
    public YearMonth getStartDate() { return startDate; }
    public YearMonth getEndDate() { return endDate; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public DataProvenance getProvenance() { return provenance; }
}
