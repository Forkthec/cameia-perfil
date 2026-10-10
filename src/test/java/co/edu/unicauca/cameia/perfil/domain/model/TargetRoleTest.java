package co.edu.unicauca.cameia.perfil.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes del Rol Objetivo: título de 1 a 255 caracteres y rol del catálogo obligatorio. */
class TargetRoleTest {

    private static final UUID ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final UUID OTHER_ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000003");

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("El título vacío o en blanco se rechaza")
    void constructor_shouldRejectTitle_whenBlank(String title) {
        assertThatThrownBy(() -> new TargetRole(UUID.randomUUID(), ROLE_ID, title, DataProvenance.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un título de 256 caracteres se rechaza")
    void constructor_shouldRejectTitle_whenTooLong() {
        var title = "a".repeat(256);

        assertThatThrownBy(() -> new TargetRole(UUID.randomUUID(), ROLE_ID, title, DataProvenance.MANUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un título de 255 caracteres se acepta")
    void constructor_shouldAccept_whenTitleHas255Characters() {
        var title = "a".repeat(255);

        assertThatCode(() -> new TargetRole(UUID.randomUUID(), ROLE_ID, title, DataProvenance.MANUAL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El rol del catálogo es obligatorio")
    void constructor_shouldRequireCatalogRole_whenRoleIdIsNull() {
        assertThatThrownBy(() -> new TargetRole(UUID.randomUUID(), null, "Backend", DataProvenance.MANUAL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("professionalRoleId es obligatorio");
    }

    @Test
    @DisplayName("Dos Roles Objetivo son el mismo rol cuando comparten el rol del catálogo")
    void isSameRoleAs_shouldCompareCatalogRole_whenIdsDiffer() {
        var first = new TargetRole(UUID.randomUUID(), ROLE_ID, "Backend", DataProvenance.MANUAL);
        var sameRole = new TargetRole(UUID.randomUUID(), ROLE_ID, "Backend", DataProvenance.MANUAL);
        var otherRole = new TargetRole(UUID.randomUUID(), OTHER_ROLE_ID, "Frontend", DataProvenance.MANUAL);

        assertThat(first.isSameRoleAs(sameRole)).isTrue();
        assertThat(first.isSameRoleAs(otherRole)).isFalse();
    }
}
