package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfessionalRoleAppServiceTest {

    private final ProfessionalRoleRepository repository = mock(ProfessionalRoleRepository.class);
    private final ProfessionalRoleAppService service = new ProfessionalRoleAppService(repository);

    @Test
    @DisplayName("Listar el catálogo pide al repositorio el idioma recibido y devuelve su lista")
    void listAllByLang_shouldDelegateLanguage_whenCalled() {
        var roles = List.of(new ProfessionalRole(
                UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Backend Developer", "Software", true));
        when(repository.findAllByLang("en")).thenReturn(roles);

        var result = service.listAllByLang("en");

        verify(repository).findAllByLang("en");
        assertThat(result).isSameAs(roles);
    }
}
