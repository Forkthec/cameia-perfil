# Plan — CM-22: Completitud del perfil

## Capas afectadas

| Capa | Archivo | Cambio |
|---|---|---|
| Dominio | `ProfessionalProfile.java` | `isComplete()` evalúa 5 requisitos; nuevo `getMissingRequirements()`; nuevo `complete()`; `removeTargetRole()` solo bloquea en COMPLETED |
| Dominio | `IncompleteProfileException.java` | Recibe `List<String>` de requisitos faltantes |
| Dominio (nuevo) | `ProfileAlreadyCompletedException.java` | Lanzada cuando se intenta completar un perfil ya COMPLETED |
| Aplicación | `ProfileAppService.java` | Nuevo método `completeProfile(UUID)` |
| Presentación (nuevo) | `CompletionErrorResponse.java` | `record` con `List<String> missingRequirements` para 422 |
| Presentación | `ProfileController.java` | Nuevo endpoint `POST /{id}/completion` → 200 con `ProfileResponse` o 422 con `CompletionErrorResponse` |
| Presentación | `ApiExceptionHandler.java` | Manejar `ProfileAlreadyCompletedException` → 409; actualizar handler de `IncompleteProfileException` para incluir la lista |

## Diseño del método `complete()` en el agregado

```java
public List<String> getMissingRequirements() {
    List<String> missing = new ArrayList<>();
    if (name == null || name.value().isBlank()) missing.add("name");
    if (summary == null || summary.value().isBlank()) missing.add("summary");
    if (educations.isEmpty()) missing.add("al menos 1 educación");
    if (profileSkills.isEmpty()) missing.add("al menos 1 habilidad");
    if (targetRoles.isEmpty()) missing.add("al menos 1 rol objetivo");
    return missing;
}

public void complete() {
    if (this.status == ProfileStatus.COMPLETED) throw new ProfileAlreadyCompletedException();
    var missing = getMissingRequirements();
    if (!missing.isEmpty()) throw new IncompleteProfileException(missing);
    this.status = ProfileStatus.COMPLETED;
    touch();
}
```

## Diseño de `removeTargetRole()` actualizado

```java
public void removeTargetRole(UUID roleId) {
    if (this.status == ProfileStatus.COMPLETED && targetRoles.size() == 1)
        throw new LastTargetRoleException();
    targetRoles.removeIf(r -> r.getId().equals(Objects.requireNonNull(roleId)));
    touch();
}
```
