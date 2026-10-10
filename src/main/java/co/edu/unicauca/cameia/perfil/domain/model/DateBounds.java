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

    /** Edad mínima, en años, para tener una experiencia laboral. */
    public static final int MINIMUM_WORKING_AGE = 15;

    /** @throws NullPointerException si {@code currentMonth} es {@code null} */
    public DateBounds {
        Objects.requireNonNull(currentMonth, "currentMonth");
    }

    /**
     * Dice si un mes queda antes del mes en que el dueño cumplió {@value #MINIMUM_WORKING_AGE} años.
     *
     * <p>Se compara por meses y no por días: quien nace el 29 de febrero cumple en febrero, exista o no el día 29,
     * y el mes en que se cumple la edad ya es válido.</p>
     *
     * @param month mes que se comprueba
     * @return {@code true} si hay mes de nacimiento y {@code month} es anterior a ese mes más 15 años
     */
    public boolean isBeforeMinimumAge(YearMonth month) {
        return birthMonth != null && month.isBefore(birthMonth.plusYears(MINIMUM_WORKING_AGE));
    }

    /**
     * Dice si un mes queda antes del mes de nacimiento del dueño.
     *
     * @param month mes que se comprueba
     * @return {@code true} si hay mes de nacimiento y {@code month} es anterior a él
     */
    public boolean isBeforeBirth(YearMonth month) {
        return birthMonth != null && month.isBefore(birthMonth);
    }
}
