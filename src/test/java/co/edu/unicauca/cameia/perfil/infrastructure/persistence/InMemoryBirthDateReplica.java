package co.edu.unicauca.cameia.perfil.infrastructure.persistence;

import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.domain.port.BirthDateReplica;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doble en memoria de la réplica de la fecha de nacimiento. Lo usan las pruebas unitarias del consumidor de eventos y
 * también las de las reglas de fechas de la experiencia laboral y la formación.
 */
public class InMemoryBirthDateReplica implements BirthDateReplica {

    private final Map<String, LocalDate> dates = new ConcurrentHashMap<>();

    @Override
    public Optional<LocalDate> findBirthDate(FirebaseUid userId) {
        return Optional.ofNullable(dates.get(userId.value()));
    }

    @Override
    public boolean saveIfAbsent(FirebaseUid userId, LocalDate birthDate) {
        return dates.putIfAbsent(userId.value(), birthDate) == null;
    }

    @Override
    public boolean delete(FirebaseUid userId) {
        return dates.remove(userId.value()) != null;
    }

    /** @return copia de las fechas guardadas, por identidad */
    public Map<String, LocalDate> contents() {
        return Map.copyOf(dates);
    }
}
