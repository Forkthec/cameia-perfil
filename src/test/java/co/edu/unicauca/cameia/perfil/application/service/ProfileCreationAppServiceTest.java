package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.IdentityRequiredException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileLimitReachedException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileCreationAppServiceTest {

    @Mock ProfessionalProfileRepository repository;

    ProfileCreationAppService service;

    @BeforeEach
    void setUp() {
        service = new ProfileCreationAppService(repository);
    }

    @Test
    @DisplayName("Un Usuario sin perfiles recibe un perfil vacío en curso")
    void createProfile_shouldCreate_whenUserHasNoProfiles() {
        when(repository.countByFirebaseUid(any())).thenReturn(0L, 0L);

        var result = service.createProfile(new CreateProfileCommand("uid-ana-001"));

        verify(repository, times(1)).save(result);
        assertThat(result.getStatus()).isEqualTo(ProfileStatus.IN_PROGRESS);
        assertThat(result.getName()).isNull();
    }

    @Test
    @DisplayName("Un Usuario que ya tenía su perfil antes de pedir recibe el cupo alcanzado")
    void createProfile_shouldThrowLimitReached_whenUserAlreadyHadOne() {
        when(repository.countByFirebaseUid(any())).thenReturn(1L, 1L);

        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("uid-ana-001")))
                .isInstanceOf(ProfileLimitReachedException.class)
                .hasMessage("Tu Plan Free permite 1 Perfil Profesional.");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Si otra petición del mismo Usuario creó el perfil mientras esperaba, devuelve ese perfil")
    void createProfile_shouldReturnJustCreated_whenAnotherRequestCreatedItMeanwhile() {
        var created = ProfessionalProfile.create(new FirebaseUid("uid-ana-001"));
        when(repository.countByFirebaseUid(any())).thenReturn(0L, 1L);
        when(repository.findLatestByFirebaseUid(any())).thenReturn(Optional.of(created));

        var result = service.createProfile(new CreateProfileCommand("uid-ana-001"));

        assertThat(result).isSameAs(created);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("El bloqueo por Usuario se toma entre los dos conteos")
    void createProfile_shouldLockBetweenCounts_whenCreating() {
        service.createProfile(new CreateProfileCommand("uid-ana-001"));

        var order = inOrder(repository);
        order.verify(repository).countByFirebaseUid(new FirebaseUid("uid-ana-001"));
        order.verify(repository).lockCreationFor(new FirebaseUid("uid-ana-001"));
        order.verify(repository).countByFirebaseUid(new FirebaseUid("uid-ana-001"));
    }

    @Test
    @DisplayName("El perfil de otro Usuario no cuenta para el cupo")
    void createProfile_shouldCreate_whenAnotherUserHasProfile() {
        when(repository.countByFirebaseUid(new FirebaseUid("uid-luis-002"))).thenReturn(0L, 0L);

        var result = service.createProfile(new CreateProfileCommand("uid-luis-002"));

        assertThat(result.getFirebaseUid().value()).isEqualTo("uid-luis-002");
    }

    @Test
    @DisplayName("Un perfil existente cuenta para el cupo sin importar su estado")
    void createProfile_shouldThrowLimitReached_whenExistingProfileIsActive() {
        when(repository.countByFirebaseUid(any())).thenReturn(1L, 1L);

        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("uid-ana-001")))
                .isInstanceOf(ProfileLimitReachedException.class)
                .hasMessage("Tu Plan Free permite 1 Perfil Profesional.");
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Una identidad en blanco se rechaza antes de consultar el repositorio")
    void createProfile_shouldThrowIdentityRequired_whenUidIsBlank(String uid) {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand(uid)))
                .isInstanceOf(IdentityRequiredException.class);
        verify(repository, never()).countByFirebaseUid(any());
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres no crea el perfil")
    void createProfile_shouldThrowIdentityRequired_whenUidIsTooLong() {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand("a".repeat(129))))
                .isInstanceOf(IdentityRequiredException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres crea el perfil")
    void createProfile_shouldSave_whenUidHasMaxLength() {
        var result = service.createProfile(new CreateProfileCommand("a".repeat(128)));
        assertThat(result.getFirebaseUid().value()).hasSize(128);
    }

    @Test
    @DisplayName("Sin identidad, crear un perfil se rechaza antes de consultar el repositorio")
    void createProfile_shouldThrowIdentityRequired_whenUidIsNull() {
        assertThatThrownBy(() -> service.createProfile(new CreateProfileCommand(null)))
                .isInstanceOf(IdentityRequiredException.class);
        verify(repository, never()).countByFirebaseUid(any());
    }
}
