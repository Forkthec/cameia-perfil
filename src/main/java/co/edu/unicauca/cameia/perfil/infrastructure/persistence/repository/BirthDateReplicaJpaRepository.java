package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.BirthDateReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/** Acceso a la tabla de la réplica; la inserción es atómica y conserva la primera fecha guardada. */
interface BirthDateReplicaJpaRepository extends JpaRepository<BirthDateReplicaEntity, String> {

    /**
     * Inserta la fecha si el Usuario aún no tiene una.
     *
     * @param firebaseUid identidad del Usuario
     * @param birthDate   fecha de nacimiento
     * @return 1 si se insertó, 0 si ya había una fila
     */
    @Modifying
    @Query(value = """
            INSERT INTO fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento)
            VALUES (:firebaseUid, :birthDate)
            ON CONFLICT (firebase_uid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("firebaseUid") String firebaseUid, @Param("birthDate") LocalDate birthDate);

    /**
     * Borra la fila del Usuario.
     *
     * @param firebaseUid identidad del Usuario
     * @return cantidad de filas borradas, 0 o 1
     */
    @Modifying
    @Query("delete from BirthDateReplicaEntity e where e.firebaseUid = :firebaseUid")
    int deleteByFirebaseUid(@Param("firebaseUid") String firebaseUid);
}
