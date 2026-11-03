package co.edu.unicauca.cameia.perfil.presentation.advice;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BadParameterEncodingTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    @Test
    @DisplayName("Reconoce la excepción de Tomcat")
    void matches_shouldBeTrue_whenTomcatException() {
        assertThat(BadParameterEncoding.matches(new InvalidParameterException("x"))).isTrue();
    }

    @Test
    @DisplayName("La reconoce aunque venga envuelta en otras excepciones")
    void matches_shouldFindCause_whenWrapped() {
        var wrapped = new RuntimeException("a", new RuntimeException("b", new InvalidParameterException("x")));

        assertThat(BadParameterEncoding.matches(wrapped)).isTrue();
    }

    @Test
    @DisplayName("Otra IllegalStateException no es un parámetro mal codificado")
    void matches_shouldBeFalse_whenOtherIllegalState() {
        assertThat(BadParameterEncoding.matches(new IllegalStateException("x"))).isFalse();
    }

    @Test
    @DisplayName("Sin excepción no hay coincidencia")
    void matches_shouldBeFalse_whenNull() {
        assertThat(BadParameterEncoding.matches(null)).isFalse();
    }

    @Test
    @DisplayName("Una cadena de causas circular termina sin coincidir")
    void matches_shouldStop_whenCauseChainIsCircular() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);

        assertThat(BadParameterEncoding.matches(a)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"POST," + FORM, "PUT," + FORM, "PATCH," + FORM, "DELETE," + FORM, "patch," + FORM,
            "POST,Application/X-WWW-Form-Urlencoded; charset=UTF-8"})
    @DisplayName("Un método con cuerpo y un formulario viene del cuerpo")
    void fromFormBody_shouldBeTrue(String method, String contentType) {
        assertThat(BadParameterEncoding.fromFormBody(method, contentType)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(value = {"GET," + FORM, "POST,application/json", "POST,NIL", "NIL," + FORM}, nullValues = "NIL")
    @DisplayName("Un GET, otro tipo, o la falta de método o tipo no vienen del cuerpo de un formulario")
    void fromFormBody_shouldBeFalse(String method, String contentType) {
        assertThat(BadParameterEncoding.fromFormBody(method, contentType)).isFalse();
    }
}
