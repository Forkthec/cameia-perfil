package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.exception.DuplicateTargetRoleException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("Reconoce la restricción de unicidad aunque venga envuelta y con otras mayúsculas")
    void violates_shouldBeTrue_whenConstraintNameMatchesInTheCause() {
        var hibernate = new ConstraintViolationException("x", new SQLException("x", "23505"),
                ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE.toUpperCase());

        assertThat(ProfessionalProfileRepositoryAdapter.violates(
                new RuntimeException("envoltorio", hibernate), ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE)).isTrue();
    }

    @Test
    @DisplayName("Otra restricción o una cadena sin la excepción de Hibernate no se toma por la de unicidad")
    void violates_shouldBeFalse_whenConstraintIsAnotherOrMissing() {
        var other = new ConstraintViolationException("x", new SQLException("x", "23503"), "fk_otra");

        assertThat(ProfessionalProfileRepositoryAdapter.violates(other, ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE)).isFalse();
        assertThat(ProfessionalProfileRepositoryAdapter.violates(
                new RuntimeException("sin causa"), ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE)).isFalse();
    }

    @Test
    @DisplayName("Una violación de otra restricción se relanza tal cual, sin tomarla por un rol repetido")
    void save_shouldRethrowSameException_whenViolationIsAnotherConstraint() {
        var jpa = mock(ProfessionalProfileJpaRepository.class);
        var thrown = new DataIntegrityViolationException("x",
                new ConstraintViolationException("m", new SQLException("x", "23514"), "ck_rol_objetivo_procedencia"));
        when(jpa.saveAndFlush(any())).thenThrow(thrown);
        var profile = ProfessionalProfile.create(new FirebaseUid("firebase-test-uid-001"));

        assertThatThrownBy(() -> new ProfessionalProfileRepositoryAdapter(jpa).save(profile)).isSameAs(thrown);
    }

    @Test
    @DisplayName("Una violación de la unicidad del rol objetivo se traduce a su error de negocio")
    void save_shouldThrowDuplicateTargetRole_whenUniqueConstraintIsViolated() {
        var jpa = mock(ProfessionalProfileJpaRepository.class);
        when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("x",
                new ConstraintViolationException("m", new SQLException("x", "23505"),
                        ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE)));
        var profile = ProfessionalProfile.create(new FirebaseUid("firebase-test-uid-001"));

        assertThatThrownBy(() -> new ProfessionalProfileRepositoryAdapter(jpa).save(profile))
                .isInstanceOf(DuplicateTargetRoleException.class);
    }

    @Test
    @DisplayName("Una cadena de más de 32 causas se corta y no se toma por la restricción de unicidad")
    void violates_shouldReturnFalse_whenChainExceedsLimit() {
        Throwable chain = new ConstraintViolationException("m", new SQLException("x", "23505"),
                ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE);
        for (var i = 0; i < 40; i++) {
            chain = new RuntimeException("capa " + i, chain);
        }

        assertThat(ProfessionalProfileRepositoryAdapter.violates(
                chain, ProfessionalProfileRepositoryAdapter.UNIQUE_TARGET_ROLE)).isFalse();
    }
}
