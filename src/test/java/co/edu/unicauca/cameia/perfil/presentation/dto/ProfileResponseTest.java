package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.ProfileName;
import co.edu.unicauca.cameia.perfil.domain.model.SalaryExpectation;
import co.edu.unicauca.cameia.perfil.domain.model.WorkModality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapeo del perfil de dominio a la respuesta pública: lo opcional sale como {@code null} y lo presente, tal cual. */
class ProfileResponseTest {

    @Test
    @DisplayName("Un perfil recién creado devuelve null en nombre, resumen, expectativa salarial y modalidad, y listas vacías")
    void from_shouldReturnNulls_whenProfileIsEmpty() {
        var response = ProfileResponse.from(ProfessionalProfile.create(new FirebaseUid("uid-resp-vacio")));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.name()).isNull();
        assertThat(response.summary()).isNull();
        assertThat(response.salaryExpectation()).isNull();
        assertThat(response.preferredModality()).isNull();
        assertThat(response.workExperiences()).isEmpty();
        assertThat(response.educations()).isEmpty();
    }

    @Test
    @DisplayName("Un perfil con nombre, expectativa salarial y modalidad los devuelve con su valor")
    void from_shouldReturnValues_whenOptionalFieldsArePresent() {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-resp-lleno"));
        profile.updateName(new ProfileName("Ana Sofía"));
        profile.updateSalaryExpectation(new SalaryExpectation(new BigDecimal("4500000")));
        profile.updatePreferredModality(WorkModality.HYBRID);

        var response = ProfileResponse.from(profile);

        assertThat(response.name()).isEqualTo("Ana Sofía");
        assertThat(response.salaryExpectation()).isEqualByComparingTo("4500000");
        assertThat(response.preferredModality()).isEqualTo("HYBRID");
    }

    @Test
    @DisplayName("La expectativa salarial definida se muestra con su monto")
    void from_shouldMapSalary_whenExpectationIsSet() {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-response-salary"));
        profile.updateSalaryExpectation(new SalaryExpectation(new BigDecimal("1000000")));

        var response = ProfileResponse.from(profile);

        assertThat(response.salaryExpectation()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("Sin expectativa salarial el campo es nulo")
    void from_shouldReturnNullSalary_whenNoExpectation() {
        var profile = ProfessionalProfile.create(new FirebaseUid("uid-response-nosalary"));

        var response = ProfileResponse.from(profile);

        assertThat(response.salaryExpectation()).isNull();
    }
}
