package co.edu.unicauca.cameia.perfil.presentation.controller;

import co.edu.unicauca.cameia.perfil.application.command.AddEducationCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddSkillCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.command.AddWorkExperienceCommand;
import co.edu.unicauca.cameia.perfil.application.command.CreateProfileCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateProfileInfoCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateSalaryExpectationCommand;
import co.edu.unicauca.cameia.perfil.application.command.UpdateTargetRoleCommand;
import co.edu.unicauca.cameia.perfil.application.service.ProfileAppService;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddEducationRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddSkillRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateTargetRoleRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.AddWorkExperienceRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.ProfileResponse;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateProfileInfoRequest;
import co.edu.unicauca.cameia.perfil.presentation.dto.UpdateSalaryExpectationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints del perfil profesional (CM-16 a CM-19). */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController {

    private final ProfileAppService profileAppService;
    ProfileController(ProfileAppService profileAppService) { this.profileAppService = profileAppService; }

    @PostMapping
    ResponseEntity<ProfileResponse> createProfile(@RequestHeader("X-User-Id") String uid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.createProfile(new CreateProfileCommand(uid))));
    }

    @GetMapping("/{id}")
    ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.getProfile(id)));
    }

    @PatchMapping("/{id}")
    ResponseEntity<ProfileResponse> updateProfileInfo(@PathVariable UUID id, @RequestBody UpdateProfileInfoRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateProfileInfo(
                new UpdateProfileInfoCommand(id, r.name(), r.summary(), r.preferredModality(), r.provenance()))));
    }

    @PostMapping("/{id}/work-experiences")
    ResponseEntity<ProfileResponse> addWorkExperience(@PathVariable UUID id, @RequestBody AddWorkExperienceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addWorkExperience(
                new AddWorkExperienceCommand(id, r.company(), r.position(), r.description(),
                        r.startDate(), r.endDate(), r.employmentStatus(), r.seniority(), r.provenance()))));
    }

    @DeleteMapping("/{id}/work-experiences/{expId}")
    ResponseEntity<ProfileResponse> removeWorkExperience(@PathVariable UUID id, @PathVariable UUID expId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeWorkExperience(id, expId)));
    }

    @PostMapping("/{id}/educations")
    ResponseEntity<ProfileResponse> addEducation(@PathVariable UUID id, @RequestBody AddEducationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addEducation(
                new AddEducationCommand(id, r.institution(), r.degree(), r.fieldOfStudy(),
                        r.level(), r.startDate(), r.endDate(), r.inProgress(), r.provenance()))));
    }

    @DeleteMapping("/{id}/educations/{eduId}")
    ResponseEntity<ProfileResponse> removeEducation(@PathVariable UUID id, @PathVariable UUID eduId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeEducation(id, eduId)));
    }

    @PatchMapping("/{id}/salary-expectation")
    ResponseEntity<ProfileResponse> updateSalaryExpectation(@PathVariable UUID id, @RequestBody UpdateSalaryExpectationRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateSalaryExpectation(
                new UpdateSalaryExpectationCommand(id, r.amount()))));
    }

    @PostMapping("/{id}/skills")
    ResponseEntity<ProfileResponse> addSkill(@PathVariable UUID id, @RequestBody AddSkillRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addSkill(
                new AddSkillCommand(id, r.skillName(), r.level(), r.provenance()))));
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    ResponseEntity<ProfileResponse> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeSkill(id, skillId)));
    }

    @PostMapping("/{id}/review-requests")
    ResponseEntity<ProfileResponse> requestReview(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileResponse.from(profileAppService.requestReview(id)));
    }

    @PostMapping("/{id}/target-roles")
    ResponseEntity<ProfileResponse> addTargetRole(@PathVariable UUID id, @RequestBody AddTargetRoleRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profileAppService.addTargetRole(
                new AddTargetRoleCommand(id, r.title(), r.seniority(), r.provenance()))));
    }

    @PatchMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<ProfileResponse> updateTargetRole(@PathVariable UUID id, @PathVariable UUID roleId,
                                                     @RequestBody UpdateTargetRoleRequest r) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.updateTargetRole(
                new UpdateTargetRoleCommand(id, roleId, r.title(), r.seniority()))));
    }

    @DeleteMapping("/{id}/target-roles/{roleId}")
    ResponseEntity<ProfileResponse> removeTargetRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        return ResponseEntity.ok(ProfileResponse.from(profileAppService.removeTargetRole(id, roleId)));
    }
}
