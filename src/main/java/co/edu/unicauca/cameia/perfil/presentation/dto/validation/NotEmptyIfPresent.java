package co.edu.unicauca.cameia.perfil.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Si el texto se envía, no puede estar vacío; si no se envía ({@code null}), se deja pasar porque significa «no cambiar».
 *
 * <p>Se usa sobre el texto ya recortado, así que cubre también el que solo traía espacios. Sustituye a
 * {@code @Size(min = 1)}, que además publica en OpenAPI un {@code maxLength} de 2 147 483 647 y tapa el límite real del
 * campo; esta restricción no escribe nada en el esquema y deja el {@code @Schema} del campo como única fuente.</p>
 */
@Documented
@Constraint(validatedBy = NotEmptyIfPresentValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotEmptyIfPresent {

    /** @return texto de respaldo; el real sale del catálogo de errores */
    String message() default "El texto no puede estar vacío";

    /** @return grupos de validación; las peticiones no usan ninguno */
    Class<?>[] groups() default {};

    /** @return carga de la restricción; las peticiones no usan ninguna */
    Class<? extends Payload>[] payload() default {};
}
