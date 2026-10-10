package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Fila de la réplica de la fecha de nacimiento de un Usuario.
 *
 * <p>La entidad existe para que {@code ddl-auto=validate} compruebe la tabla; la escritura es la consulta nativa del repositorio.</p>
 */
@Entity
@Table(name = "fecha_nacimiento_usuario")
public class BirthDateReplicaEntity {

    @Id
    @Column(name = "firebase_uid", length = 128)
    private String firebaseUid;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    /** Constructor para JPA. */
    protected BirthDateReplicaEntity() {
    }

    /** @return identidad del Usuario */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /** @return fecha de nacimiento replicada */
    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }
}
