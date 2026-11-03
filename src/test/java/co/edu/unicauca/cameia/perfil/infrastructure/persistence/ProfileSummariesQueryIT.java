package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileSummary;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalProfileRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lectura de los perfiles de un Usuario con sus roles objetivo, contra PostgreSQL real.
 *
 * <p>Los datos se insertan con SQL porque el plan Free solo deja crear un perfil por la API y estas
 * pruebas necesitan varios, con instantes de creación elegidos.</p>
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
class ProfileSummariesQueryIT {

    /** PostgreSQL real, con la misma imagen que el servicio de base de datos de docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String OWNER_A = "uid-cm201-a";
    private static final String OWNER_B = "uid-cm201-b";
    private static final String INSERT_PROFILE = """
            INSERT INTO perfil_profesional (id, firebase_uid, nombre, estado, creado_en, actualizado_en)
            VALUES (?::uuid, ?, ?, ?, ?::timestamptz, ?::timestamptz)
            """;
    private static final String INSERT_ROLE = """
            INSERT INTO rol_objetivo (id, perfil_id, nombre_rol, rol_profesional_id, creado_en)
            VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?::timestamptz)
            """;

    @Autowired
    ProfessionalProfileRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void cleanAndPrepare() {
        deleteTestProfiles();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void deleteTestProfiles() {
        jdbc.update("DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-cm201-%'");
    }

    private static String id(int n) {
        return String.format("c2010000-0000-4000-8000-%012d", n);
    }

    private void profile(int n, String owner, String name, String status, String createdAt) {
        jdbc.update(INSERT_PROFILE, id(n), owner, name, status, createdAt, createdAt);
    }

    private void role(int n, int profile, String title, String catalogRoleId, String createdAt) {
        jdbc.update(INSERT_ROLE, id(n), id(profile), title, catalogRoleId, createdAt);
    }

    private static String catalogRole(int n) {
        return "a0000002-0000-0000-0000-00000000000" + n;
    }

    private List<ProfileSummary> list(String owner, ProfileStatus status) {
        return repository.findSummariesByOwner(new FirebaseUid(owner), status);
    }

    @Test
    @DisplayName("Los perfiles salen del primero creado al último y, a igual instante, por id")
    void findSummaries_shouldOrderByCreationThenId() {
        profile(11, OWNER_A, "P1", "IN_PROGRESS", "2026-10-01T10:00:00Z");
        profile(12, OWNER_A, "P2", "IN_PROGRESS", "2026-10-02T10:00:00Z");
        profile(13, OWNER_A, "P3", "IN_PROGRESS", "2026-09-30T10:00:00Z");
        profile(2, OWNER_A, "Empate-2", "IN_PROGRESS", "2026-10-03T10:00:00Z");
        profile(1, OWNER_A, "Empate-1", "IN_PROGRESS", "2026-10-03T10:00:00Z");

        assertThat(list(OWNER_A, null)).extracting(ProfileSummary::name)
                .containsExactly("P3", "P1", "P2", "Empate-1", "Empate-2");
    }

    @Test
    @DisplayName("Los roles de cada perfil salen en el orden en que se agregaron, con su id de catálogo y su nombre")
    void findSummaries_shouldOrderTargetRolesByCreation() {
        profile(1, OWNER_A, "P1", "COMPLETED", "2026-10-01T10:00:00Z");
        role(1, 1, "Tercero", catalogRole(3), "2026-10-01T12:00:00Z");
        role(2, 1, "Primero", catalogRole(1), "2026-10-01T10:00:00Z");
        role(3, 1, "Segundo", catalogRole(2), "2026-10-01T11:00:00Z");

        var roles = list(OWNER_A, null).getFirst().targetRoles();

        assertThat(roles).extracting(ProfileSummary.TargetRoleSummary::roleTitle)
                .containsExactly("Primero", "Segundo", "Tercero");
        assertThat(roles).extracting(ProfileSummary.TargetRoleSummary::professionalRoleId)
                .containsExactly(UUID.fromString(catalogRole(1)), UUID.fromString(catalogRole(2)),
                        UUID.fromString(catalogRole(3)));
    }

    @Test
    @DisplayName("Cada Usuario ve solo sus perfiles")
    void findSummaries_shouldReturnOnlyOwnerProfiles() {
        profile(1, OWNER_A, "A1", "IN_PROGRESS", "2026-10-01T10:00:00Z");
        profile(2, OWNER_A, "A2", "COMPLETED", "2026-10-02T10:00:00Z");
        profile(3, OWNER_B, "B1", "IN_PROGRESS", "2026-10-01T10:00:00Z");

        assertThat(list(OWNER_A, null)).extracting(ProfileSummary::name).containsExactly("A1", "A2");
        assertThat(list(OWNER_B, null)).extracting(ProfileSummary::name).containsExactly("B1");
    }

    @Test
    @DisplayName("Con estado solo salen los perfiles de ese estado; sin estado, todos")
    void findSummaries_shouldFilterByStatus_whenGiven() {
        profile(1, OWNER_A, "Listo", "COMPLETED", "2026-10-01T10:00:00Z");
        profile(2, OWNER_A, "Borrador", "IN_PROGRESS", "2026-10-02T10:00:00Z");

        assertThat(list(OWNER_A, ProfileStatus.COMPLETED)).extracting(ProfileSummary::name).containsExactly("Listo");
        assertThat(list(OWNER_A, ProfileStatus.IN_REVIEW)).isEmpty();
        assertThat(list(OWNER_A, null)).hasSize(2);
    }

    @Test
    @DisplayName("Un Usuario sin perfiles recibe la lista vacía")
    void findSummaries_shouldReturnEmpty_whenNoProfiles() {
        assertThat(list("uid-cm201-vacio", null)).isEmpty();
    }

    @Test
    @DisplayName("Un perfil sin nombre ni roles trae nombre nulo y roles vacíos")
    void findSummaries_shouldReturnNullName_andEmptyRoles() {
        profile(1, OWNER_A, null, "IN_PROGRESS", "2026-10-01T10:00:00Z");

        var summary = list(OWNER_A, null).getFirst();

        assertThat(summary.name()).isNull();
        assertThat(summary.targetRoles()).isEmpty();
        assertThat(summary.status()).isEqualTo(ProfileStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Un nombre con tildes, emoji o marcas HTML se conserva tal cual")
    void findSummaries_shouldKeepName_whenUnicodeOrMarkup() {
        profile(1, OWNER_A, "Análisis 😀", "IN_PROGRESS", "2026-10-01T10:00:00Z");
        profile(2, OWNER_A, "<script>alert(1)</script>", "IN_PROGRESS", "2026-10-02T10:00:00Z");

        assertThat(list(OWNER_A, null)).extracting(ProfileSummary::name)
                .containsExactly("Análisis 😀", "<script>alert(1)</script>");
    }

    @Test
    @DisplayName("Cinco perfiles con cinco roles se leen con una sola consulta y sin cargas perezosas")
    void findSummaries_shouldUseSingleQuery() {
        for (int p = 1; p <= 5; p++) {
            profile(p, OWNER_A, "P" + p, "IN_PROGRESS", "2026-10-0" + p + "T10:00:00Z");
            for (int r = 1; r <= 5; r++) {
                role(p * 10 + r, p, "Rol " + r, catalogRole(r), "2026-10-0" + p + "T1" + r + ":00:00Z");
            }
        }
        statistics.clear();

        var result = list(OWNER_A, null);

        assertThat(result).hasSize(5);
        assertThat(result).allSatisfy(s -> assertThat(s.targetRoles()).hasSize(5));
        assertThat(statistics.getCollectionFetchCount()).isZero();
        assertThat(statistics.getEntityFetchCount()).isZero();
        long listQueryExecutions = Arrays.stream(statistics.getQueries())
                .filter(q -> q.contains("left join fetch"))
                .mapToLong(q -> statistics.getQueryStatistics(q).getExecutionCount())
                .sum();
        assertThat(listQueryExecutions).isEqualTo(1);
    }

    @Test
    @DisplayName("Leer la lista no inserta, actualiza ni borra nada")
    void findSummaries_shouldNotWrite() {
        profile(1, OWNER_A, "P1", "COMPLETED", "2026-10-01T10:00:00Z");
        role(1, 1, "Rol", catalogRole(1), "2026-10-01T10:00:00Z");
        statistics.clear();

        list(OWNER_A, null);
        list(OWNER_A, ProfileStatus.COMPLETED);

        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
    }
}
