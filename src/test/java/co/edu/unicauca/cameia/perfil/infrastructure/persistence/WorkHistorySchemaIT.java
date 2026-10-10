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
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Esquema de {@code experiencia_laboral} tras la migración V6, contra PostgreSQL real: conserva los datos de la
 * versión anterior, rechaza cada fila que rompe una restricción y usa nombres estándar.
 */
@Testcontainers
class WorkHistorySchemaIT {

    /** PostgreSQL real; la clase migra por su cuenta para poder detenerse en una versión intermedia. */
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROFILE_ID = "11111111-1111-4111-8111-111111111111";

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
                    + PROFILE_ID + "', 'uid-schema', now(), now())");
        }
    }

    private static void insertExperience(Connection connection, String company, String position, String status,
            String provenance, String start, String end) throws SQLException {
        var sql = "INSERT INTO experiencia_laboral (id, perfil_id, empresa, cargo, fecha_inicio, fecha_fin, estado_empleo, procedencia) "
                + "VALUES (?::uuid, ?::uuid, ?, ?, ?::date, ?::date, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, PROFILE_ID);
            statement.setString(3, company);
            statement.setString(4, position);
            statement.setString(5, start);
            statement.setString(6, end);
            statement.setString(7, status);
            statement.setString(8, provenance);
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("La migración conserva una experiencia guardada con el esquema anterior")
    void migration_shouldKeepExistingRow_whenAppliedOnDataFromV4() throws Exception {
        var older = flyway("4");
        older.clean();
        older.migrate();
        try (var connection = connection()) {
            insertProfile(connection);
            insertExperience(connection, "ACME", "Dev", "ENDED", "MANUAL", "2023-06-01", "2024-12-01");
        }

        flyway("latest").migrate();

        try (var connection = connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT empresa, cargo, fecha_inicio, fecha_fin, estado_empleo FROM experiencia_laboral")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("empresa")).isEqualTo("ACME");
            assertThat(rows.getString("cargo")).isEqualTo("Dev");
            assertThat(rows.getString("fecha_inicio")).isEqualTo("2023-06-01");
            assertThat(rows.getString("fecha_fin")).isEqualTo("2024-12-01");
            assertThat(rows.getString("estado_empleo")).isEqualTo("ENDED");
            assertThat(rows.next()).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenRows")
    @DisplayName("Cada fila que rompe una restricción se rechaza con su nombre")
    void constraint_shouldReject_whenRowBreaksIt(String name, String company, String position, String status,
            String provenance, String start, String end, String constraint) throws Exception {
        migrateFresh();
        try (var connection = connection()) {
            insertProfile(connection);
            assertThatThrownBy(() -> insertExperience(connection, company, position, status, provenance, start, end))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo("23514");
                        assertThat(e.getMessage()).contains(constraint);
                    });
        }
    }

    static Stream<Arguments> brokenRows() {
        return Stream.of(
                Arguments.of("empresa en blanco", "  ", "Dev", "CURRENT", "MANUAL", null, null, "ck_experiencia_laboral_empresa_presente"),
                Arguments.of("cargo en blanco", "ACME", " ", "CURRENT", "MANUAL", null, null, "ck_experiencia_laboral_cargo_presente"),
                Arguments.of("estado desconocido", "ACME", "Dev", "FREELANCE", "MANUAL", null, null, "ck_experiencia_laboral_estado_empleo"),
                Arguments.of("procedencia desconocida", "ACME", "Dev", "CURRENT", "HUMAN", null, null, "ck_experiencia_laboral_procedencia"),
                Arguments.of("terminada sin fin", "ACME", "Dev", "ENDED", "MANUAL", "2024-05-01", null, "ck_experiencia_laboral_fin_segun_estado"),
                Arguments.of("en curso con fin", "ACME", "Dev", "CURRENT", "MANUAL", "2024-05-01", "2024-06-01", "ck_experiencia_laboral_fin_segun_estado"),
                Arguments.of("fin antes del inicio", "ACME", "Dev", "ENDED", "MANUAL", "2024-05-01", "2024-04-01", "ck_experiencia_laboral_fechas"),
                Arguments.of("inicio a mitad de mes", "ACME", "Dev", "CURRENT", "MANUAL", "2024-05-15", null, "ck_experiencia_laboral_primer_dia"));
    }

    @Test
    @DisplayName("Una experiencia sin inicio ni fin con fin desconocido se acepta")
    void constraint_shouldAccept_whenUnknownEndHasNoDates() throws Exception {
        migrateFresh();
        try (var connection = connection()) {
            insertProfile(connection);

            insertExperience(connection, "ACME", "Dev", "UNKNOWN_END", "MANUAL", null, null);
        }
    }

    @Test
    @DisplayName("La clave primaria, la clave foránea y el índice tienen nombres estándar")
    void constraints_shouldHaveStandardNames_whenSchemaIsMigrated() throws Exception {
        migrateFresh();
        try (var connection = connection(); var statement = connection.createStatement()) {
            var names = new java.util.ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT conname FROM pg_constraint WHERE conrelid = 'experiencia_laboral'::regclass")) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
            var indexes = new java.util.ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT indexname FROM pg_indexes WHERE tablename = 'experiencia_laboral'")) {
                while (rows.next()) {
                    indexes.add(rows.getString(1));
                }
            }

            assertThat(names).contains("pk_experiencia_laboral", "fk_experiencia_laboral_perfil");
            assertThat(indexes).contains("ix_experiencia_laboral_perfil_id");
        }
    }
}
