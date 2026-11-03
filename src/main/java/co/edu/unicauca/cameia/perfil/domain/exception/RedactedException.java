package co.edu.unicauca.cameia.perfil.domain.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Copia de una excepción sin sus mensajes, para registrar un fallo sin datos personales.
 *
 * <p>El mensaje de una excepción puede traer lo que el Usuario escribió: PostgreSQL pone los valores de
 * las columnas en el de una restricción violada, y Hibernate el SQL con sus parámetros. La copia conserva
 * la clase original (como mensaje), la traza y la cadena de causas, que es lo que hace falta para
 * diagnosticar, y descarta los textos (CLAUDE.md §6 y estándar §8).</p>
 */
public final class RedactedException extends RuntimeException {

    private RedactedException(Throwable original, Throwable cause) {
        super(original.getClass().getName(), cause, false, true);
        setStackTrace(original.getStackTrace());
    }

    /**
     * @param original excepción tal como se lanzó
     * @return la copia sin mensajes, con la misma traza y la misma cadena de causas
     */
    public static RedactedException of(Throwable original) {
        return copy(original, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * @param original excepción tal como se lanzó
     * @return el {@code SQLState} de la primera {@link SQLException} de la cadena, o {@code -} si no hay;
     *         es un código de cinco caracteres, sin datos
     */
    public static String sqlState(Throwable original) {
        var seen = Collections.<Throwable>newSetFromMap(new IdentityHashMap<>());
        for (var t = original; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "-";
    }

    /**
     * @param original excepción tal como se lanzó
     * @return {@code clase.método} donde se lanzó, o {@code desconocido} si no tiene traza
     */
    public static String origin(Throwable original) {
        var frames = original.getStackTrace();
        return frames.length == 0 ? "desconocido" : frames[0].getClassName() + "." + frames[0].getMethodName();
    }

    private static RedactedException copy(Throwable original, Set<Throwable> seen) {
        seen.add(original);
        var cause = original.getCause();
        // Una cadena con ciclo se corta en la primera repetición.
        var redactedCause = cause == null || seen.contains(cause) ? null : copy(cause, seen);
        return new RedactedException(original, redactedCause);
    }
}
