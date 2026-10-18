# Plan · CM-201 Lista de perfiles

Spec: `spec.md` (misma carpeta). Rama `CM-201-lista-de-perfiles` desde `origin/develop` `0854e5c`. Un PR, sin dependencias.

## 1. Enfoque

Lectura pura en cuatro piezas, siguiendo el patrón del repo (controlador → servicio de aplicación → puerto → adaptador):

1. **Dominio:** `ProfileStatusFilter` interpreta `?status=`; `ProfileSummary` es la vista mínima de un perfil; `ProfileStatusInvalidException` con su código.
2. **Puerto y adaptador:** un método de lectura nuevo con una sola consulta (perfiles con sus roles).
3. **Aplicación y web:** `ProfileAppService.listProfiles` y `GET /api/v1/profiles` con `no-store`.
4. **Pruebas, Postman y documentos.**

## 2. Reutilización

| Se reutiliza | Por qué |
|---|---|
| `FirebaseUid.required` | Ya da el 401 `IDENTITY_REQUIRED` |
| `ProfileAppService` (se extiende) | Ya concentra las lecturas del perfil con `@Transactional(readOnly = true)`; un servicio nuevo para un método no se justifica |
| `ProfessionalProfileRepository` (se extiende) y su adaptador | Mismo agregado y mismas tablas |
| `ErrorCatalog`, `ApiExceptionHandler` | La excepción nueva pasa por el manejador común de `BusinessException` |
| Patrón de `UnsupportedLanguageException` | Parámetro de consulta inválido → 422 sin `errors` |
| `ApiTestClient`, forma de `TargetRolesEndToEndIT` | Pruebas de punta a punta |

**No** se reutiliza `findById` del agregado para cada perfil: cargaría experiencia, formación y habilidades (N+1, decisión I-1).

## 3. Archivos

| Acción | Ruta (bajo `src/main/java/co/edu/unicauca/cameia/perfil/` salvo indicación) |
|---|---|
| crear | `domain/model/ProfileStatusFilter.java`, `domain/model/ProfileSummary.java` |
| crear | `domain/exception/ProfileStatusInvalidException.java` |
| modificar | `domain/exception/ErrorCode.java` (+`PROFILE_STATUS_INVALID_VALUE`) |
| modificar | `domain/port/ProfessionalProfileRepository.java` (+`findSummariesByOwner`) |
| modificar | `infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java` (+2 consultas) |
| modificar | `infrastructure/persistence/repository/ProfessionalProfileRepositoryAdapter.java` (+1 método) |
| modificar | `infrastructure/persistence/repository/ProfessionalProfileMapping.java` (+`toSummary`) |
| modificar | `application/service/ProfileAppService.java` (+`listProfiles`) |
| crear | `presentation/dto/ProfileListResponse.java` |
| modificar | `presentation/controller/ProfileController.java` (+`listProfiles`) |
| modificar | `presentation/advice/ErrorCatalog.java` (+1 fila) |
| pruebas | ver `tasks.md` |
| modificar | `docs/errores.md`, `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, `docs/bitacora-ia/02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md` (enlace) |
| crear | `docs/adr/0007-lista-de-perfiles.md` |

No se tocan: migraciones, eventos, `.github/`, `ProfileResponse`.

## 4. Commits

| Tarjeta | Commit |
|---|---|
| T1 | `CM-201 \| feat(perfil): filtro de estado y vista resumida del perfil` |
| T2 | `CM-201 \| feat(perfil): consulta de los perfiles del usuario con sus roles en una sola lectura` |
| T3 | `CM-201 \| feat(perfil): GET /api/v1/profiles con la lista ordenada y sin caché` |
| T4 | `CM-201 \| test(perfil): pruebas de punta a punta y colección de Postman de la lista` |
| T5 | `CM-201 \| docs(perfil): catálogo de errores, ADR 0007 y enlace de la bitácora` |

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| `left join fetch` con orden y `@OrderBy` de la colección | El mapeo ordena los roles por `createdAt`, `id` en Java; la prueba lo comprueba con roles insertados fuera de orden |
| La copia local del libro no tiene L-13 | La forma sale del aviso del 17-oct; Paula pega L-13 en Drive si falta |
| Docker apagado | T0 lo exige; los `*IT` no se cuentan como verdes si no corrieron |
