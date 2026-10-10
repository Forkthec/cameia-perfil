package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import co.edu.unicauca.cameia.perfil.domain.model.SingleLineText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Valida {@link CodePointSize} contando puntos de código con la misma regla que el dominio. */
public class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {

    private int max;

    @Override
    public void initialize(CodePointSize annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SingleLineText.length(value) <= max;
    }
}
