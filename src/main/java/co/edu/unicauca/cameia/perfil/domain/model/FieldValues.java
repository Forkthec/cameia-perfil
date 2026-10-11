package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * Convierte los textos de una petición en opciones y fechas del dominio.
 *
 * <p>Un valor que no se puede convertir se rechaza en su campo con su código, sin repetir el valor
 * recibido ni el texto de Java. Los mensajes son los de RT-01 para listas y fechas.</p>
 */
public final class FieldValues {

    static final String SELECT_OPTION = "Selecciona una opción.";
    static final String INVALID_DATE = "Ingresa una fecha válida con el formato mm/aaaa.";

    /** Año de cuatro dígitos desde 0001 y mes de dos: lo que la base de datos guarda como fecha. */
    private static final Pattern YEAR_MONTH = Pattern.compile("(?!0000)\\d{4}-\\d{2}");

    private FieldValues() { }

    /**
     * @param type  enumerado del dominio
     * @param value código recibido; se compara exacto, sin cambiar mayúsculas
     * @param code  código del error si el valor no es una de las opciones
     * @param field nombre del campo en el cuerpo de la petición
     * @return la opción del enumerado
     * @throws InvalidFieldsException si el valor no es una de las opciones
     */
    public static <E extends Enum<E>> E option(Class<E> type, String value, ErrorCode code, String field) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        throw InvalidFieldsException.of(field, code, SELECT_OPTION);
    }

    /**
     * Acepta solo {@code AAAA-MM}: un año suelto como {@code 2024} no es una fecha válida.
     *
     * @param value fecha recibida, o {@code null}
     * @param code  código del error si la fecha no es válida
     * @param field nombre del campo en el cuerpo de la petición
     * @return la fecha, o {@code null} si no llegó
     * @throws InvalidFieldsException si la fecha no tiene el formato o no existe, como {@code 2020-13}
     */
    public static YearMonth yearMonth(String value, ErrorCode code, String field) {
        if (value == null) {
            return null;
        }
        if (!YEAR_MONTH.matcher(value).matches()) {
            throw InvalidFieldsException.of(field, code, INVALID_DATE);
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeException e) {
            throw InvalidFieldsException.of(field, code, INVALID_DATE);
        }
    }
}
