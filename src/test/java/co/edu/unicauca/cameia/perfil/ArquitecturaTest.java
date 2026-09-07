package co.edu.unicauca.cameia.perfil;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Verifica las reglas de dependencia entre capas de las secciones 3.4 y 3.5 de las reglas de
 * código backend.
 *
 * <p>Se instalan ahora, con el proyecto vacío, a propósito: es el momento barato. Cuando exista
 * código que las viole, la prueba ya está puesta y falla en el PR que introduce la violación, no
 * tres sprints después. Con los paquetes vacíos pasan trivialmente, y eso está bien —
 * {@code src/test/resources/archunit.properties} desactiva el fallo por regla sin clases que
 * evaluar.
 *
 * <p>La cita que justifica automatizarlas en vez de revisarlas a ojo está en la sección 3.4:
 * "es la única forma de que sobrevivan al sprint 3".
 *
 * <p>Nota para quien agregue reglas: ArchUnit las expone como <b>campos</b> anotados con
 * {@code @ArchTest}, y {@code @DisplayName} solo es válido sobre clases y métodos. El nombre
 * legible se pone con {@code .as(...)}.
 */
@AnalyzeClasses(
        packages = "co.edu.unicauca.cameia.perfil",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String PRESENTATION = "..presentation..";
    private static final String INFRASTRUCTURE = "..infrastructure..";

    @ArchTest
    static final ArchRule dominioNoDependeDeOtrasCapas = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage(PRESENTATION, APPLICATION, INFRASTRUCTURE)
            .as("El dominio no depende de ninguna otra capa")
            .because("el dominio no apunta a ninguna capa: por eso puede existir sin Spring, "
                    + "sin JPA y sin RabbitMQ (nota del diagrama de paquetes)");

    @ArchTest
    static final ArchRule dominioNoImportaFrameworks = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "com.rabbitmq..",
                    "com.google..")
            .as("El dominio no importa Spring, JPA ni RabbitMQ")
            .because("un agregado anotado con @Entity es el antipatrón número 3 de la sección 11 "
                    + "de las reglas de código");

    @ArchTest
    static final ArchRule presentacionNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage(PRESENTATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .as("La capa de presentación no depende de infraestructura")
            .because("presentation habla con application y con sus propios DTO "
                    + "(reglas de código 3.4, regla 5)");

    @ArchTest
    static final ArchRule aplicacionNoInyectaRepositoriosDeSpringData = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure.persistence.repository..")
            .as("La capa de aplicación no depende de los repositorios de Spring Data")
            .because("el caso de uso inyecta el PUERTO de domain.port, no el JpaRepository ni su "
                    + "adaptador; inyectarlo es el antipatrón 12 y acopla la aplicación a "
                    + "Spring Data (reglas de código 5.5.1)");

    @ArchTest
    static final ArchRule sinCiclosEntrePaquetes = slices()
            .matching("co.edu.unicauca.cameia.perfil.(*)..")
            .should().beFreeOfCycles()
            .as("No hay dependencias circulares entre paquetes")
            .because("una dependencia circular entre paquetes es un defecto, no un detalle "
                    + "(reglas de código 3.5, regla 8)");

    @ArchTest
    static final ArchRule controladoresTerminanEnController = classes()
            .that().resideInAPackage("..presentation.controller..")
            .should().haveSimpleNameEndingWith("Controller")
            .as("Las clases de presentation.controller terminan en Controller")
            .because("el sufijo dice la capa y el núcleo dice el concepto del glosario "
                    + "(reglas de código 5.2)");

    @ArchTest
    static final ArchRule serviciosTerminanEnAppService = classes()
            .that().resideInAPackage("..application.service..")
            .should().haveSimpleNameEndingWith("AppService")
            .as("Las clases de application.service terminan en AppService")
            .because("AppService distingue la capa de aplicación de los servicios de dominio, "
                    + "que no llevan sufijo (reglas de código 5.3)");

    @ArchTest
    static final ArchRule entidadesTerminanEnEntity = classes()
            .that().resideInAPackage("..infrastructure.persistence.entity..")
            .should().haveSimpleNameEndingWith("Entity")
            .as("Las clases de infrastructure.persistence.entity terminan en Entity")
            .because("el C4 dibuja el mismo concepto dos veces, como agregado y como clase JPA; "
                    + "sin el sufijo alguien termina anotando el agregado con @Entity");
}
