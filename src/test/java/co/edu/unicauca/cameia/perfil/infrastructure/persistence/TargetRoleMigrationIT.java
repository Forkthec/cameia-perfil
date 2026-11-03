package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migración que endurece la tabla de Roles Objetivo, probada contra una base que ya tiene datos.
 *
 * <p>Una base vacía no demuestra nada: la migración agrega {@code NOT NULL} y una restricción de unicidad sobre filas que
 * ya existen. Aquí se migra primero hasta la versión previa, se insertan filas y recién entonces se aplica la migración.</p>
 */
@Testcontainers
class TargetRoleMigrationIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROFILE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ROLE_ID = "a0000001-0000-0000-0000-000000000001";
    private static final String OTHER_ROLE_ID = "a0000001-0000-0000-0000-000000000002";
    private static final String INSERT_TARGET_ROLE = """
            INSERT INTO rol_objetivo (id, perfil_id, nombre_rol, rol_profesional_id, procedencia, estado_revision, creado_en)
            VALUES (gen_random_uuid(), ?::uuid, 'Rol', ?::uuid, ?, ?, now())
            """;

    private JdbcTemplate jdbc;

    /** Deja la base en la versión previa a la migración, con el perfil que referencian las filas de cada prueba. */
    @BeforeEach
    void migrateUntilPreviousVersion() {
        flyway("5").clean();
        flyway("5").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("""
                INSERT INTO perfil_profesional (id, firebase_uid, creado_en, actualizado_en)
                VALUES (?::uuid, 'uid-migration-roles', now(), now())
                """, PROFILE_ID);
    }

    @Test
    @DisplayName("Con filas válidas migra, conserva la procedencia y deja todo el catálogo activo")
    void migrate_shouldKeepRowsAndActivateCatalog_whenDataIsValid() {
        jdbc.update(INSERT_TARGET_ROLE, PROFILE_ID, ROLE_ID, "AI_SUGGESTED", "PENDING_REVIEW");
        jdbc.update(INSERT_TARGET_ROLE, PROFILE_ID, OTHER_ROLE_ID, "AI_EDITED", "REVIEWED");

        flyway("5.1").migrate();

        assertThat(jdbc.queryForList("SELECT procedencia FROM rol_objetivo ORDER BY procedencia", String.class))
                .containsExactly("AI_EDITED", "AI_SUGGESTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rol_profesional WHERE estado <> 'ACTIVE'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Con un rol repetido en el perfil aborta y deja el esquema en la versión anterior")
    void migrate_shouldAbortAndKeepPreviousSchema_whenRoleRepeatsInProfile() {
        jdbc.update(INSERT_TARGET_ROLE, PROFILE_ID, ROLE_ID, "MANUAL", "PENDING_REVIEW");
        jdbc.update(INSERT_TARGET_ROLE, PROFILE_ID, ROLE_ID, "MANUAL", "PENDING_REVIEW");

        assertThatThrownBy(() -> flyway("5.1").migrate()).isInstanceOf(FlywayException.class);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'rol_profesional' AND column_name = 'estado'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rol_objetivo", Integer.class)).isEqualTo(2);
    }

    /** Flyway sobre la base de la prueba, limitado a la versión indicada. */
    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(target)
                .load();
    }
}
