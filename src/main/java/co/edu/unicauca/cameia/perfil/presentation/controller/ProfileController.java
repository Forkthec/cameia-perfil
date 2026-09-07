package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddEducationRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddWorkExperienceRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints del perfil profesional (CM-16, CM-17, CM-18).
 * TODO CM-DEV-IN: confirmar que el Gateway propaga X-User-Id.
 */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileAppService profileAppService;

    ProfileController(ProfileAppService profileAppService) {
        this.profileAppService = profileAppService;
    }

    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(@RequestHeader("X-User-Id") String firebaseUid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.createProfile(new CreateProfileCommand(firebaseUid))));
    }

    @PatchMapping("/{id}")
    ResponseEntity<ProfileResponse> updateProfileInfo(@PathVariable UUID id, @RequestBody UpdateProfileInfoRequest req) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateProfileInfo(
                new UpdateProfileInfoCommand(id, req.name(), req.headline(), req.summary(), req.preferredModality(), req.provenance()))));
    }

    @PostMapping("/{id}/work-experiences")
    ResponseEntity<ProfileResponse> addWorkExperience(@PathVariable UUID id, @RequestBody AddWorkExperienceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(
                profileAppService.addWorkExperience(new AddWorkExperienceCommand(
                        id, req.company(), req.position(), req.description(),
                        req.startDate(), req.endDate(), req.employmentStatus(), req.seniority(), req.provenance()))));
    }

    @DeleteMapping("/{id}/work-experiences/{expId}")
    ResponseEntity<ProfileResponse> removeWorkExperience(@PathVariable UUID id, @PathVariable UUID expId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeWorkExperience(id, expId)));
    }

    @PostMapping("/{id}/educations")
    ResponseEntity<ProfileResponse> addEducation(@PathVariable UUID id, @RequestBody AddEducationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(
                profileAppService.addEducation(new AddEducationCommand(
                        id, req.institution(), req.degree(), req.fieldOfStudy(),
                        req.level(), req.startDate(), req.endDate(), req.inProgress(), req.provenance()))));
    }

    @DeleteMapping("/{id}/educations/{eduId}")
    ResponseEntity<ProfileResponse> removeEducation(@PathVariable UUID id, @PathVariable UUID eduId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeEducation(id, eduId)));
    }
}
