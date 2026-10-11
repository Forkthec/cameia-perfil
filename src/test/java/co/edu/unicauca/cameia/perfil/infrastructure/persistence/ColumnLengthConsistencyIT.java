package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El largo de cada columna de texto es el mismo que el límite del dominio: lo que el dominio acepta siempre cabe en
 * la columna y lo que no cabe nunca llega a la base.
 */
@Testcontainers
class ColumnLengthConsistencyIT {

    /** PostgreSQL real con todas las migraciones aplicadas. */
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Los largos de empresa, cargo y descripción de la base son los del dominio")
    void columnLengths_shouldMatchDomainLimits_whenSchemaIsMigrated() throws Exception {
        var expected = Map.of(
                "empresa", WorkExperience.MAX_COMPANY_LENGTH,
                "cargo", WorkExperience.MAX_POSITION_LENGTH,
                "descripcion", WorkExperience.MAX_DESCRIPTION_LENGTH);

        assertColumnLengths("experiencia_laboral", expected);
    }

    @Test
    @DisplayName("Los largos de institución y título de la base son los del dominio")
    void educationColumnLengths_shouldMatchDomainLimits_whenSchemaIsMigrated() throws Exception {
        var expected = Map.of(
                "institucion", Education.MAX_INSTITUTION_LENGTH,
                "titulo", Education.MAX_DEGREE_LENGTH);

        assertColumnLengths("educacion", expected);
    }

    /** Migra la base y compara el largo de cada columna con el límite del dominio. */
    private static void assertColumnLengths(String table, Map<String, Integer> expected) throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            for (var column : expected.entrySet()) {
                try (var rows = statement.executeQuery("SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = '" + table + "' AND column_name = '" + column.getKey() + "'")) {
                    assertThat(rows.next()).as(column.getKey()).isTrue();
                    assertThat(rows.getInt(1)).as(column.getKey()).isEqualTo(column.getValue());
                }
            }
        }
    }
}
