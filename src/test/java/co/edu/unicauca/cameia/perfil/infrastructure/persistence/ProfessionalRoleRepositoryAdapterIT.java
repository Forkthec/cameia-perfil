package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
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

    @Test
    @DisplayName("En inglés el catálogo sale con los nombres en inglés")
    void findAllByLang_shouldUseEnglish_whenLangIsEn() {
        var backend = roles.findAllByLang("en").stream().filter(r -> r.id().equals(BACKEND_ID)).findFirst();

        assertThat(backend).isPresent();
        assertThat(backend.get().nombre()).isEqualTo("Backend Developer");
    }
}
