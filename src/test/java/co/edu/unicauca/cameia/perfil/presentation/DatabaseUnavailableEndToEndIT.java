package co.edu.unicauca.cameia.perfil.presentation;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Cada forma de indisponibilidad de la base de datos (pool agotado, conexiones cortadas por el servidor, base que no
 * responde) enviada por un socket real al servicio completo, con PostgreSQL real.
 *
 * <p>El pool se reduce a dos conexiones y una espera de 1,5 s para que el agotamiento sea rápido y determinista. La
 * base que no responde se simula pausando el contenedor ({@code docker pause}), que conserva el puerto y deja las
 * conexiones colgadas, a diferencia de detenerlo.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1500"})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseUnavailableEndToEndIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROBLEM_JSON = "application/problem+json;charset=UTF-8";
    private static final String JSON = "Content-Type: application/json";
    private static final String PROFILES = "/api/v1/profiles";
    private static final String UID_A = "uid-cm290-a";
    private static final String UID_B = "uid-cm290-b";
    private static final String ANY_PROFILE = "11111111-1111-1111-1111-111111111111";
    private static final long MAX_MILLIS = 3_000;
    private static final int POOL_SIZE = 2;
    /**
     * Primera línea de un registro ERROR o WARN emitido por un hilo que atiende peticiones; las líneas de una traza no la
     * cumplen y los hilos de fondo del pool (housekeeper) quedan fuera, porque no pertenecen a la petición.
     */
    private static final Pattern ERROR_OR_WARN_LINE =
            Pattern.compile("\\b(ERROR|WARN)\\s+\\d+ --- \\[[^\\]]*\\] \\[[^\\]]*exec-\\d+\\]");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    /** Espía del puerto de persistencia: permite cortar la base en mitad de una transacción. */
    @MockitoSpyBean
    ProfessionalProfileRepository profileRepository;

    @BeforeEach
    void awaitHealthyPool() throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                return;
            } catch (RuntimeException notYet) {
                if (System.nanoTime() > deadline) {
                    throw notYet;
                }
                Thread.sleep(300);
            }
        }
    }

    @AfterEach
    void resumeDatabase() {
        var paused = Boolean.TRUE.equals(postgres.getDockerClient()
                .inspectContainerCmd(postgres.getContainerId()).exec().getState().getPaused());
        if (paused) {
            postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        }
    }

    private RawHttpClient raw() {
        return new RawHttpClient(port);
    }

    private RawHttpClient.Reply listProfiles(String uid) throws IOException {
        return raw().send("GET", PROFILES, List.of("X-User-Id: " + uid), null);
    }

    /** Toma todas las conexiones del pool: la siguiente petición espera hasta {@code connection-timeout}. */
    private List<Connection> takeAllConnections() throws SQLException {
        var held = new ArrayList<Connection>();
        for (var i = 0; i < POOL_SIZE; i++) {
            held.add(dataSource.getConnection());
        }
        return held;
    }

    private static void release(List<Connection> held) throws SQLException {
        for (var connection : held) {
            connection.close();
        }
    }

    /** Pausa el contenedor y espera a que las conexiones del pool dejen de parecer recién usadas. */
    private static void pauseDatabase() throws InterruptedException {
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        // Hikari no comprueba una conexión usada hace menos de 500 ms: sin esta espera la consulta se enviaría a
        // una conexión colgada y no vencería el tiempo de espera del pool.
        Thread.sleep(800);
    }

    /** Corta, con una conexión aparte, todas las conexiones que el pool tiene abiertas. */
    private static void terminateBackends() throws SQLException {
        try (var admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = admin.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND pid <> pg_backend_pid()");
        }
    }

    private void assertDatabaseUnavailable(RawHttpClient.Reply reply) {
        assertThat(reply.status()).isEqualTo(503);
        var json = mapper.readTree(reply.body());
        assertThat(json.get("code").asString()).isEqualTo("DATABASE_UNAVAILABLE");
        assertThat(json.get("requestId").asString()).isNotBlank();
        assertThat(reply.headers().get("content-type")).isEqualToIgnoringCase(PROBLEM_JSON);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    @DisplayName("Con el pool agotado, GET /profiles responde 503 DATABASE_UNAVAILABLE en menos de 3 s")
    void listProfiles_shouldReturn503_whenPoolIsExhausted() throws Exception {
        var held = takeAllConnections();
        try {
            var start = System.nanoTime();
            var reply = listProfiles(UID_A);
            var millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertDatabaseUnavailable(reply);
            assertThat(millis).isLessThan(MAX_MILLIS);
        } finally {
            release(held);
        }
    }

    @Test
    @DisplayName("Con las conexiones cortadas por el servidor, GET /profiles responde 503")
    void listProfiles_shouldReturn503_whenServerTerminatesConnections() throws Exception {
        // Se usan las dos conexiones justo antes del corte: Hikari no revalida las usadas hace menos de 500 ms.
        var held = takeAllConnections();
        for (var connection : held) {
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
        }
        release(held);
        terminateBackends();

        assertDatabaseUnavailable(listProfiles(UID_A));
    }

    @Test
    @DisplayName("Con la base en pausa responde 503 y, al reanudarla, vuelve a responder 200 con la lista vacía")
    void listProfiles_shouldRecover_whenDatabaseComesBack() throws Exception {
        pauseDatabase();
        var down = listProfiles(UID_A);
        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();

        assertDatabaseUnavailable(down);
        var back = awaitNot503(() -> listProfiles(UID_A));
        assertThat(back.status()).isEqualTo(200);
        assertThat(mapper.readTree(back.body()).get("profiles")).isEmpty();
    }

    static List<Arguments> databaseRoutes() {
        return List.of(
                Arguments.of("GET", PROFILES, List.of("X-User-Id: " + UID_B), null),
                Arguments.of("POST", PROFILES, List.of("X-User-Id: " + UID_B), ""),
                Arguments.of("GET", PROFILES + "/" + ANY_PROFILE, List.of("X-User-Id: " + UID_B), null),
                Arguments.of("PATCH", PROFILES + "/" + ANY_PROFILE, List.of("X-User-Id: " + UID_B, JSON),
                        "{\"headline\":\"x\"}"),
                Arguments.of("GET", PROFILES + "/professional-roles", List.of(), null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("databaseRoutes")
    @DisplayName("Con la base caída, las rutas que la usan responden 503 DATABASE_UNAVAILABLE")
    void allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown(String method, String path, List<String> headers,
            String body) throws Exception {
        pauseDatabase();

        assertDatabaseUnavailable(raw().send(method, path, headers, body));
    }

    @Test
    @DisplayName("Veinte peticiones iguales con el pool agotado responden lo mismo y no escriben nada")
    void listProfiles_shouldReturnSameAnswer_whenRepeated20Times() throws Exception {
        var profilesBefore = count("perfil_profesional");
        var eventsBefore = count("evento_saliente");
        var held = takeAllConnections();
        try {
            for (var i = 0; i < 20; i++) {
                assertDatabaseUnavailable(listProfiles(UID_A));
            }
        } finally {
            release(held);
        }

        assertThat(count("perfil_profesional")).isEqualTo(profilesBefore);
        assertThat(count("evento_saliente")).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("Treinta peticiones en paralelo con el pool agotado responden 503 sin pasar de 3 s")
    void listProfiles_shouldNotFail_whenManyRequestsRunInParallel() throws Exception {
        var held = takeAllConnections();
        var threads = Executors.newFixedThreadPool(30);
        try {
            List<Future<long[]>> pending = IntStream.range(0, 30)
                    .mapToObj(i -> threads.submit(() -> timedStatus("uid-cm290-p" + i)))
                    .toList();
            for (var future : pending) {
                var result = future.get(20, TimeUnit.SECONDS);
                assertThat(result[0]).isEqualTo(503);
                assertThat(result[1]).isLessThan(MAX_MILLIS);
            }
        } finally {
            threads.shutdownNow();
            release(held);
        }
    }

    @Test
    @DisplayName("Una creación con las conexiones cortadas antes de confirmar responde 503 y no deja perfil ni evento")
    void createProfile_shouldRollBack_whenConnectionsAreTerminatedBeforeCommit() throws Exception {
        var profilesBefore = count("perfil_profesional");
        var eventsBefore = count("evento_saliente");
        doAnswer(invocation -> {
            terminateBackends();
            return invocation.callRealMethod();
        }).when(profileRepository).save(any(ProfessionalProfile.class));

        var reply = raw().send("POST", PROFILES, List.of("X-User-Id: uid-cm290-rollback"), "");

        assertDatabaseUnavailable(reply);
        awaitHealthyPool();
        assertThat(count("perfil_profesional")).isEqualTo(profilesBefore);
        assertThat(count("evento_saliente")).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("Con el pool agotado, la respuesta no repite el mensaje de la librería y el registro es una sola línea")
    void databaseUnavailable_shouldNotEchoDatabaseMessage(CapturedOutput output) throws Exception {
        var held = takeAllConnections();
        var mark = output.getAll().length();
        RawHttpClient.Reply reply;
        try {
            reply = listProfiles(UID_A);
        } finally {
            release(held);
        }

        assertDatabaseUnavailable(reply);
        assertThat(reply.body()).doesNotContain("HikariPool").doesNotContain("jdbc")
                .doesNotContain("postgres").doesNotContain("org.");
        var requestId = mapper.readTree(reply.body()).get("requestId").asString();
        assertOnlyHandlerLoggedTheRequest(output, mark, requestId);
    }

    @Test
    @DisplayName("Con las conexiones cortadas por el servidor, ninguna librería registra el fallo: solo el manejador")
    void databaseUnavailable_shouldLogOnlyHandlerLine_whenServerTerminatesConnections(CapturedOutput output)
            throws Exception {
        var held = takeAllConnections();
        for (var connection : held) {
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
        }
        release(held);
        terminateBackends();
        var mark = output.getAll().length();

        var reply = listProfiles(UID_A);

        assertDatabaseUnavailable(reply);
        assertOnlyHandlerLoggedTheRequest(output, mark, mapper.readTree(reply.body()).get("requestId").asString());
    }

    /**
     * Exige que, desde {@code mark}, la salida tenga exactamente una línea ERROR o WARN y que sea la del manejador.
     * No filtra por requestId: los avisos de Hibernate y el error de la transacción de Spring no lo traen, y citan
     * el SQL y el mensaje de PostgreSQL, que puede incluir valores de columnas. Solo exceptúa los avisos del pool.
     */
    private static void assertOnlyHandlerLoggedTheRequest(CapturedOutput output, int mark, String requestId) {
        var levelLines = output.getAll().substring(mark).lines()
                .filter(line -> ERROR_OR_WARN_LINE.matcher(line).find())
                // Los avisos del pool son texto fijo sin datos y señalan conexiones caducadas: se conservan.
                .filter(line -> !line.contains("com.zaxxer.hikari")).toList();

        assertThat(levelLines).singleElement().satisfies(line -> assertThat(line)
                .contains("ApiExceptionHandler")
                .contains("code=DATABASE_UNAVAILABLE requestId=" + requestId)
                .doesNotContain("request timed out").doesNotContain("HikariPool"));
    }

    /** @return estado HTTP y milisegundos que tardó la respuesta */
    private long[] timedStatus(String uid) throws IOException {
        var start = System.nanoTime();
        var reply = listProfiles(uid);
        return new long[] {reply.status(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)};
    }

    /** Repite la petición hasta que deje de responder 503 (Hikari tarda un instante en reponer el pool). */
    private static RawHttpClient.Reply awaitNot503(ThrowingSupplier call) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        var reply = call.get();
        while (reply.status() == 503 && System.nanoTime() < deadline) {
            Thread.sleep(300);
            reply = call.get();
        }
        return reply;
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        RawHttpClient.Reply get() throws Exception;
    }
}
