package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El texto no supera {@link #max()} puntos de código Unicode. Un emoji cuenta como uno, a diferencia de {@code @Size},
 * que cuenta unidades de UTF-16. Ignora el valor ausente: la ausencia la informa {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = CodePointSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointSize {

    /** @return largo máximo en puntos de código; es el mismo de la columna y de la regla del dominio */
    int max();

    /** @return texto de respaldo; el real sale del catálogo de errores */
    String message() default "El texto es demasiado largo";

    /** @return grupos de validación; las peticiones no usan ninguno */
    Class<?>[] groups() default {};

    /** @return carga de la restricción; las peticiones no usan ninguna */
    Class<? extends Payload>[] payload() default {};
}
