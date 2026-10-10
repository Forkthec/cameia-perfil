package co.edu.unicauca.cameia.perfil.domain.model;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Meses que limitan las fechas de un registro de experiencia o formación.
 *
 * @param currentMonth mes actual en UTC; ninguna fecha puede ser posterior
 * @param birthMonth   mes de nacimiento del dueño, o {@code null} si el registro no trae fechas
 */
public record DateBounds(YearMonth currentMonth, YearMonth birthMonth) {

    /** @throws NullPointerException si {@code currentMonth} es {@code null} */
    public DateBounds {
        Objects.requireNonNull(currentMonth, "currentMonth");
    }
}
