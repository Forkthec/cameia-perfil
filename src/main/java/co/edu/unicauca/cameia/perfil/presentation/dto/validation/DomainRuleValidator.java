package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/** Ejecuta la regla del dominio que nombra {@link DomainRule}; cualquier otro rechazo es de otra restricción. */
public class DomainRuleValidator implements ConstraintValidator<DomainRule, String> {

    private DomainRule.Rule rule;

    @Override
    public void initialize(DomainRule annotation) {
        this.rule = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            rule.requireValid(value);
            return true;
        } catch (InvalidFieldsException rejection) {
            var error = rejection.getErrors().get(0);
            if (!rule.canReport(error.code())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .withDynamicPayload(error.code())
                    .buildConstraintViolationWithTemplate(literal(error.message()))
                    .addConstraintViolation();
            return false;
        }
        // Cualquier otra excepción no se captura: un invariante que la regla no espera debe salir como falla.
    }

    /** Escapa el texto para que la interpolación del mensaje no lea llaves ni expresiones. */
    private static String literal(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
