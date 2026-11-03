package co.edu.unicauca.cameia.perfil.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El rol con sus dos idiomas nunca deja el nombre en inglés vacío y exige el nombre en español. */
class ProfessionalRoleNamesTest {

    private static final UUID ID = UUID.fromString("a0000001-0000-0000-0000-000000000002");

    @Test
    @DisplayName("Con los dos nombres y el estado se construye tal cual")
    void constructor_shouldKeepComponents_whenAllArePresent() {
        var role = new ProfessionalRoleNames(ID, "Desarrollador Backend", "Backend Developer", true);

        assertThat(role.id()).isEqualTo(ID);
        assertThat(role.nameEs()).isEqualTo("Desarrollador Backend");
        assertThat(role.nameEn()).isEqualTo("Backend Developer");
        assertThat(role.active()).isTrue();
    }

    @ParameterizedTest(name = "nombre en inglés = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Sin nombre en inglés usa el español, para no violar el contrato")
    void constructor_shouldFallBackToSpanish_whenEnglishNameIsMissing(String nameEn) {
        var role = new ProfessionalRoleNames(ID, "Desarrollador Backend", nameEn, false);

        assertThat(role.nameEn()).isEqualTo("Desarrollador Backend");
        assertThat(role.active()).isFalse();
    }

    @ParameterizedTest(name = "nombre en español = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Sin nombre en español se rechaza: la instantánea sería inválida")
    void constructor_shouldReject_whenSpanishNameIsMissing(String nameEs) {
        assertThatThrownBy(() -> new ProfessionalRoleNames(ID, nameEs, "Backend Developer", true))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Sin identificador se rechaza con NullPointerException")
    void constructor_shouldReject_whenIdIsMissing() {
        assertThatThrownBy(() -> new ProfessionalRoleNames(null, "Desarrollador Backend", "Backend Developer", true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Un nombre de 255 puntos de código se acepta aunque ocupe más de 255 caracteres UTF-16")
    void constructor_shouldAccept_whenNamesHaveExactlyTheMaximumCodePoints() {
        var name = Character.toString(0x1F600).repeat(ProfessionalRoleNames.MAX_NAME_LENGTH);

        var role = new ProfessionalRoleNames(ID, name, name, true);

        assertThat(role.nameEs()).isEqualTo(name);
        assertThat(role.nameEn()).isEqualTo(name);
    }

    @Test
    @DisplayName("Un nombre en español de 256 puntos de código se rechaza")
    void constructor_shouldReject_whenSpanishNameExceedsTheMaximum() {
        var name = "a".repeat(ProfessionalRoleNames.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new ProfessionalRoleNames(ID, name, "Backend Developer", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un nombre en inglés de 256 puntos de código se rechaza")
    void constructor_shouldReject_whenEnglishNameExceedsTheMaximum() {
        var name = "a".repeat(ProfessionalRoleNames.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new ProfessionalRoleNames(ID, "Desarrollador Backend", name, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
