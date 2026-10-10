package co.edu.unicauca.cameia.perfil.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigila que el catálogo de errores del contrato no quede atrás del código: todo valor de {@link ErrorCode}
 * está documentado en {@code docs/errores.md} y tiene al menos una prueba que lo nombra.
 */
class ErrorCodeDocumentationTest {

    private static final Path CATALOG = Path.of("docs", "errores.md");
    private static final Path TESTS = Path.of("src", "test", "java");

    @Test
    @DisplayName("Todo código del enumerado tiene una fila en docs/errores.md")
    void everyCode_shouldBeDocumented_whenCatalogIsRead() throws IOException {
        var rows = Files.readAllLines(CATALOG, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("| `"))
                .toList();

        var undocumented = Arrays.stream(ErrorCode.values())
                .map(Enum::name)
                .filter(name -> rows.stream().noneMatch(row -> row.startsWith("| `" + name + "`")))
                .toList();

        assertThat(undocumented).as("códigos sin fila en docs/errores.md").isEmpty();
    }

    @Test
    @DisplayName("Todo código del enumerado aparece en alguna prueba, además de la que enumera el enumerado")
    void everyCode_shouldBeTested_whenTestSourcesAreScanned() throws IOException {
        List<String> sources;
        try (Stream<Path> files = Files.walk(TESTS)) {
            sources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("ErrorCodeTest.java"))
                    .filter(path -> !path.getFileName().toString().equals("ErrorCodeDocumentationTest.java"))
                    .map(ErrorCodeDocumentationTest::read)
                    .toList();
        }

        var untested = Arrays.stream(ErrorCode.values())
                .map(Enum::name)
                .filter(name -> sources.stream().noneMatch(source -> source.contains(name)))
                .toList();

        assertThat(untested).as("códigos que ninguna prueba nombra").isEmpty();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + path, e);
        }
    }
}
