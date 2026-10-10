package co.edu.unicauca.cameia.perfil.domain.model;

import co.edu.unicauca.cameia.perfil.domain.exception.ErrorCode;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidFieldsException;
import co.edu.unicauca.cameia.perfil.domain.exception.ProfessionalRoleNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regla de elección del Rol profesional: solo un rol activo se puede elegir. */
class ProfessionalRoleTest {

    @Test
    @DisplayName("Un rol activo se puede elegir")
    void requireSelectable_shouldReturnRole_whenRoleIsActive() {
        var role = new ProfessionalRole(UUID.randomUUID(), "Desarrollador Backend", "Desarrollo", true);

        assertThat(role.requireSelectable()).isSameAs(role);
    }

    @Test
    @DisplayName("Un rol inactivo se rechaza con el campo professionalRoleId y el texto del rol inexistente")
    void requireSelectable_shouldRejectField_whenRoleIsInactive() {
        var role = new ProfessionalRole(UUID.randomUUID(), "Desarrollador Backend", "Desarrollo", false);

        assertThatThrownBy(role::requireSelectable)
                .isInstanceOfSatisfying(InvalidFieldsException.class, e -> assertThat(e.getErrors())
                        .singleElement()
                        .satisfies(error -> {
                            assertThat(error.field()).isEqualTo("professionalRoleId");
                            assertThat(error.code()).isEqualTo(ErrorCode.PROFESSIONAL_ROLE_DISABLED);
                            assertThat(error.message()).isEqualTo("No encontramos ese rol en el catálogo.")
                                    .isEqualTo(new ProfessionalRoleNotFoundException().getMessage());
                        }));
    }
}
