package co.edu.unicauca.cameia.perfil.application.command;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;

import java.time.DateTimeException;
import java.time.YearMonth;

/**
 * Convierte los textos de un comando en opciones y fechas del dominio.
 *
 * <p>Un valor que no se puede convertir se rechaza en su campo con su código, sin repetir el valor
 * recibido ni el texto de Java. Los mensajes son los de RT-01 para listas y fechas.</p>
 */
public final class CommandValues {

    static final String SELECT_OPTION = "Selecciona una opción.";
    static final String INVALID_DATE = "Ingresa una fecha válida con el formato mm/aaaa.";

    private CommandValues() { }

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
     * Acepta {@code AAAA-MM} y, si {@code yearOnly} lo permite, {@code AAAA} (se lee como enero).
     *
     * @param value    fecha recibida, o {@code null}
     * @param yearOnly si se acepta solo el año
     * @param code     código del error si la fecha no es válida
     * @param field    nombre del campo en el cuerpo de la petición
     * @return la fecha, o {@code null} si no llegó
     * @throws InvalidFieldsException si la fecha no tiene el formato o no existe, como {@code 2020-13}
     */
    public static YearMonth yearMonth(String value, boolean yearOnly, ErrorCode code, String field) {
        if (value == null) {
            return null;
        }
        var normalized = yearOnly && value.matches("\\d{4}") ? value + "-01" : value;
        try {
            return YearMonth.parse(normalized);
        } catch (DateTimeException e) {
            throw InvalidFieldsException.of(field, code, INVALID_DATE);
        }
    }
}
