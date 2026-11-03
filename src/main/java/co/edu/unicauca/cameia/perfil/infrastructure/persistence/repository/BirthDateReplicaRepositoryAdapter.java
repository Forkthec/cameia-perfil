package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.BirthDateReplicaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** Implementa la réplica de la fecha de nacimiento sobre PostgreSQL; las escrituras se unen a la transacción del servicio. */
@Repository
class BirthDateReplicaRepositoryAdapter implements BirthDateReplica {

    private final BirthDateReplicaJpaRepository jpa;

    BirthDateReplicaRepositoryAdapter(BirthDateReplicaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findBirthDate(FirebaseUid userId) {
        return jpa.findById(userId.value()).map(BirthDateReplicaEntity::getFechaNacimiento);
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(FirebaseUid userId, LocalDate birthDate) {
        return jpa.insertIfAbsent(userId.value(), birthDate) == 1;
    }

    @Override
    @Transactional
    public boolean delete(FirebaseUid userId) {
        return jpa.deleteByFirebaseUid(userId.value()) == 1;
    }
}
