package co.edu.unicauca.cameia.perfil.presentation.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class RedactedExceptionTest {

    @Test
    @DisplayName("La copia conserva la clase y la traza de cada causa y descarta los mensajes")
    void of_shouldKeepClassesAndFrames_whenChainHasMessages() {
        var original = new IllegalStateException("dato personal", new IllegalArgumentException("otro dato"));

        var copy = RedactedException.of(original);

        assertThat(copy.getMessage()).isEqualTo(IllegalStateException.class.getName());
        assertThat(copy.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(copy.getCause().getMessage()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(copy.getCause().getCause()).isNull();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo se copia hasta la primera repetición")
    void of_shouldStop_whenChainHasCycle() {
        var first = new RuntimeException("a");
        var second = new RuntimeException("b", first);
        first.initCause(second);

        var copy = RedactedException.of(first);

        assertThat(copy.getCause()).isNotNull();
        assertThat(copy.getCause().getCause()).isNull();
    }

    @Test
    @DisplayName("El SQLState sale de la primera SQLException de la cadena, o es un guion si no hay")
    void sqlState_shouldReturnCodeOrDash_whenChainHasOrLacksSqlException() {
        var withSql = new IllegalStateException("x", new SQLException("valor", "55P03"));

        assertThat(RedactedException.sqlState(withSql)).isEqualTo("55P03");
        assertThat(RedactedException.sqlState(new SQLException("sin estado"))).isEqualTo("-");
        assertThat(RedactedException.sqlState(new IllegalStateException("x"))).isEqualTo("-");
    }

    @Test
    @DisplayName("El origen es la clase y el método donde se lanzó, o desconocido si no hay traza")
    void origin_shouldNameFrameOrUnknown_whenTraceIsPresentOrEmpty() {
        var withTrace = new IllegalStateException();
        var withoutTrace = new IllegalStateException();
        withoutTrace.setStackTrace(new StackTraceElement[0]);

        assertThat(RedactedException.origin(withTrace))
                .isEqualTo(RedactedExceptionTest.class.getName() + ".origin_shouldNameFrameOrUnknown_whenTraceIsPresentOrEmpty");
        assertThat(RedactedException.origin(withoutTrace)).isEqualTo("desconocido");
    }
}
