package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Valida {@link NotEmptyIfPresent}: {@code null} pasa y la cadena vacía no. */
public class NotEmptyIfPresentValidator implements ConstraintValidator<NotEmptyIfPresent, String> {

    /** @return {@code true} si el valor es {@code null} o no está vacío */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !value.isEmpty();
    }
}
