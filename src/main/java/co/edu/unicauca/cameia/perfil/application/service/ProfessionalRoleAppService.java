package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.domain.model.ProfessionalRole;
import co.edu.unicauca.cameia.perfil.domain.port.ProfessionalRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProfessionalRoleAppService {

    private final ProfessionalRoleRepository repository;

    ProfessionalRoleAppService(ProfessionalRoleRepository repository) {
        this.repository = repository;
    }

    public List<ProfessionalRole> listAll() {
        return repository.findAll();
    }
}
