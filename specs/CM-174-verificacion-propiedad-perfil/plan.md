# Plan — CM-174: Verificación de propiedad en endpoints de perfil (anti-IDOR)

## Estrategia

Agregar un método privado `loadForUser(UUID profileId, String uid)` en `ProfileAppService`
que carga el perfil (usando el `load()` existente) y verifica que
`profile.getFirebaseUid().value().equals(uid)`. Si no coincide lanza
`ProfileAccessDeniedException`. Si `uid` es nulo o vacío lanza `IdentityRequiredException`.

Los 14 métodos del service que hoy llaman a `load()` directamente pasan a llamar
`loadForUser()`. Los 15 endpoints del controlador agregan
`@RequestHeader(value = "X-User-Id", required = false) String uid` (required=false para que
Spring no lance 400 antes de que el service lance 401).

Dos nuevas excepciones de dominio → dos nuevos handlers en `ApiExceptionHandler`:
`ProfileAccessDeniedException → 403` e `IdentityRequiredException → 401`.

## Orden de cambios

1. **Excepciones nuevas** — sin dependencias, se pueden escribir primero.
2. **`ApiExceptionHandler`** — agregar los dos handlers (depende de las excepciones).
3. **`ProfileAppService`** — agregar `loadForUser()` y actualizar los 14 métodos.
4. **`ProfileController`** — agregar `uid` a los 15 endpoints y pasarlo al service.
5. **Swagger** — agregar `@ApiResponse(403)` y `@ApiResponse(401)` a los 15 endpoints.
6. **Tests** — nuevos casos en `ProfileControllerTest` y `ProfileAppServiceTest`.

## Detalle por archivo

### 1. `ProfileAccessDeniedException.java` (nuevo)
```java
package co.edu.unicauca.cameia.perfil.domain.exception;
public class ProfileAccessDeniedException extends RuntimeException {
    public ProfileAccessDeniedException() {
        super("No tienes permiso para acceder a este perfil");
    }
}
```

### 2. `IdentityRequiredException.java` (nuevo)
```java
package co.edu.unicauca.cameia.perfil.domain.exception;
public class IdentityRequiredException extends RuntimeException {
    public IdentityRequiredException() {
        super("Identidad del usuario requerida");
    }
}
```

### 3. `ApiExceptionHandler.java` — dos handlers nuevos
```java
@ExceptionHandler(ProfileAccessDeniedException.class)
ProblemDetail handleAccessDenied(ProfileAccessDeniedException ex) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    p.setTitle("Acceso denegado"); return p;
}

@ExceptionHandler(IdentityRequiredException.class)
ProblemDetail handleIdentityRequired(IdentityRequiredException ex) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    p.setTitle("Identidad requerida"); return p;
}
```

### 4. `ProfileAppService.java` — método `loadForUser`
```java
private ProfessionalProfile loadForUser(UUID profileId, String uid) {
    if (uid == null || uid.isBlank()) throw new IdentityRequiredException();
    var p = load(profileId);
    if (!p.getFirebaseUid().value().equals(uid)) throw new ProfileAccessDeniedException();
    return p;
}
```
Los 14 métodos afectados reemplazan `load(cmd.profileId())` / `load(profileId)` por
`loadForUser(cmd.profileId(), cmd.uid())` / `loadForUser(profileId, uid)`.
Los commands que no traen `uid` se extienden con el campo correspondiente.

### 5. Commands que necesitan `uid`
Los commands de los métodos que reciben un `cmd` necesitan el campo `uid`:
`UpdateProfileInfoCommand`, `AddWorkExperienceCommand`, `AddEducationCommand`,
`UpdateSalaryExpectationCommand`, `AddSkillCommand`, `AddTargetRoleCommand`,
`UpdateTargetRoleCommand`.
Los métodos que reciben `(UUID profileId, UUID subId)` directamente (removeXxx, getProfile,
requestReview, completeProfile) pasan a recibir además `String uid`.

### 6. `ProfileController.java`
Agregar a los 15 endpoints:
```java
@RequestHeader(value = "X-User-Id", required = false) String uid
```
Y `@ApiResponse(responseCode = "401")`, `@ApiResponse(responseCode = "403")` a cada uno.

## Tests nuevos mínimos (criterio de aceptación de Paula)

| Test | Qué verifica |
|------|-------------|
| `getProfile_returns403WhenProfileIsOwnedByAnotherUser` | 403 en GET ajeno |
| `patchProfile_returns403WhenProfileIsOwnedByAnotherUser` | 403 en PATCH ajeno |
| `getProfile_returns401WhenXUserIdIsMissing` | 401 sin header |
| `patchProfile_returns401WhenXUserIdIsMissing` | 401 sin header |
