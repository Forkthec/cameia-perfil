package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileNotFoundException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAppServiceTest {

    @Mock
    ProfessionalProfileRepository repository;

    @InjectMocks
    ProfileAppService service;

    // ── CM-16: createProfile ──────────────────────────────────────────────

    @Test
    void createProfile_savesProfileAndReturnsIt() {
        when(repository.existsByFirebaseUid(any())).thenReturn(false);

        var result = service.createProfile(new CreateProfileCommand("uid-new-001"));

        assertThat(result.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        assertThat(result.getFirebaseUid().value()).isEqualTo("uid-new-001");
        verify(repository).save(result);
    }

    @Test
    void createProfile_throwsAlreadyExistsWhenUidAlreadyRegistered() {
        when(repository.existsByFirebaseUid(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("uid-dup")))
                .isInstanceOf(ProfileAlreadyExistsException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createProfile_checksExistenceWithCorrectUid() {
        when(repository.existsByFirebaseUid(any())).thenReturn(false);

        service.createProfile(new CreateProfileCommand("uid-check-123"));

        var captor = ArgumentCaptor.forClass(FirebaseUid.class);
        verify(repository).existsByFirebaseUid(captor.capture());
        assertThat(captor.getValue().value()).isEqualTo("uid-check-123");
    }

    @Test
    void createProfile_rejectsBlankFirebaseUid() {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).existsByFirebaseUid(any());
    }

    // ── loadProfile ───────────────────────────────────────────────────────

    @Test
    void loadProfile_throwsNotFoundWhenProfileDoesNotExist() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        var id = UUID.randomUUID();
        assertThatThrownBy(() -> service.loadProfile(id))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
