package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfileAlreadyExistsException;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @WebMvcTest no existe en Spring Boot 4.1.1 — se usa Mockito puro.
// Las pruebas HTTP-level (JSON serialization, headers) se validan con Postman.
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock ProfileAppService profileAppService;
    @InjectMocks ProfileController controller;

    @Test
    void createProfile_delegatesAndReturns201() {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-ctrl-001"));
        when(profileAppService.createProfile(any(CreateProfileCommand.class))).thenReturn(profile);

        var response = controller.createProfile("uid-ctrl-001");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("IN_PROGRESS");
        verify(profileAppService).createProfile(new CreateProfileCommand("uid-ctrl-001"));
    }

    @Test
    void createProfile_propagatesAlreadyExistsException() {
        when(profileAppService.createProfile(any())).thenThrow(new ProfileAlreadyExistsException());
        assertThatThrownBy(() -> controller.createProfile("uid-dup"))
                .isInstanceOf(ProfileAlreadyExistsException.class);
    }
}
