package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura del catálogo de roles profesionales, con su estado, contra PostgreSQL real. */
@SpringBootTest
@Testcontainers
class ProfessionalRoleRepositoryAdapterIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final UUID BACKEND_ID = UUID.fromString("a0000001-0000-0000-0000-000000000002");

    @Autowired
    ProfessionalRoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void restoreCatalog() {
        jdbc.update("UPDATE rol_profesional SET estado = 'ACTIVE'");
    }

    @Test
    @DisplayName("Un rol con estado ACTIVE se lee como activo")
    void findById_shouldReturnActiveRole_whenCatalogRowIsActive() {
        var role = roles.findById(BACKEND_ID);

        assertThat(role).isPresent();
        assertThat(role.get().nombre()).isEqualTo("Desarrollador Backend");
        assertThat(role.get().active()).isTrue();
    }

    @Test
    @DisplayName("Un rol con estado INACTIVE se lee como inactivo y no desaparece")
    void findById_shouldReturnInactiveRole_whenCatalogRowIsInactive() {
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?", BACKEND_ID);

        var role = roles.findById(BACKEND_ID);

        assertThat(role).isPresent();
        assertThat(role.get().active()).isFalse();
    }

    @Test
    @DisplayName("Un rol que no está en el catálogo no se encuentra")
    void findById_shouldReturnEmpty_whenRoleDoesNotExist() {
        assertThat(roles.findById(UUID.fromString("a0000001-0000-0000-0000-0000000000ff"))).isEmpty();
    }

    @Test
    @DisplayName("En español el catálogo sale ordenado por categoría y nombre")
    void findAllByLang_shouldOrderByCategoryAndName_whenSpanish() {
        List<ProfessionalRole> all = roles.findAllByLang("es");

        assertThat(all).isNotEmpty();
        assertThat(all.getFirst().categoria()).isEqualTo("Calidad");
        assertThat(all).extracting(ProfessionalRole::categoria).isSorted();
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "idioma {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"es", "en"})
    @DisplayName("El catálogo no ofrece un rol inactivo y ofrece todos los activos")
    void findAllByLang_shouldExcludeInactive_whenCatalogHasInactiveRole(String lang) {
        var inactive = UUID.fromString("a0000007-0000-0000-0000-000000000006");
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?", inactive);
        var active = jdbc.queryForObject("SELECT count(*) FROM rol_profesional WHERE estado = 'ACTIVE'", Integer.class);

        var all = roles.findAllByLang(lang);

        assertThat(all).extracting(ProfessionalRole::id).doesNotContain(inactive);
        assertThat(all).hasSize(active).allMatch(ProfessionalRole::active);
    }

    @Test
    @DisplayName("En inglés el catálogo sale con los nombres en inglés")
    void findAllByLang_shouldUseEnglish_whenLangIsEn() {
        var backend = roles.findAllByLang("en").stream().filter(r -> r.id().equals(BACKEND_ID)).findFirst();

        assertThat(backend).isPresent();
        assertThat(backend.get().nombre()).isEqualTo("Backend Developer");
    }

    @Test
    @DisplayName("El catálogo para anunciar trae los 46 roles con los dos idiomas")
    void findAllWithBothLanguages_shouldReturnEveryRoleWithBothNames_whenCatalogIsSeeded() {
        var all = roles.findAllWithBothLanguages();

        assertThat(all).hasSize(46).allMatch(ProfessionalRoleNames::active);
        assertThat(all).extracting(ProfessionalRoleNames::id).doesNotHaveDuplicates();
        var backend = all.stream().filter(r -> r.id().equals(BACKEND_ID)).findFirst().orElseThrow();
        assertThat(backend.nameEs()).isEqualTo("Desarrollador Backend");
        assertThat(backend.nameEn()).isEqualTo("Backend Developer");
        assertThat(all).extracting(ProfessionalRoleNames::nameEs).isSorted();
    }

    @Test
    @DisplayName("El catálogo para anunciar incluye los roles inactivos, marcados como tales")
    void findAllWithBothLanguages_shouldIncludeInactiveRoles_whenOneIsDeactivated() {
        jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?", BACKEND_ID);

        var all = roles.findAllWithBothLanguages();

        assertThat(all).hasSize(46);
        assertThat(all).filteredOn(r -> r.id().equals(BACKEND_ID)).singleElement().extracting(ProfessionalRoleNames::active)
                .isEqualTo(false);
        assertThat(all.stream().filter(ProfessionalRoleNames::active)).hasSize(45);
    }

    @Test
    @DisplayName("Un nombre en inglés en blanco se anuncia con el nombre en español")
    void findAllWithBothLanguages_shouldFallBackToSpanish_whenEnglishNameIsBlank() {
        jdbc.update("UPDATE rol_profesional SET nombre_en = '   ' WHERE id = ?", BACKEND_ID);

        try {
            var backend = roles.findAllWithBothLanguages().stream().filter(r -> r.id().equals(BACKEND_ID)).findFirst().orElseThrow();

            assertThat(backend.nameEn()).isEqualTo("Desarrollador Backend");
        } finally {
            jdbc.update("UPDATE rol_profesional SET nombre_en = 'Backend Developer' WHERE id = ?", BACKEND_ID);
        }
    }
}
