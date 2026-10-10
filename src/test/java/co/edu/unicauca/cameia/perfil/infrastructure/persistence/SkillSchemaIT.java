package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Esquema de {@code habilidad_perfil} tras la migración V8, contra PostgreSQL real: conserva los datos de la
 * versión anterior, rellena la clave de comparación, rechaza cada fila que rompe una restricción y usa nombres estándar.
 */
@Testcontainers
class SkillSchemaIT {

    /** PostgreSQL real; la clase migra por su cuenta para poder detenerse en una versión intermedia. */
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROFILE_ID = "22222222-2222-4222-8222-222222222222";

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .cleanDisabled(false).target(target).load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void migrateFresh() {
        var flyway = flyway("latest");
        flyway.clean();
        flyway.migrate();
    }

    private static void insertProfile(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("INSERT INTO perfil_profesional (id, firebase_uid, creado_en, actualizado_en) VALUES ('"
                    + PROFILE_ID + "', 'uid-skill-schema', now(), now())");
        }
    }

    private static void insertSkill(Connection connection, String name, String key, String level, String provenance)
            throws SQLException {
        var sql = "INSERT INTO habilidad_perfil (id, perfil_id, nombre_habilidad, nombre_normalizado, nivel, procedencia) "
                + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, PROFILE_ID);
            statement.setString(3, name);
            statement.setString(4, key);
            statement.setString(5, level);
            statement.setString(6, provenance);
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("La migración conserva una habilidad guardada con el esquema anterior y rellena su clave")
    void migration_shouldFillComparisonKey_whenAppliedOnDataFromV7() throws Exception {
        var older = flyway("7");
        older.clean();
        older.migrate();
        try (var connection = connection()) {
            insertProfile(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO habilidad_perfil (id, perfil_id, nombre_habilidad, nivel, procedencia) VALUES ('"
                        + UUID.randomUUID() + "', '" + PROFILE_ID + "', '  Programación   Web ', 'BASIC', 'MANUAL')");
            }
        }

        flyway("latest").migrate();

        try (var connection = connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT nombre_habilidad, nombre_normalizado, nivel, procedencia FROM habilidad_perfil")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("nombre_habilidad")).isEqualTo("  Programación   Web ");
            assertThat(rows.getString("nombre_normalizado")).isEqualTo("programacion web");
            assertThat(rows.getString("nivel")).isEqualTo("BASIC");
            assertThat(rows.getString("procedencia")).isEqualTo("MANUAL");
            assertThat(rows.next()).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenRows")
    @DisplayName("Cada fila que rompe una restricción se rechaza con su nombre")
    void constraint_shouldReject_whenRowBreaksIt(String name, String skill, String level, String provenance,
            String constraint, String sqlState) throws Exception {
        migrateFresh();
        try (var connection = connection()) {
            insertProfile(connection);
            insertSkill(connection, "Python", "python", "BASIC", "MANUAL");
            assertThatThrownBy(() -> insertSkill(connection, skill, skill.strip().toLowerCase(), level, provenance))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo(sqlState);
                        assertThat(e.getMessage()).contains(constraint);
                    });
        }
    }

    static Stream<Arguments> brokenRows() {
        return Stream.of(
                Arguments.of("nombre en blanco", "  ", "BASIC", "MANUAL", "ck_habilidad_perfil_nombre_presente", "23514"),
                Arguments.of("nivel desconocido", "Java", "EXPERT", "MANUAL", "ck_habilidad_perfil_nivel", "23514"),
                Arguments.of("procedencia desconocida", "Java", "BASIC", "HUMAN", "ck_habilidad_perfil_procedencia", "23514"),
                Arguments.of("clave repetida en el perfil", "PYTHON", "BASIC", "MANUAL", "uq_habilidad_perfil_nombre", "23505"));
    }

    @Test
    @DisplayName("La misma clave se acepta en otro perfil")
    void constraint_shouldAccept_whenSameKeyBelongsToAnotherProfile() throws Exception {
        migrateFresh();
        try (var connection = connection(); var statement = connection.createStatement()) {
            insertProfile(connection);
            statement.execute("INSERT INTO perfil_profesional (id, firebase_uid, creado_en, actualizado_en) VALUES "
                    + "('33333333-3333-4333-8333-333333333333', 'uid-skill-schema-2', now(), now())");
            insertSkill(connection, "Python", "python", "BASIC", "MANUAL");

            statement.execute("INSERT INTO habilidad_perfil (id, perfil_id, nombre_habilidad, nombre_normalizado, nivel, procedencia) VALUES ('"
                    + UUID.randomUUID() + "', '33333333-3333-4333-8333-333333333333', 'Python', 'python', 'BASIC', 'MANUAL')");
        }
    }

    @Test
    @DisplayName("Un nombre de 61 caracteres no cabe en la columna")
    void column_shouldReject_whenNameExceedsSixty() throws Exception {
        migrateFresh();
        try (var connection = connection()) {
            insertProfile(connection);

            assertThatThrownBy(() -> insertSkill(connection, "a".repeat(61), "a", "BASIC", "MANUAL"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("22001"));
        }
    }

    @Test
    @DisplayName("La clave primaria, la clave foránea y el índice tienen nombres estándar")
    void constraints_shouldHaveStandardNames_whenSchemaIsMigrated() throws Exception {
        migrateFresh();
        try (var connection = connection(); var statement = connection.createStatement()) {
            var names = new ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT conname FROM pg_constraint WHERE conrelid = 'habilidad_perfil'::regclass")) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
            var indexes = new ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'habilidad_perfil'")) {
                while (rows.next()) {
                    indexes.add(rows.getString(1));
                }
            }

            assertThat(names).contains("pk_habilidad_perfil", "fk_habilidad_perfil_perfil", "uq_habilidad_perfil_nombre");
            assertThat(indexes).contains("ix_habilidad_perfil_perfil_id");
        }
    }
}
