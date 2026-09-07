package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del perfil profesional.
 * CM-16: POST /api/v1/profiles — crea un perfil vacío en estado IN_PROGRESS.
 * TODO CM-DEV-IN: confirmar con el equipo que el Gateway propaga el UID en X-User-Id.
 */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileAppService profileAppService;

    ProfileController(ProfileAppService profileAppService) {
        this.profileAppService = profileAppService;
    }

    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(
            @RequestHeader("X-User-Id") String firebaseUid) {
        var profile = profileAppService.createProfile(new CreateProfileCommand(firebaseUid));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profile));
    }
}
