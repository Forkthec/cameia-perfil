package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints del perfil profesional (CM-16 y CM-17).
 * TODO CM-DEV-IN: confirmar que el Gateway propaga X-User-Id.
 * TODO Sprint 2: agregar verificación de autorización por propietario.
 */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileAppService profileAppService;

    ProfileController(ProfileAppService profileAppService) {
        this.profileAppService = profileAppService;
    }

    // ── CM-16 ─────────────────────────────────────────────────────────────

    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(
            @RequestHeader("X-User-Id") String firebaseUid) {
        var profile = profileAppService.createProfile(new CreateProfileCommand(firebaseUid));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profile));
    }

    // ── CM-17 ─────────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    ResponseEntity<ProfileResponse> updateProfileInfo(
            @PathVariable UUID id,
            @RequestBody UpdateProfileInfoRequest request) {
        var cmd = new UpdateProfileInfoCommand(
                id, request.name(), request.headline(),
                request.summary(), request.preferredModality(), request.provenance());
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateProfileInfo(cmd)));
    }
}
