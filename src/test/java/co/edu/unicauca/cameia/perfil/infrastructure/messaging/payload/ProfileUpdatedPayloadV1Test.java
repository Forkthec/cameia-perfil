package co.edu.unicauca.cameia.perfil.infrastructure.messaging.payload;

import co.edu.unicauca.cameia.perfil.domain.event.CorrelationId;
import co.edu.unicauca.cameia.perfil.domain.event.ProfileUpdated;
import co.edu.unicauca.cameia.perfil.domain.model.DataProvenance;
import co.edu.unicauca.cameia.perfil.domain.model.EducationLevel;
import co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.SkillLevel;
import co.edu.unicauca.cameia.perfil.domain.model.TargetRole;
import co.edu.unicauca.cameia.perfil.support.ActiveProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** La carga v1 copia el perfil activo tal como está guardado, con los opcionales presentes como {@code null}. */
class ProfileUpdatedPayloadV1Test {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant AT = Instant.parse("2026-10-15T12:00:00.123Z");

    private static ProfileUpdatedPayloadV1 payloadOf(ProfessionalProfile profile, Instant at) {
        return ProfileUpdatedPayloadV1.from(new ProfileUpdated(UUID.randomUUID(), profile, at, new CorrelationId("req-1")));
    }

    private static JsonNode jsonOf(ProfessionalProfile profile) {
        return JSON.readTree(JSON.writeValueAsString(payloadOf(profile, AT)));
    }

    private static TargetRole role(String title) {
        return new TargetRole(UUID.randomUUID(), ActiveProfiles.BACKEND_ROLE, title, DataProvenance.MANUAL);
    }

    @Test
    @DisplayName("El dueño de la carga es el firebaseUid guardado en el perfil")
    void from_shouldTakeOwnerFromProfile_whenBuilt() {
        var payload = payloadOf(ActiveProfiles.example(), AT);

        assertThat(payload.userId()).isEqualTo("6f1d2c3b4a5e4f60718293a4b5c6d7e8");
        assertThat(payload.professionalContext().id()).isEqualTo("7c9e6679-7425-40de-944b-e07fc1f90ae7");
        assertThat(payload.professionalContext().status()).isEqualTo("COMPLETED");
        assertThat(payload.targetRoles()).extracting(ProfileUpdatedPayloadV1.TargetRoleItem::code)
                .containsExactly("a0000001-0000-0000-0000-000000000002");
    }

    @Test
    @DisplayName("Los valores opcionales ausentes viajan presentes y en null")
    void from_shouldWriteNulls_whenOptionalValuesAreMissing() {
        var profile = ActiveProfiles.active(null, null, List.of(role("Desarrollador Backend")),
                List.of(ActiveProfiles.experience("ACME", "Analista", null, YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT)),
                List.of(ActiveProfiles.education("Universidad del Cauca", "Ingeniería", YearMonth.of(2017, 2), null)),
                List.of(ActiveProfiles.skill("Python", SkillLevel.BASIC)));

        var context = jsonOf(profile).get("contextoProfesional");

        assertThat(context.has("nombre")).isTrue();
        assertThat(context.get("nombre").isNull()).isTrue();
        assertThat(context.get("resumen").isNull()).isTrue();
        assertThat(context.get("fecha_archivado").isNull()).isTrue();
        assertThat(context.get("experiencias").get(0).get("descripcion").isNull()).isTrue();
        assertThat(context.get("experiencias").get(0).get("fin").isNull()).isTrue();
        assertThat(context.get("formaciones").get(0).get("inicio").asString()).isEqualTo("2017-02");
        assertThat(context.get("formaciones").get(0).get("fin").isNull()).isTrue();
    }

    @Test
    @DisplayName("Los textos en su límite y los hostiles viajan literales")
    void from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits() {
        var limits = ActiveProfiles.active("a".repeat(255), "😀".repeat(2000), List.of(role("r".repeat(255))),
                List.of(ActiveProfiles.experience("e".repeat(100), "c".repeat(100), "d".repeat(500), YearMonth.of(2021, 12),
                        YearMonth.of(2022, 1), EmploymentStatus.ENDED)),
                List.of(ActiveProfiles.education("i".repeat(150), "t".repeat(150), YearMonth.of(2017, 2), YearMonth.of(2021, 12))),
                List.of(ActiveProfiles.skill("a".repeat(60), SkillLevel.ADVANCED)));
        var combining = "e" + (char) 0x0301;
        var hostile = "<script>alert(1)</script> \"comillas\" ñandú 😀 " + combining;
        var hostileProfile = ActiveProfiles.active("Analista", hostile, List.of(), List.of(), List.of(), List.of());

        var context = jsonOf(limits).get("contextoProfesional");
        var summary = context.get("resumen").asString();
        var hostileContext = jsonOf(hostileProfile).get("contextoProfesional");

        assertThat(context.get("nombre").asString()).isEqualTo("a".repeat(255));
        assertThat(summary.codePointCount(0, summary.length())).isEqualTo(2000);
        assertThat(summary).isEqualTo("😀".repeat(2000));
        assertThat(context.get("experiencias").get(0).get("descripcion").asString()).isEqualTo("d".repeat(500));
        assertThat(context.get("experiencias").get(0).get("inicio").asString()).isEqualTo("2021-12");
        assertThat(context.get("habilidades").get(0).get("nombre").asString()).isEqualTo("a".repeat(60));
        assertThat(hostileContext.get("resumen").asString()).isEqualTo(hostile);
    }

    @Test
    @DisplayName("Las colecciones llenas viajan completas y en su orden")
    void from_shouldKeepEveryItemInOrder_whenCollectionsAreFull() {
        var experiences = IntStream.rangeClosed(1, 4).mapToObj(i -> ActiveProfiles.experience("Empresa " + i, "Cargo " + i, null,
                YearMonth.of(2020, i), null, EmploymentStatus.UNKNOWN_END)).toList();
        var educations = IntStream.rangeClosed(1, 5).mapToObj(i -> ActiveProfiles.education("Institución " + i, "Título " + i,
                YearMonth.of(2010 + i, 1), null)).toList();
        var skills = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> ActiveProfiles.skill(String.format("h%02d", i), SkillLevel.BASIC)).toList();
        var roles = IntStream.rangeClosed(1, 5).mapToObj(i -> role("Rol " + i)).toList();

        var json = jsonOf(ActiveProfiles.active("Analista", "Resumen", roles, experiences, educations, skills));
        var context = json.get("contextoProfesional");

        assertThat(context.get("experiencias")).hasSize(4);
        assertThat(context.get("formaciones")).hasSize(5);
        assertThat(context.get("habilidades")).hasSize(20);
        assertThat(json.get("rolesAspirados")).hasSize(5);
        assertThat(context.get("experiencias").get(3).get("empresa").asString()).isEqualTo("Empresa 4");
        assertThat(context.get("formaciones").get(0).get("institucion").asString()).isEqualTo("Institución 1");
        assertThat(context.get("habilidades").get(0).get("nombre").asString()).isEqualTo("h01");
        assertThat(context.get("habilidades").get(19).get("nombre").asString()).isEqualTo("h20");
        assertThat(json.get("rolesAspirados").get(4).get("nombre").asString()).isEqualTo("Rol 5");
    }

    @Test
    @DisplayName("El instante siempre lleva tres dígitos de milisegundos")
    void from_shouldFormatInstantWithMillis_whenMillisAreZero() {
        var payload = payloadOf(ActiveProfiles.example(), Instant.parse("2026-10-15T12:00:00Z"));

        assertThat(payload.updatedAt()).isEqualTo("2026-10-15T12:00:00.000Z");
    }

    @Test
    @DisplayName("Los enumerados viajan con el nombre del enumerado")
    void from_shouldWriteEnumNames_whenProfileHasLevels() {
        var context = jsonOf(ActiveProfiles.example()).get("contextoProfesional");

        assertThat(context.get("experiencias").get(0).get("estadoEmpleo").asString()).isEqualTo("CURRENT");
        assertThat(context.get("formaciones").get(0).get("nivel").asString()).isEqualTo(EducationLevel.UNDERGRADUATE.name());
        assertThat(context.get("formaciones").get(0).get("enCurso").asBoolean()).isFalse();
        assertThat(context.get("habilidades").get(0).get("nivel").asString()).isEqualTo("INTERMEDIATE");
    }
}
