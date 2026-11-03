package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.ReplicateBirthDateCommand;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La transacción del consumo: si la réplica falla, el Inbox no queda anotado y el reintento podrá procesar el mensaje. */
@SpringBootTest
@Testcontainers
class BirthDateReplicaAppServiceIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    BirthDateReplicaAppService service;
    @MockitoSpyBean
    BirthDateReplica replica;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        Mockito.reset(replica);
        jdbc.update("DELETE FROM fecha_nacimiento_usuario");
        jdbc.update("DELETE FROM evento_procesado");
    }

    @Test
    @DisplayName("Si la réplica falla, el mensaje no queda anotado en el Inbox")
    void recordAccountCreated_shouldRollBackInbox_whenReplicaFails() {
        Mockito.doThrow(new DataAccessResourceFailureException("base caída"))
                .when(replica).saveIfAbsent(Mockito.any(FirebaseUid.class), Mockito.any(LocalDate.class));
        var command = new ReplicateBirthDateCommand("11111111-1111-4111-8111-111111111111",
                "6f1d2c3b4a5e4f60718293a4b5c6d7e8", LocalDate.of(2008, 3, 15));

        assertThatThrownBy(() -> service.recordAccountCreated(command))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_procesado", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fecha_nacimiento_usuario", Integer.class)).isZero();
    }
}
