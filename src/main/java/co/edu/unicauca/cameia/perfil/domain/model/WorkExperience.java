package co.edu.unicauca.cameia.perfil.domain.model;

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
    private final Seniority seniority;
    private final DataProvenance provenance;

    public WorkExperience(UUID id, String company, String position, String description,
                          YearMonth startDate, YearMonth endDate,
                          EmploymentStatus employmentStatus, Seniority seniority,
                          DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.company = requireNonBlankMax(company, "company", MAX_TEXT_LENGTH);
        this.position = requireNonBlankMax(position, "position", MAX_TEXT_LENGTH);
        this.description = description;
        this.startDate = Objects.requireNonNull(startDate, "startDate es obligatoria");
        this.employmentStatus = Objects.requireNonNull(employmentStatus);
        this.seniority = Objects.requireNonNull(seniority);
        this.provenance = Objects.requireNonNull(provenance);
        validateEndDate(employmentStatus, startDate, endDate);
        this.endDate = endDate;
    }

    private static void validateEndDate(EmploymentStatus status, YearMonth start, YearMonth end) {
        if (status == EmploymentStatus.ENDED) {
            Objects.requireNonNull(end, "endDate es obligatoria cuando el estado es ENDED");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("endDate no puede ser anterior a startDate");
            }
        } else if (end != null) {
            throw new IllegalArgumentException("endDate debe ser null para estado " + status);
        }
    }

    private static String requireNonBlankMax(String value, String field, int max) {
        Objects.requireNonNull(value, field + " no puede ser nulo");
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " debe tener entre 1 y " + max + " caracteres");
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
    public Seniority getSeniority() { return seniority; }
    public DataProvenance getProvenance() { return provenance; }
}
