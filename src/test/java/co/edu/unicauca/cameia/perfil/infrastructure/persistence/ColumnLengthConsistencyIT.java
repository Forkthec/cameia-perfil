package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.Education;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRoleNames;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalSummary;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.SkillName;
import co.edu.unicauca.cameia.perfil.domain.model.WorkExperience;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalRoleEntity;
import jakarta.persistence.Column;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("Los largos de nombre y resumen del perfil en la base son los del dominio")
    void profileColumnLengths_shouldMatchDomainLimits_whenSchemaIsMigrated() throws Exception {
        var expected = Map.of(
                "nombre", ProfileName.MAX_LENGTH,
                "resumen", ProfessionalSummary.MAX_LENGTH);

        assertColumnLengths("perfil_profesional", expected);
    }

    @Test
    @DisplayName("El largo declarado en la entidad del perfil para nombre y resumen es el del dominio")
    void profileEntityColumnLengths_shouldMatchDomainLimits_whenAnnotationsAreRead() throws Exception {
        assertThat(columnLength("name")).isEqualTo(ProfileName.MAX_LENGTH);
        assertThat(columnLength("summary")).isEqualTo(ProfessionalSummary.MAX_LENGTH);
    }

    @Test
    @DisplayName("Los largos de los nombres del rol en la base son el límite del dominio")
    void roleColumnLengths_shouldMatchDomainLimit_whenSchemaIsMigrated() throws Exception {
        var expected = Map.of(
                "nombre", ProfessionalRoleNames.MAX_NAME_LENGTH,
                "nombre_en", ProfessionalRoleNames.MAX_NAME_LENGTH);

        assertColumnLengths("rol_profesional", expected);
    }

    @Test
    @DisplayName("El largo declarado en la entidad del rol para sus nombres es el del dominio")
    void roleEntityColumnLengths_shouldMatchDomainLimit_whenAnnotationsAreRead() throws Exception {
        for (var attribute : new String[] {"nombre", "nombreEn"}) {
            var length = ProfessionalRoleEntity.class.getDeclaredField(attribute).getAnnotation(Column.class).length();
            assertThat(length).as(attribute).isEqualTo(ProfessionalRoleNames.MAX_NAME_LENGTH);
        }
    }

    private static int columnLength(String attribute) throws NoSuchFieldException {
        return ProfessionalProfileEntity.class.getDeclaredField(attribute).getAnnotation(Column.class).length();
    }

    @Test
    @DisplayName("Los largos del nombre de la habilidad y de su clave de comparación son el límite del dominio")
    void skillColumnLengths_shouldMatchDomainLimit_whenSchemaIsMigrated() throws Exception {
        var expected = Map.of(
                "nombre_habilidad", SkillName.MAX_LENGTH,
                "nombre_normalizado", SkillName.MAX_LENGTH);

        assertColumnLengths("habilidad_perfil", expected);
    }

    @Test
    @DisplayName("La base acepta un resumen de 2000 caracteres y rechaza uno de 2001")
    void summaryColumn_shouldReject2001Characters_whenWritten() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        var insert = "INSERT INTO perfil_profesional (id, firebase_uid, resumen, creado_en, actualizado_en) "
                + "VALUES (gen_random_uuid(), ?, repeat('a', ?), now(), now())";

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement(insert)) {
            statement.setString(1, "uid-col-2000");
            statement.setInt(2, ProfessionalSummary.MAX_LENGTH);
            assertThat(statement.executeUpdate()).isEqualTo(1);

            statement.setString(1, "uid-col-2001");
            statement.setInt(2, ProfessionalSummary.MAX_LENGTH + 1);
            assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo("22001"));
        }
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
