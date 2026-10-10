package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.Collector;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Formación académica del candidato. Entidad interna de {@link ProfessionalProfile}.
 *
 * <p>Las reglas viven en {@link #create(EducationDraft, DateBounds)}: textos obligatorios y de largo acotado,
 * nivel y fecha de inicio obligatorios, fechas no posteriores al mes actual y la relación entre
 * {@code inProgress} y {@code endDate}: si está en curso no lleva fin; si no, el fin es opcional (puede
 * desconocerse) y, si llega, no es anterior al inicio.</p>
 */
public final class Education {

    /** Largo máximo de la institución, en puntos de código; es el de la columna. */
    public static final int MAX_INSTITUTION_LENGTH = 150;
    /** Largo máximo del título obtenido, en puntos de código; es el de la columna. */
    public static final int MAX_DEGREE_LENGTH = 150;

    /** Rechazo de una institución con un carácter de control. */
    public static final FieldError INSTITUTION_CHARACTERS =
            new FieldError("institution", ErrorCode.INSTITUTION_INVALID_CHARACTERS, "La institución tiene caracteres no permitidos.");
    /** Rechazo de un título obtenido con un carácter de control. */
    public static final FieldError DEGREE_CHARACTERS =
            new FieldError("degree", ErrorCode.DEGREE_INVALID_CHARACTERS, "El título obtenido tiene caracteres no permitidos.");

    private static final String FUTURE_DATE = "La fecha no puede ser posterior al mes actual.";

    private final UUID id;
    private final String institution;
    private final String degree;
    private final EducationLevel level;
    private final YearMonth startDate;
    private final YearMonth endDate;
    private final boolean inProgress;
    private final DataProvenance provenance;

    private Education(UUID id, EducationDraft data, DataProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.institution = Objects.requireNonNull(data.institution());
        this.degree = Objects.requireNonNull(data.degree());
        this.level = Objects.requireNonNull(data.level());
        this.startDate = Objects.requireNonNull(data.startDate());
        this.endDate = data.endDate();
        this.inProgress = data.inProgress();
        this.provenance = Objects.requireNonNull(provenance);
    }

    /**
     * Crea una formación nueva aplicando todas las reglas.
     *
     * <p>Corren todas las reglas y se informan a la vez todos los campos rechazados; en cada campo gana la
     * primera regla que falla, en el orden en que los criterios de aceptación las listan. Lo que se agrega por
     * las rutas manuales es siempre MANUAL; lo que escribe la IA se crea con su propia fábrica.</p>
     *
     * @param draft  datos recibidos, con los textos aún sin normalizar
     * @param bounds meses que limitan las fechas
     * @return la formación con un identificador nuevo y procedencia MANUAL
     * @throws InvalidFieldsException si algún campo incumple una regla
     */
    public static Education create(EducationDraft draft, DateBounds bounds) {
        String institution = SingleLineText.normalize(draft.institution());
        String degree = SingleLineText.normalize(draft.degree());
        var collector = new Collector();
        checkText(collector, institution, "institution", ErrorCode.INSTITUTION_REQUIRED, "Ingresa la institución.",
                MAX_INSTITUTION_LENGTH, ErrorCode.INSTITUTION_TOO_LONG, "La institución", INSTITUTION_CHARACTERS);
        checkText(collector, degree, "degree", ErrorCode.DEGREE_REQUIRED, "Ingresa el título obtenido.",
                MAX_DEGREE_LENGTH, ErrorCode.DEGREE_TOO_LONG, "El título obtenido", DEGREE_CHARACTERS);
        if (draft.level() == null) {
            collector.add("level", ErrorCode.EDUCATION_LEVEL_REQUIRED, "Elige un nivel educativo.");
        }
        checkStartDate(collector, draft.startDate(), bounds);
        checkEndDate(collector, draft, bounds);
        collector.throwIfAny();
        var data = new EducationDraft(institution, degree, draft.level(), draft.startDate(), draft.endDate(),
                draft.inProgress());
        return new Education(UUID.randomUUID(), data, DataProvenance.MANUAL);
    }

    /**
     * Reconstruye una formación guardada sin aplicar reglas: lo guardado ya fue válido con las reglas de su momento.
     *
     * @param id         identificador guardado
     * @param stored     datos guardados
     * @param provenance procedencia guardada
     * @return la formación tal como estaba
     */
    public static Education rebuild(UUID id, EducationDraft stored, DataProvenance provenance) {
        return new Education(id, stored, provenance);
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

    private static void checkStartDate(Collector collector, YearMonth start, DateBounds bounds) {
        if (start == null) {
            collector.add("startDate", ErrorCode.START_DATE_REQUIRED, "Ingresa la fecha de inicio.");
        } else if (start.isAfter(bounds.currentMonth())) {
            collector.add("startDate", ErrorCode.START_DATE_IN_THE_FUTURE, FUTURE_DATE);
        }
    }

    /** Gana la primera regla que falla: no permitida, futura y anterior al inicio, en ese orden. */
    private static void checkEndDate(Collector collector, EducationDraft draft, DateBounds bounds) {
        var end = draft.endDate();
        if (end == null) {
            return;
        }
        if (draft.inProgress()) {
            collector.add("endDate", ErrorCode.END_DATE_NOT_ALLOWED, "La fecha de fin debe quedar vacía.");
        } else if (end.isAfter(bounds.currentMonth())) {
            collector.add("endDate", ErrorCode.END_DATE_IN_THE_FUTURE, FUTURE_DATE);
        } else if (draft.startDate() != null && end.isBefore(draft.startDate())) {
            collector.add("endDate", ErrorCode.END_DATE_BEFORE_START_DATE,
                    "La fecha de fin no puede ser anterior a la de inicio.");
        }
    }

    public UUID getId() { return id; }
    public String getInstitution() { return institution; }
    public String getDegree() { return degree; }
    public EducationLevel getLevel() { return level; }
    public YearMonth getStartDate() { return startDate; }
    public YearMonth getEndDate() { return endDate; }
    public boolean isInProgress() { return inProgress; }
    public DataProvenance getProvenance() { return provenance; }
}
