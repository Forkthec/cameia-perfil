package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restricciones de las tablas de la réplica de la fecha de nacimiento y del Inbox contra PostgreSQL real.
 *
 * <p>Cada restricción se viola a propósito y se comprueba el estado SQL o el nombre de la restricción, porque el esquema repite
 * lo que valida el código y una violación nunca debe terminar en un error genérico.</p>
 */
@SpringBootTest
@Testcontainers
class BirthDateReplicaSchemaIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String INSERT_REPLICA =
            "INSERT INTO fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) VALUES (?, ?::date)";
    private static final String INSERT_INBOX =
            "INSERT INTO evento_procesado (message_id, tipo, procesado_en) VALUES (?::uuid, ?, now())";
    private static final String MESSAGE_ID = "11111111-1111-4111-8111-111111111111";

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM fecha_nacimiento_usuario");
        jdbc.update("DELETE FROM evento_procesado");
    }

    @Test
    @DisplayName("Una fila válida de la réplica se inserta")
    void insert_shouldSucceed_whenRowIsValid() {
        int rows = jdbc.update(INSERT_REPLICA, "uid-1", "2008-03-15");

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Un mismo Usuario no puede tener dos filas")
    void insert_shouldFail_whenUserRepeats() {
        jdbc.update(INSERT_REPLICA, "uid-1", "2008-03-15");

        assertThatThrownBy(() -> jdbc.update(INSERT_REPLICA, "uid-1", "2000-01-01"))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"))
                .hasMessageContaining("pk_fecha_nacimiento_usuario");
    }

    @Test
    @DisplayName("La identidad en blanco se rechaza")
    void insert_shouldFail_whenUserIsBlank() {
        assertThatThrownBy(() -> jdbc.update(INSERT_REPLICA, "   ", "2008-03-15"))
                .hasMessageContaining("ck_fecha_nacimiento_usuario_firebase_uid");
    }

    @Test
    @DisplayName("La fecha de nacimiento nula se rechaza")
    void insert_shouldFail_whenBirthDateIsNull() {
        assertThatThrownBy(() -> jdbc.update(INSERT_REPLICA, "uid-1", null))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23502"));
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres no cabe en la columna")
    void insert_shouldFail_whenUserHas129Characters() {
        assertThatThrownBy(() -> jdbc.update(INSERT_REPLICA, "a".repeat(129), "2008-03-15"))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("22001"));
    }

    @Test
    @DisplayName("La tabla guarda solo la identidad y la fecha de nacimiento")
    void table_shouldHaveOnlyIdentityAndBirthDate() {
        assertThat(columnsOf("fecha_nacimiento_usuario")).containsExactlyInAnyOrder("firebase_uid", "fecha_nacimiento");
    }

    @Test
    @DisplayName("El largo de la columna de identidad es el del límite validado")
    void columnLength_shouldMatchFirebaseUidLimit() {
        Integer length = jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'fecha_nacimiento_usuario' AND column_name = 'firebase_uid'
                """, Integer.class);

        assertThat(length).isEqualTo(FirebaseUid.MAX_LENGTH);
    }

    @Test
    @DisplayName("La clave primaria es el único índice de la réplica")
    void primaryKey_shouldBeTheOnlyIndex() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'fecha_nacimiento_usuario'", String.class);

        assertThat(indexes).containsExactly("pk_fecha_nacimiento_usuario");
    }

    @Test
    @DisplayName("Un mensaje no puede registrarse dos veces en el Inbox")
    void inbox_shouldFail_whenMessageIdRepeats() {
        jdbc.update(INSERT_INBOX, MESSAGE_ID, "cuenta.creada");

        assertThatThrownBy(() -> jdbc.update(INSERT_INBOX, MESSAGE_ID, "cuenta.creada"))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"))
                .hasMessageContaining("pk_evento_procesado");
    }

    @Test
    @DisplayName("El Inbox solo admite los tipos de evento de cuenta")
    void inbox_shouldFail_whenTypeIsNotAnAccountEvent() {
        assertThatThrownBy(() -> jdbc.update(INSERT_INBOX, MESSAGE_ID, "perfil.actualizado"))
                .hasMessageContaining("ck_evento_procesado_tipo");
    }

    @Test
    @DisplayName("El instante de procesamiento nulo se rechaza")
    void inbox_shouldFail_whenProcessedAtIsNull() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento_procesado (message_id, tipo, procesado_en) VALUES (?::uuid, ?, NULL)",
                MESSAGE_ID, "cuenta.creada"))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23502"));
    }

    @Test
    @DisplayName("El Inbox no guarda datos personales")
    void inbox_shouldHaveNoPersonalDataColumns() {
        assertThat(columnsOf("evento_procesado")).containsExactlyInAnyOrder("message_id", "tipo", "procesado_en");
    }

    @Test
    @DisplayName("La migración conserva los perfiles que ya existen")
    void migration_shouldKeepProfiles_whenProfilesExist() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables WHERE table_name = 'perfil_profesional'
                """, Integer.class);

        assertThat(tables).isEqualTo(1);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class, table);
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
