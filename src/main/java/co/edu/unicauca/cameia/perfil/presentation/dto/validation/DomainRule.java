package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException.FieldError;
import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.FieldValues;
import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.function.Consumer;

import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.COMPANY_INVALID_CHARACTERS;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.DEGREE_INVALID_CHARACTERS;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.DESCRIPTION_INVALID_CHARACTERS;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.EDUCATION_LEVEL_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.EMPLOYMENT_STATUS_INVALID_VALUE;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.END_DATE_INVALID_FORMAT;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.INSTITUTION_INVALID_CHARACTERS;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.POSITION_INVALID_CHARACTERS;
import static co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode.START_DATE_INVALID_FORMAT;

/**
 * Ejecuta en el borde una regla de forma escrita una sola vez en el dominio, para que se informe
 * junto con los demás errores de campo. El código y el mensaje salen del rechazo del dominio.
 */
@Documented
@Constraint(validatedBy = DomainRuleValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainRule {

    /** @return regla que se aplica al campo */
    Rule value();

    /** @return texto de respaldo; el real es el mensaje del rechazo del dominio */
    String message() default "Valor no válido";

    /** @return grupos de validación; las peticiones no usan ninguno */
    Class<?>[] groups() default {};

    /** @return carga de la restricción; las peticiones no usan ninguna */
    Class<? extends Payload>[] payload() default {};

    /** Reglas de forma de las peticiones, con la comprobación del dominio que corre y los códigos que informa. */
    enum Rule {
        COMPANY(text -> rejectControl(text, false, WorkExperience.COMPANY_CHARACTERS), Set.of(COMPANY_INVALID_CHARACTERS)),
        POSITION(text -> rejectControl(text, false, WorkExperience.POSITION_CHARACTERS), Set.of(POSITION_INVALID_CHARACTERS)),
        DESCRIPTION(text -> rejectControl(text, true, WorkExperience.DESCRIPTION_CHARACTERS), Set.of(DESCRIPTION_INVALID_CHARACTERS)),
        INSTITUTION(text -> rejectControl(text, false, Education.INSTITUTION_CHARACTERS), Set.of(INSTITUTION_INVALID_CHARACTERS)),
        DEGREE(text -> rejectControl(text, false, Education.DEGREE_CHARACTERS), Set.of(DEGREE_INVALID_CHARACTERS)),
        EMPLOYMENT_STATUS(text -> FieldValues.option(EmploymentStatus.class, text, EMPLOYMENT_STATUS_INVALID_VALUE, "employmentStatus"),
                Set.of(EMPLOYMENT_STATUS_INVALID_VALUE)),
        EDUCATION_LEVEL(text -> FieldValues.option(EducationLevel.class, text, EDUCATION_LEVEL_INVALID_VALUE, "level"),
                Set.of(EDUCATION_LEVEL_INVALID_VALUE)),
        START_DATE(text -> FieldValues.yearMonth(text, false, START_DATE_INVALID_FORMAT, "startDate"), Set.of(START_DATE_INVALID_FORMAT)),
        END_DATE(text -> FieldValues.yearMonth(text, false, END_DATE_INVALID_FORMAT, "endDate"), Set.of(END_DATE_INVALID_FORMAT));

        private final Consumer<String> validation;
        private final Set<ErrorCode> reportedCodes;

        Rule(Consumer<String> validation, Set<ErrorCode> reportedCodes) {
            this.validation = validation;
            this.reportedCodes = reportedCodes;
        }

        /**
         * Ejecuta la comprobación del dominio.
         *
         * @param text valor recibido, presente y no en blanco
         * @throws InvalidFieldsException si el dominio lo rechaza
         */
        void requireValid(String text) {
            validation.accept(text);
        }

        /**
         * Indica si esta regla informa un código del dominio.
         *
         * @param code código del rechazo del dominio
         * @return {@code true} si esta regla lo informa
         */
        boolean canReport(ErrorCode code) {
            return reportedCodes.contains(code);
        }

        private static void rejectControl(String text, boolean allowLineBreaks, FieldError rejection) {
            if (SingleLineText.hasControlCharacter(text, allowLineBreaks)) {
                throw InvalidFieldsException.of(rejection);
            }
        }
    }
}
