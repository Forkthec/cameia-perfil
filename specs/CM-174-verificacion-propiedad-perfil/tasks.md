# Tasks — CM-174: Verificación de propiedad en endpoints de perfil (anti-IDOR)

## Fase 1 — Excepciones nuevas

- [x] Crear `ProfileAccessDeniedException` en `domain/exception/`
- [x] Crear `IdentityRequiredException` en `domain/exception/`

## Fase 2 — Handler en ApiExceptionHandler

- [x] Agregar handler `ProfileAccessDeniedException → 403` en `ApiExceptionHandler`
- [x] Agregar handler `IdentityRequiredException → 401` en `ApiExceptionHandler`

## Fase 3 — Commands: agregar campo `uid`

- [x] `UpdateProfileInfoCommand` — agregar `String uid`
- [x] `AddWorkExperienceCommand` — agregar `String uid`
- [x] `AddEducationCommand` — agregar `String uid`
- [x] `UpdateSalaryExpectationCommand` — agregar `String uid`
- [x] `AddSkillCommand` — agregar `String uid`
- [x] `AddTargetRoleCommand` — agregar `String uid`
- [x] `UpdateTargetRoleCommand` — agregar `String uid`

## Fase 4 — ProfileAppService

- [x] Agregar `loadForUser(UUID profileId, String uid)` (lanza `IdentityRequiredException` si uid nulo/vacío, `ProfileAccessDeniedException` si uid no coincide)
- [x] `updateProfileInfo` — usar `loadForUser` con `cmd.uid()`
- [x] `addWorkExperience` — usar `loadForUser` con `cmd.uid()`
- [x] `removeWorkExperience` — recibir `String uid`, usar `loadForUser`
- [x] `addEducation` — usar `loadForUser` con `cmd.uid()`
- [x] `removeEducation` — recibir `String uid`, usar `loadForUser`
- [x] `updateSalaryExpectation` — usar `loadForUser` con `cmd.uid()`
- [x] `addSkill` — usar `loadForUser` con `cmd.uid()`
- [x] `removeSkill` — recibir `String uid`, usar `loadForUser`
- [x] `requestReview` — recibir `String uid`, usar `loadForUser`
- [x] `getProfile` — recibir `String uid`, usar `loadForUser`
- [x] `addTargetRole` — usar `loadForUser` con `cmd.uid()`
- [x] `updateTargetRole` — usar `loadForUser` con `cmd.uid()`
- [x] `removeTargetRole` — recibir `String uid`, usar `loadForUser`
- [x] `completeProfile` — recibir `String uid`, usar `loadForUser`

## Fase 5 — ProfileController

- [x] `getProfile` — agregar `@RequestHeader(value="X-User-Id", required=false) String uid`, pasarlo al service; `@ApiResponse` 401 y 403
- [x] `updateProfileInfo` — ídem; pasar `uid` en `UpdateProfileInfoCommand`
- [x] `addWorkExperience` — ídem; pasar `uid` en `AddWorkExperienceCommand`
- [x] `removeWorkExperience` — ídem; pasar `uid` al service
- [x] `addEducation` — ídem; pasar `uid` en `AddEducationCommand`
- [x] `removeEducation` — ídem; pasar `uid` al service
- [x] `updateSalaryExpectation` — ídem; pasar `uid` en `UpdateSalaryExpectationCommand`
- [x] `addSkill` — ídem; pasar `uid` en `AddSkillCommand`
- [x] `removeSkill` — ídem; pasar `uid` al service
- [x] `requestReview` — ídem; pasar `uid` al service
- [x] `addTargetRole` — ídem; pasar `uid` en `AddTargetRoleCommand`
- [x] `updateTargetRole` — ídem; pasar `uid` en `UpdateTargetRoleCommand`
- [x] `removeTargetRole` — ídem; pasar `uid` al service
- [x] `completeProfile` — ídem; pasar `uid` al service

## Fase 6 — Tests

- [x] `ProfileControllerTest.getProfile_returns403WhenProfileIsOwnedByAnotherUser`
- [x] `ProfileControllerTest.getProfile_returns401WhenXUserIdIsMissing`
- [x] `ProfileControllerTest.patchProfile_returns403WhenProfileIsOwnedByAnotherUser`
- [x] `ProfileControllerTest.patchProfile_returns401WhenXUserIdIsMissing`

## Fase 7 — Verificación

- [x] `docker compose run --rm verify` — 72 tests en verde (19/09/2026)
