package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProfessionalProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Pieza 2 del patrón de tres piezas (AGENTS §5.3): repositorio Spring Data.
 * Spring genera la implementación en tiempo de ejecución. El adaptador la inyecta.
 * La capa de aplicación NUNCA ve esta interfaz (test ArchUnit {@code aplicacionNoInyectaRepositoriosDeSpringData}).
 */
interface ProfessionalProfileJpaRepository extends JpaRepository<ProfessionalProfileEntity, UUID> {

    boolean existsByFirebaseUid(String firebaseUid);
}
