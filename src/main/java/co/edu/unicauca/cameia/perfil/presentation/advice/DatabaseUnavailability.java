package co.edu.unicauca.cameia.perfil.presentation.advice;

import org.hibernate.TransactionException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

/**
 * Reconoce que un fallo se debe a que la base de datos no está disponible: no responde, la
 * conexión se cortó, el pool de conexiones se agotó o una consulta pasó su tiempo máximo.
 *
 * <p>Recorre la excepción, sus causas y sus excepciones suprimidas, porque el corte de una
 * conexión puede llegar como una excepción de transacción sin código de estado y con la causa
 * verdadera solo entre las suprimidas. No reconoce los fallos del dato o de la consulta
 * (violaciones de restricción, SQL inválido, espera de bloqueo agotada).</p>
 */
final class DatabaseUnavailability {

    /** Máximo de excepciones que se visitan: una cadena con ciclos no debe dejar el hilo en un bucle. */
    private static final int MAX_NODES = 64;

    /** Estados de apagado o arranque del servidor y de demasiadas conexiones. */
    private static final Set<String> OUTAGE_STATES = Set.of("57P01", "57P02", "57P03", "53300");

    private DatabaseUnavailability() { }

    /**
     * Indica si el fallo es una indisponibilidad de la base de datos.
     *
     * @param error excepción recibida por el manejador, o {@code null}
     * @return {@code true} si la excepción, alguna causa o alguna suprimida es una indisponibilidad de la base
     */
    static boolean matches(Throwable error) {
        return firstMatch(error, node -> isOutage(node) ? Boolean.TRUE : null) != null;
    }

    /**
     * Devuelve el primer SQLState del árbol de excepciones, que es un código de cinco caracteres sin datos.
     *
     * @param error excepción recibida por el manejador, o {@code null}
     * @return el primer SQLState del árbol, o {@code -} si ninguna excepción trae uno (igual que
     *         {@code RedactedException.sqlState})
     */
    static String sqlState(Throwable error) {
        var state = firstMatch(error, node -> node instanceof SQLException sql ? sql.getSQLState() : null);
        return state != null ? state : "-";
    }

    /**
     * Recorre en anchura la excepción, sus causas y sus suprimidas, sin repetir nodos y con tope de
     * {@value #MAX_NODES}, y devuelve el primer resultado no nulo de {@code probe}.
     */
    private static <T> T firstMatch(Throwable error, Function<Throwable, T> probe) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.<Throwable>newSetFromMap(new IdentityHashMap<>());
        if (error != null) {
            pending.add(error);
        }
        while (!pending.isEmpty() && visited.size() < MAX_NODES) {
            var node = pending.poll();
            if (!visited.add(node)) {
                continue;
            }
            var found = probe.apply(node);
            if (found != null) {
                return found;
            }
            if (node.getCause() != null) {
                pending.add(node.getCause());
            }
            Collections.addAll(pending, node.getSuppressed());
        }
        return null;
    }

    /** Una sola excepción, sin recorrer su árbol: ¿es un corte, un agotamiento del pool o un tiempo agotado? */
    private static boolean isOutage(Throwable error) {
        if (error instanceof DataAccessResourceFailureException
                || error instanceof TransientDataAccessResourceException
                || error instanceof QueryTimeoutException
                || error instanceof JDBCConnectionException
                || error instanceof SQLTransientConnectionException
                || error instanceof SQLNonTransientConnectionException
                || error instanceof SQLTimeoutException) {
            return true;
        }
        // Hibernate lanza TransactionException al abrir, confirmar o deshacer la transacción, y también por un uso
        // incorrecto de la transacción en el código. Solo es un corte cuando lo causó la conexión (un SQLException,
        // que en una conexión cerrada llega sin SQLState).
        if (error instanceof TransactionException && error.getCause() instanceof SQLException) {
            return true;
        }
        if (error instanceof SQLException sql && sql.getSQLState() != null) {
            return sql.getSQLState().startsWith("08") || OUTAGE_STATES.contains(sql.getSQLState());
        }
        return false;
    }
}
