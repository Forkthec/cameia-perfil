package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restricciones del estado del catálogo de roles y de la tabla de Roles Objetivo contra PostgreSQL real.
 *
 * <p>Cada restricción se viola a propósito y se comprueba su nombre o su estado SQL, porque el esquema repite lo que valida el
 * código y una violación nunca debe terminar en un error genérico.</p>
 */
@SpringBootTest
@Testcontainers
class TargetRoleSchemaIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROFILE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ROLE_ID = "a0000001-0000-0000-0000-000000000001";
    private static final String OTHER_ROLE_ID = "a0000001-0000-0000-0000-000000000002";
    private static final String INSERT_TARGET_ROLE = """
            INSERT INTO rol_objetivo (id, perfil_id, nombre_rol, rol_profesional_id, procedencia, estado_revision, creado_en)
            VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, now())
            """;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void createProfile() {
        jdbc.update("""
                INSERT INTO perfil_profesional (id, firebase_uid, creado_en, actualizado_en)
                VALUES (?::uuid, 'uid-schema-roles', now(), now())
                """, PROFILE_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM rol_objetivo WHERE perfil_id = ?::uuid", PROFILE_ID);
        jdbc.update("DELETE FROM perfil_profesional WHERE id = ?::uuid", PROFILE_ID);
        jdbc.update("UPDATE rol_profesional SET estado = 'ACTIVE'");
    }

    @Test
    @DisplayName("Todos los roles del catálogo quedan activos al migrar")
    void catalog_shouldStartActive_whenMigrationRuns() {
        Integer notActive = jdbc.queryForObject("SELECT count(*) FROM rol_profesional WHERE estado <> 'ACTIVE'", Integer.class);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM rol_profesional", Integer.class);

        assertThat(notActive).isZero();
        assertThat(total).isPositive();
    }

    @Test
    @DisplayName("El catálogo rechaza un estado desconocido")
    void catalog_shouldRejectUnknownStatus_whenUpdated() {
        assertThatThrownBy(() -> jdbc.update("UPDATE rol_profesional SET estado = 'PAUSED' WHERE id = ?::uuid", ROLE_ID))
                .hasMessageContaining("ck_rol_profesional_estado");
    }

    @Test
    @DisplayName("El catálogo acepta desactivar un rol")
    void catalog_shouldAcceptInactive_whenUpdated() {
        int rows = jdbc.update("UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = ?::uuid", ROLE_ID);

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Una fila válida de Rol Objetivo se inserta")
    void insert_shouldSucceed_whenRowIsValid() {
        int rows = insertTargetRole("22222222-2222-4222-8222-222222222221", ROLE_ID, "MANUAL", "PENDING_REVIEW");

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("El Rol Objetivo sin rol del catálogo se rechaza")
    void insert_shouldFail_whenCatalogRoleIsNull() {
        assertThatThrownBy(() -> insertTargetRole("22222222-2222-4222-8222-222222222221", null, "MANUAL", "PENDING_REVIEW"))
                .hasMessageContaining("rol_profesional_id")
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23502"));
    }

    @Test
    @DisplayName("El mismo rol del catálogo no se repite en un perfil")
    void insert_shouldFail_whenRoleRepeatsInProfile() {
        insertTargetRole("22222222-2222-4222-8222-222222222221", ROLE_ID, "MANUAL", "PENDING_REVIEW");

        assertThatThrownBy(() -> insertTargetRole("22222222-2222-4222-8222-222222222222", ROLE_ID, "MANUAL", "PENDING_REVIEW"))
                .hasMessageContaining("uq_rol_objetivo_perfil_rol_profesional")
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"));
    }

    @Test
    @DisplayName("Una procedencia desconocida se rechaza")
    void insert_shouldFail_whenProvenanceIsUnknown() {
        assertThatThrownBy(() -> insertTargetRole("22222222-2222-4222-8222-222222222221", ROLE_ID, "IMPORTED", "PENDING_REVIEW"))
                .hasMessageContaining("ck_rol_objetivo_procedencia");
    }

    @Test
    @DisplayName("Un estado de revisión desconocido se rechaza")
    void insert_shouldFail_whenReviewStatusIsUnknown() {
        assertThatThrownBy(() -> insertTargetRole("22222222-2222-4222-8222-222222222221", ROLE_ID, "MANUAL", "DONE"))
                .hasMessageContaining("ck_rol_objetivo_estado_revision");
    }

    @Test
    @DisplayName("Un rol del catálogo en uso no se borra")
    void deleteCatalogRole_shouldFail_whenTargetRoleUsesIt() {
        insertTargetRole("22222222-2222-4222-8222-222222222221", OTHER_ROLE_ID, "MANUAL", "PENDING_REVIEW");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM rol_profesional WHERE id = ?::uuid", OTHER_ROLE_ID))
                .hasMessageContaining("fk_rol_objetivo_rol_profesional")
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23503"));
    }

    private int insertTargetRole(String id, String roleId, String provenance, String reviewStatus) {
        return jdbc.update(INSERT_TARGET_ROLE, id, PROFILE_ID, "Rol de prueba", roleId, provenance, reviewStatus);
    }

    private static String sqlState(Throwable thrown) {
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        if (cause instanceof SQLException sql) {
            return sql.getSQLState();
        }
        throw new AssertionError("La excepción no trae SQLException: " + thrown.getClass(), thrown);
    }
}
