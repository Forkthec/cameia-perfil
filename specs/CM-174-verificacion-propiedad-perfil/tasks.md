# Tasks — CM-174: Verificación de propiedad en endpoints de perfil (anti-IDOR)

## Fase 1 — Excepciones nuevas

- [ ] Crear `ProfileAccessDeniedException` en `domain/exception/`
- [ ] Crear `IdentityRequiredException` en `domain/exception/`

## Fase 2 — Handler en ApiExceptionHandler

- [ ] Agregar handler `ProfileAccessDeniedException → 403` en `ApiExceptionHandler`
- [ ] Agregar handler `IdentityRequiredException → 401` en `ApiExceptionHandler`

## Fase 3 — Commands: agregar campo `uid`

- [ ] `UpdateProfileInfoCommand` — agregar `String uid`
- [ ] `AddWorkExperienceCommand` — agregar `String uid`
- [ ] `AddEducationCommand` — agregar `String uid`
- [ ] `UpdateSalaryExpectationCommand` — agregar `String uid`
- [ ] `AddSkillCommand` — agregar `String uid`
- [ ] `AddTargetRoleCommand` — agregar `String uid`
- [ ] `UpdateTargetRoleCommand` — agregar `String uid`

## Fase 4 — ProfileAppService

- [ ] Agregar `loadForUser(UUID profileId, String uid)` (lanza `IdentityRequiredException` si uid nulo/vacío, `ProfileAccessDeniedException` si uid no coincide)
- [ ] `updateProfileInfo` — usar `loadForUser` con `cmd.uid()`
- [ ] `addWorkExperience` — usar `loadForUser` con `cmd.uid()`
- [ ] `removeWorkExperience` — recibir `String uid`, usar `loadForUser`
- [ ] `addEducation` — usar `loadForUser` con `cmd.uid()`
- [ ] `removeEducation` — recibir `String uid`, usar `loadForUser`
- [ ] `updateSalaryExpectation` — usar `loadForUser` con `cmd.uid()`
- [ ] `addSkill` — usar `loadForUser` con `cmd.uid()`
- [ ] `removeSkill` — recibir `String uid`, usar `loadForUser`
- [ ] `requestReview` — recibir `String uid`, usar `loadForUser`
- [ ] `getProfile` — recibir `String uid`, usar `loadForUser`
- [ ] `addTargetRole` — usar `loadForUser` con `cmd.uid()`
- [ ] `updateTargetRole` — usar `loadForUser` con `cmd.uid()`
- [ ] `removeTargetRole` — recibir `String uid`, usar `loadForUser`
- [ ] `completeProfile` — recibir `String uid`, usar `loadForUser`

## Fase 5 — ProfileController

- [ ] `getProfile` — agregar `@RequestHeader(value="X-User-Id", required=false) String uid`, pasarlo al service; `@ApiResponse` 401 y 403
- [ ] `updateProfileInfo` — ídem; pasar `uid` en `UpdateProfileInfoCommand`
- [ ] `addWorkExperience` — ídem; pasar `uid` en `AddWorkExperienceCommand`
- [ ] `removeWorkExperience` — ídem; pasar `uid` al service
- [ ] `addEducation` — ídem; pasar `uid` en `AddEducationCommand`
- [ ] `removeEducation` — ídem; pasar `uid` al service
- [ ] `updateSalaryExpectation` — ídem; pasar `uid` en `UpdateSalaryExpectationCommand`
- [ ] `addSkill` — ídem; pasar `uid` en `AddSkillCommand`
- [ ] `removeSkill` — ídem; pasar `uid` al service
- [ ] `requestReview` — ídem; pasar `uid` al service
- [ ] `addTargetRole` — ídem; pasar `uid` en `AddTargetRoleCommand`
- [ ] `updateTargetRole` — ídem; pasar `uid` en `UpdateTargetRoleCommand`
- [ ] `removeTargetRole` — ídem; pasar `uid` al service
- [ ] `completeProfile` — ídem; pasar `uid` al service

## Fase 6 — Tests

- [ ] `ProfileControllerTest.getProfile_returns403WhenProfileIsOwnedByAnotherUser`
- [ ] `ProfileControllerTest.getProfile_returns401WhenXUserIdIsMissing`
- [ ] `ProfileControllerTest.patchProfile_returns403WhenProfileIsOwnedByAnotherUser`
- [ ] `ProfileControllerTest.patchProfile_returns401WhenXUserIdIsMissing`

## Fase 7 — Verificación

- [ ] `docker compose run --rm verify` — todos los tests en verde (sin regresiones)
