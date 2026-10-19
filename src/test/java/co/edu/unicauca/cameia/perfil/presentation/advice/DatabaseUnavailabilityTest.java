package co.edu.unicauca.cameia.perfil.presentation.advice;

import org.hibernate.TransactionException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Reconocimiento de la indisponibilidad de la base de datos en excepciones construidas a mano, sin Spring. */
class DatabaseUnavailabilityTest {

    @Test
    @DisplayName("Un pool agotado se reconoce en su cadena completa; la envoltura de Spring sola no basta")
    void matches_shouldBeTrue_whenPoolIsExhausted() {
        var pool = new SQLTransientConnectionException("Connection is not available, request timed out");
        var hibernate = new JDBCConnectionException("x", pool);
        var error = new CannotCreateTransactionException("x", hibernate);

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
        assertThat(DatabaseUnavailability.matches(hibernate)).isTrue();
        assertThat(DatabaseUnavailability.matches(pool)).isTrue();
        assertThat(DatabaseUnavailability.matches(new CannotCreateTransactionException("x"))).isFalse();
    }

    @Test
    @DisplayName("Un apagado del servidor (57P01) se reconoce con la conexión cerrada entre las suprimidas")
    void matches_shouldBeTrue_whenSqlStateIsAdminShutdown() {
        var error = new DataAccessResourceFailureException("x", new SQLException("FATAL", "57P01"));
        error.addSuppressed(new SQLException("Connection is closed"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Un fallo al deshacer la transacción por una conexión cerrada se reconoce sin SQLState")
    void matches_shouldBeTrue_whenRollbackFailsOnClosedConnection() {
        var error = new JpaSystemException(new TransactionException("Unable to rollback", new SQLException("Connection is closed")));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Un error de conexión que solo está entre las excepciones suprimidas se reconoce")
    void matches_shouldBeTrue_whenOnlySuppressedIsConnectionError() {
        var error = new RuntimeException("x");
        error.addSuppressed(new SQLException("x", "08006"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @ParameterizedTest(name = "SQLState {0}")
    @ValueSource(strings = {"08001", "08006", "57P01", "57P02", "57P03", "53300"})
    @DisplayName("Los SQLState de conexión, apagado, arranque y exceso de conexiones se reconocen")
    void matches_shouldBeTrue_whenSqlStateIsConnectionClass(String sqlState) {
        assertThat(DatabaseUnavailability.matches(new SQLException("x", sqlState))).isTrue();
    }

    @Test
    @DisplayName("Una consulta que pasa su tiempo máximo se reconoce")
    void matches_shouldBeTrue_whenQueryTimesOut() {
        var error = new QueryTimeoutException("x", new SQLException("canceling", "57014"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    static Stream<Arguments> notAvailabilityProblems() {
        return Stream.of(
                Arguments.of("violación de restricción", new DataIntegrityViolationException("x", new SQLException("x", "23502"))),
                Arguments.of("SQL inválido", new BadSqlGrammarException("x", "select", new SQLException("x", "42601"))),
                Arguments.of("espera de bloqueo agotada", new CannotAcquireLockException("x", new SQLException("x", "55P03"))),
                Arguments.of("estado ilegal", new IllegalStateException("x")),
                Arguments.of("excepción vacía", new RuntimeException()),
                Arguments.of("dato fuera de rango", new SQLException("x", "22012")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAvailabilityProblems")
    @DisplayName("Los fallos del dato o de la consulta no son una indisponibilidad")
    void matches_shouldBeFalse_whenNotAvailabilityProblem(String ignored, Throwable error) {
        assertThat(DatabaseUnavailability.matches(error)).isFalse();
    }

    static Stream<Arguments> transactionFailuresWithoutSqlCause() {
        return Stream.of(
                Arguments.of("sin causa", new TransactionException("Unable to commit")),
                Arguments.of("causa que no es SQL", new TransactionException("Unable to commit", new IllegalStateException("x"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transactionFailuresWithoutSqlCause")
    @DisplayName("Un fallo de transacción sin causa SQL es un defecto de programación, no una indisponibilidad")
    void matches_shouldBeFalse_whenTransactionExceptionHasNoSqlCause(String ignored, Throwable error) {
        assertThat(DatabaseUnavailability.matches(error)).isFalse();
    }

    @Test
    @DisplayName("Un fallo de transacción sin causa SQL se reconoce si el árbol trae otra pieza de conexión")
    void matches_shouldBeTrue_whenTransactionExceptionHasConnectionPieceElsewhere() {
        var error = new TransactionException("Unable to commit", new IllegalStateException("x"));
        error.addSuppressed(new SQLException("x", "08006"));

        assertThat(DatabaseUnavailability.matches(error)).isTrue();
    }

    @Test
    @DisplayName("Una excepción nula no es una indisponibilidad")
    void matches_shouldBeFalse_whenNull() {
        assertThat(DatabaseUnavailability.matches(null)).isFalse();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo termina y no es una indisponibilidad")
    void matches_shouldTerminate_whenCauseCycle() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);

        assertThat(DatabaseUnavailability.matches(a)).isFalse();
    }

    @Test
    @DisplayName("Una cadena de 100 causas se corta en 64 nodos: el corte al fondo no se alcanza")
    void matches_shouldStopAtLimit_whenChainIsTooDeep() {
        assertThat(DatabaseUnavailability.matches(chain(100, new SQLException("x", "08001")))).isFalse();
    }

    @Test
    @DisplayName("El nodo 65 de la cadena ya no se visita")
    void matches_shouldNotFind_whenOutageIsAtNode65() {
        assertThat(DatabaseUnavailability.matches(chain(64, new SQLException("x", "08001")))).isFalse();
    }

    @Test
    @DisplayName("El nodo 64 de la cadena todavía se visita")
    void matches_shouldFind_whenOutageIsAtNode64() {
        assertThat(DatabaseUnavailability.matches(chain(63, new SQLException("x", "08001")))).isTrue();
    }

    @Test
    @DisplayName("El SQLState que se registra es el primero del árbol de excepciones")
    void sqlState_shouldReturnFirstState_whenPresent() {
        assertThat(DatabaseUnavailability.sqlState(new SQLException("x", "57P01"))).isEqualTo("57P01");
    }

    @Test
    @DisplayName("Sin SQLState en el árbol se registra un guion, como hace RedactedException")
    void sqlState_shouldReturnDash_whenAbsent() {
        assertThat(DatabaseUnavailability.sqlState(new RuntimeException())).isEqualTo("-");
        assertThat(DatabaseUnavailability.sqlState(null)).isEqualTo("-");
    }

    /** Encadena {@code runtimeNodes} excepciones por causa y deja {@code bottom} al fondo. */
    private static Throwable chain(int runtimeNodes, Throwable bottom) {
        var current = bottom;
        for (var i = 0; i < runtimeNodes; i++) {
            current = new RuntimeException("n" + i, current);
        }
        return current;
    }
}
