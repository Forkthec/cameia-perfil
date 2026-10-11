package co.edu.unicauca.cameia.perfil.presentation.dto;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalProfile;
import co.edu.unicauca.cameia.perfil.domain.model.SalaryExpectation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapeo del perfil de dominio a su representación HTTP: la expectativa salarial presente y ausente. */
class ProfileResponseTest {

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
