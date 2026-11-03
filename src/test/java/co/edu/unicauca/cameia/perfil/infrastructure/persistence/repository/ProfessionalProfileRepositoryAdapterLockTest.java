package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Un fallo de persistencia que no es el corte de la espera por un bloqueo sale tal cual: solo el estado
 * {@code 55P03} se traduce al 409, nunca otro fallo. Los cortes reales los cubren las pruebas de integración.
 */
class ProfessionalProfileRepositoryAdapterLockTest {

    private ProfessionalProfileJpaRepository jpa;
    private Query query;
    private ProfessionalProfileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpa = mock(ProfessionalProfileJpaRepository.class);
        query = mock(Query.class, RETURNS_SELF);
        var em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        adapter = new ProfessionalProfileRepositoryAdapter(jpa);
        ReflectionTestUtils.setField(adapter, "em", em);
    }

    @Test
    @DisplayName("Un fallo de lectura que no es de bloqueo sale sin convertirse en 409")
    void findByIdForUpdate_shouldRethrowSameFailure_whenFailureIsNotALockTimeout() {
        var failure = new PersistenceException("otra causa");
        when(jpa.findLockedById(any())).thenThrow(failure);

        assertThatThrownBy(() -> adapter.findByIdForUpdate(ProfileId.of(UUID.randomUUID()))).isSameAs(failure);
    }

    @Test
    @DisplayName("Un fallo al tomar el bloqueo de creación que no es de bloqueo sale sin convertirse en 409")
    void lockCreationFor_shouldRethrowSameFailure_whenFailureIsNotALockTimeout() {
        var failure = new PersistenceException("otra causa");
        when(query.getSingleResult()).thenThrow(failure);

        assertThatThrownBy(() -> adapter.lockCreationFor(new FirebaseUid("uid-lock-unit"))).isSameAs(failure);
    }
}
