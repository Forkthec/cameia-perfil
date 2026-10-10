package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.Collector;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Experiencia laboral del candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>Las reglas viven en {@link #create(WorkExperienceDraft, DateBounds)}: textos obligatorios y de largo
 * acotado, fechas no posteriores al mes actual y la relación entre {@link EmploymentStatus} y {@code endDate}:
 * <ul>
 *   <li>CURRENT y UNKNOWN_END → endDate debe ser null.
 *   <li>ENDED → endDate requerida y no anterior a startDate (si hay inicio).
 * </ul>
 */
public final class WorkExperience {

    /** Largo máximo de la empresa, en puntos de código; es el de la columna. */
    public static final int MAX_COMPANY_LENGTH = 100;
    /** Largo máximo del cargo, en puntos de código; es el de la columna. */
    public static final int MAX_POSITION_LENGTH = 100;
    /** Largo máximo de la descripción, en puntos de código; es el de la columna. */
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Rechazo de una empresa con un carácter de control. */
    public static final FieldError COMPANY_CHARACTERS =
            new FieldError("company", ErrorCode.COMPANY_INVALID_CHARACTERS, "La empresa tiene caracteres no permitidos.");
    /** Rechazo de un cargo con un carácter de control. */
    public static final FieldError POSITION_CHARACTERS =
            new FieldError("position", ErrorCode.POSITION_INVALID_CHARACTERS, "El cargo tiene caracteres no permitidos.");
    /** Rechazo de una descripción con un carácter de control distinto de tabulador, salto de línea y retorno. */
    public static final FieldError DESCRIPTION_CHARACTERS =
            new FieldError("description", ErrorCode.DESCRIPTION_INVALID_CHARACTERS, "La descripción tiene caracteres no permitidos.");

    private static final String FUTURE_DATE = "La fecha no puede ser posterior al mes actual.";

    private final UUID id;
    private final String company;
    private final String position;
    private final String description;
    private final YearMonth startDate;
    private final YearMonth endDate;
    private final EmploymentStatus employmentStatus;
    private final DataProvenance provenance;

    private WorkExperience(UUID id, WorkExperienceDraft data, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.company = Objects.requireNonNull(data.company());
        this.position = Objects.requireNonNull(data.position());
        this.description = data.description();
        this.startDate = data.startDate();
        this.endDate = data.endDate();
        this.employmentStatus = Objects.requireNonNull(data.employmentStatus());
        this.provenance = Objects.requireNonNull(provenance);
    }

    /**
     * Crea una experiencia nueva aplicando todas las reglas.
     *
     * <p>Corren todas las reglas y se informan a la vez todos los campos rechazados; en cada campo gana la
     * primera regla que falla, en el orden en que los criterios de aceptación las listan. Lo que se agrega por
     * las rutas manuales es siempre MANUAL; lo que escribe la IA se crea con su propia fábrica.</p>
     *
     * @param draft  datos recibidos, con los textos aún sin normalizar
     * @param bounds meses que limitan las fechas
     * @return la experiencia con un identificador nuevo y procedencia MANUAL
     * @throws InvalidFieldsException si algún campo incumple una regla
     */
    public static WorkExperience create(WorkExperienceDraft draft, DateBounds bounds) {
        String company = SingleLineText.normalize(draft.company());
        String position = SingleLineText.normalize(draft.position());
        String description = SingleLineText.normalizeOptional(draft.description());
        var collector = new Collector();
        checkText(collector, company, "company", ErrorCode.COMPANY_REQUIRED, "Ingresa la empresa.",
                MAX_COMPANY_LENGTH, ErrorCode.COMPANY_TOO_LONG, "La empresa", COMPANY_CHARACTERS);
        checkText(collector, position, "position", ErrorCode.POSITION_REQUIRED, "Ingresa el cargo.",
                MAX_POSITION_LENGTH, ErrorCode.POSITION_TOO_LONG, "El cargo", POSITION_CHARACTERS);
        checkDescription(collector, description);
        checkStatus(collector, draft.employmentStatus());
        checkStartDate(collector, draft.startDate(), bounds);
        checkEndDate(collector, draft, bounds);
        collector.throwIfAny();
        var data = new WorkExperienceDraft(company, position, description, draft.startDate(), draft.endDate(),
                draft.employmentStatus());
        return new WorkExperience(UUID.randomUUID(), data, DataProvenance.MANUAL);
    }

    /**
     * Reconstruye una experiencia guardada sin aplicar reglas: lo guardado ya fue válido con las reglas de su momento.
     *
     * @param id         identificador guardado
     * @param stored     datos guardados
     * @param provenance procedencia guardada
     * @return la experiencia tal como estaba
     */
    public static WorkExperience rebuild(UUID id, WorkExperienceDraft stored, DataProvenance provenance) {
        return new WorkExperience(id, stored, provenance);
    }

    /** Gana la primera regla que falla: obligatorio, caracteres de control y largo, en ese orden. */
    private static void checkText(Collector collector, String text, String field, ErrorCode required,
            String requiredMessage, int max, ErrorCode tooLong, String subject, FieldError characters) {
        if (text == null || text.isEmpty()) {
            collector.add(field, required, requiredMessage);
        } else if (SingleLineText.hasControlCharacter(text, false)) {
            collector.add(characters);
        } else if (SingleLineText.length(text) > max) {
            collector.add(field, tooLong, subject + " no puede superar los " + max + " caracteres.");
        }
    }

    private static void checkDescription(Collector collector, String description) {
        if (description == null) {
            return;
        }
        if (SingleLineText.hasControlCharacter(description, true)) {
            collector.add(DESCRIPTION_CHARACTERS);
        } else if (SingleLineText.length(description) > MAX_DESCRIPTION_LENGTH) {
            collector.add("description", ErrorCode.DESCRIPTION_TOO_LONG,
                    "La descripción no puede superar los " + MAX_DESCRIPTION_LENGTH + " caracteres.");
        }
    }

    private static void checkStatus(Collector collector, EmploymentStatus status) {
        if (status == null) {
            collector.add("employmentStatus", ErrorCode.EMPLOYMENT_STATUS_REQUIRED, FieldValues.SELECT_OPTION);
        }
    }

    /** Gana la primera regla que falla: futura y anterior a la edad mínima, en ese orden. */
    private static void checkStartDate(Collector collector, YearMonth start, DateBounds bounds) {
        if (start == null) {
            return;
        }
        if (start.isAfter(bounds.currentMonth())) {
            collector.add("startDate", ErrorCode.START_DATE_IN_THE_FUTURE, FUTURE_DATE);
        } else if (bounds.isBeforeMinimumAge(start)) {
            collector.add("startDate", ErrorCode.START_DATE_BEFORE_MINIMUM_AGE,
                    "La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.");
        }
    }

    /** Gana la primera regla que falla: no permitida, requerida, futura, anterior al inicio y anterior a la edad mínima sin inicio, en ese orden. */
    private static void checkEndDate(Collector collector, WorkExperienceDraft draft, DateBounds bounds) {
        var status = draft.employmentStatus();
        var end = draft.endDate();
        if (status == null) {
            return;
        }
        if (status != EmploymentStatus.ENDED && end != null) {
            collector.add("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        } else if (status == EmploymentStatus.ENDED && end == null) {
            collector.add("endDate", ErrorCode.END_DATE_REQUIRED, "Ingresa la fecha de fin.");
        } else if (end != null && end.isAfter(bounds.currentMonth())) {
            collector.add("endDate", ErrorCode.END_DATE_IN_THE_FUTURE, FUTURE_DATE);
        } else if (end != null && draft.startDate() != null && end.isBefore(draft.startDate())) {
            collector.add("endDate", ErrorCode.END_DATE_BEFORE_START_DATE,
                    "La fecha de fin no puede ser anterior a la de inicio.");
        } else if (end != null && draft.startDate() == null && bounds.isBeforeMinimumAge(end)) {
            // Sin inicio, el fin es la única fecha que puede delatar una experiencia anterior a los 15 años.
            collector.add("endDate", ErrorCode.END_DATE_BEFORE_MINIMUM_AGE,
                    "La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.");
        }
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
