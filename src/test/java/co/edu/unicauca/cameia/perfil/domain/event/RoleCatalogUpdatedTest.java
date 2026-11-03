package co.edu.unicauca.cameia.perfil.domain.event;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El hecho «el catálogo queda así» exige de 1 a 200 roles y todos sus componentes. */
class RoleCatalogUpdatedTest {

    private static final UUID EVENT_ID = UUID.fromString("0b9f8e7d-6c5b-4a39-8271-605f4e3d2c1b");
    private static final Instant AT = Instant.parse("2026-10-24T12:00:00.123Z");
    private static final CorrelationId CORRELATION = new CorrelationId("perfil-catalogo-0b9f8e7d");

    private enum Missing { EVENT_ID, ROLES, UPDATED_AT, CORRELATION_ID }

    private static List<ProfessionalRoleNames> roles(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ProfessionalRoleNames(new UUID(1L, i), "Rol " + i, "Role " + i, i % 2 == 0))
                .toList();
    }

    private static RoleCatalogUpdated eventWith(List<ProfessionalRoleNames> roles) {
        return new RoleCatalogUpdated(EVENT_ID, roles, AT, CORRELATION);
    }

    @Test
    @DisplayName("El tipo, la versión y el agregado son los del contrato")
    void constants_shouldMatchTheContract() {
        assertThat(RoleCatalogUpdated.TYPE).isEqualTo("perfil.catalogo-roles-actualizado");
        assertThat(RoleCatalogUpdated.VERSION).isEqualTo(1);
        assertThat(RoleCatalogUpdated.MAX_ROLES).isEqualTo(200);
        assertThat(RoleCatalogUpdated.AGGREGATE_ID).hasToString("00000000-0000-0000-0000-000000000000");
    }

    @Test
    @DisplayName("Un catálogo sin roles se rechaza")
    void constructor_shouldReject_whenThereAreNoRoles() {
        assertThatThrownBy(() -> eventWith(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Con 1 rol y con 200 roles se construye")
    void constructor_shouldAccept_whenRolesAreAtTheLimits() {
        assertThat(eventWith(roles(1)).roles()).hasSize(1);
        assertThat(eventWith(roles(200)).roles()).hasSize(200);
    }

    @Test
    @DisplayName("Con 201 roles se rechaza: es uno más que el máximo del contrato")
    void constructor_shouldReject_whenThereAreMoreThanTwoHundredRoles() {
        assertThatThrownBy(() -> eventWith(roles(201))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "falta {0}")
    @EnumSource(Missing.class)
    @DisplayName("Falta un componente: se rechaza con NullPointerException")
    void constructor_shouldRejectNulls_whenAnyComponentIsMissing(Missing missing) {
        assertThatThrownBy(() -> new RoleCatalogUpdated(
                missing == Missing.EVENT_ID ? null : EVENT_ID,
                missing == Missing.ROLES ? null : roles(2),
                missing == Missing.UPDATED_AT ? null : AT,
                missing == Missing.CORRELATION_ID ? null : CORRELATION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Un rol nulo dentro de la lista se rechaza")
    void constructor_shouldReject_whenARoleIsNull() {
        var withNull = new ArrayList<ProfessionalRoleNames>(roles(2));
        withNull.add(null);

        assertThatThrownBy(() -> eventWith(withNull)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La lista queda copiada: cambiar la original después no cambia el evento")
    void constructor_shouldCopyRoles_whenListIsMutable() {
        var mutable = new ArrayList<>(roles(2));
        var event = eventWith(mutable);

        mutable.clear();

        assertThat(event.roles()).hasSize(2);
        assertThatThrownBy(() -> event.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
