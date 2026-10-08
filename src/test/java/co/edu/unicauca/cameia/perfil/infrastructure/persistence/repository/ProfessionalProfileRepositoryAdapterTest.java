package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalProfileRepositoryAdapterTest {

    @Test
    @DisplayName("Reconoce la espera agotada de PostgreSQL aunque venga envuelta en otras excepciones")
    void isLockNotAvailable_shouldBeTrue_whenSqlStateIs55P03InTheCause() {
        var failure = new IllegalStateException("envoltorio", new RuntimeException(new SQLException("x", "55P03")));

        assertThat(ProfessionalProfileRepositoryAdapter.isLockNotAvailable(failure)).isTrue();
    }

    @Test
    @DisplayName("Otro estado de PostgreSQL no se toma por una espera agotada")
    void isLockNotAvailable_shouldBeFalse_whenSqlStateIsAnother() {
        var failure = new RuntimeException(new SQLException("x", "23505"));

        assertThat(ProfessionalProfileRepositoryAdapter.isLockNotAvailable(failure)).isFalse();
    }

    @Test
    @DisplayName("Una cadena sin excepción de SQL ni estado no es una espera agotada")
    void isLockNotAvailable_shouldBeFalse_whenThereIsNoSqlState() {
        assertThat(ProfessionalProfileRepositoryAdapter.isLockNotAvailable(new RuntimeException("sin causa"))).isFalse();
        assertThat(ProfessionalProfileRepositoryAdapter.isLockNotAvailable(new RuntimeException(new SQLException("sin estado")))).isFalse();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo termina y no es una espera agotada")
    void isLockNotAvailable_shouldStop_whenChainHasCycle() {
        var first = new RuntimeException("a");
        var second = new RuntimeException("b", first);
        first.initCause(second);

        assertThat(ProfessionalProfileRepositoryAdapter.isLockNotAvailable(first)).isFalse();
    }
}
