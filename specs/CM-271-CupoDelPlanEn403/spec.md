# Spec — CM-271: el cupo de Perfiles Profesionales del plan responde 403 `PLAN_LIMIT`

- **Tarea:** CM-271 · Subtarea «Ajustes v4 – Backend Perfil (HU-2.2)» (Jira: «En revisión») · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Escrita contra `origin/develop` `2a54ca7`; se ejecuta en la cola sobre la punta de CM-73.
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`: CA-2.2.3, 2.2.6 (HU-2.2); CA-2.12.1, 2.12.11, 2.12.15, 2.12.18 (HU-2.12); CA-2.14.3 (HU-2.14).
  Todos dicen «→ 403 Forbidden con code PLAN_LIMIT». Nota técnica de HU-2.2: «mismo código que el cupo de entrevistas, HU-4.6».
- **Estado:** `LISTA PARA EJECUTAR` (sección 11). Sin pendientes que bloqueen.
- **Atributos de calidad:** interacción (AC-0006), compatibilidad de contrato, fiabilidad (FIA-01, FIA-02), seguridad (SEG-04), mantenibilidad (MAN-01).

## 1. Contexto y defecto comprobado

| # | Defecto | Dónde (`develop` `2a54ca7`) | ANTES | DESPUÉS |
|---|---|---|---|---|
| D1 | El cupo agotado responde 409 `PROFILE_LIMIT_REACHED` | `ProfileLimitReachedException` (código), `ErrorCatalog` (`CONFLICT`), `ProfileController` (`@ApiResponse` 409 y ejemplo), `docs/errores.md`, ADR 0003 paso 6, colección de Postman | Usuario con 1 perfil: `POST /api/v1/profiles` → 409 `PROFILE_LIMIT_REACHED` | **403** `PLAN_LIMIT` con el mismo `detail` «Tu Plan Free permite 1 Perfil Profesional.»; nada se crea |

La regla de decisión (ADR 0003: contar, bloquear por Usuario, volver a contar) no cambia; solo el código y el estado del rechazo.

## 2. Alcance y PR

Un PR: `CM-271-cupo-del-plan-en-403`, base la punta de CM-73. ≈ 420 líneas (≈ 200 de documentos). Fuera de alcance: sección 10.

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA | Técnico | Hoy | Requisito | Prueba | Postman |
|---|---|---|---|---|---|
| CA-2.2.3 | `POST /api/v1/profiles` → 403 `PLAN_LIMIT` (cupo del Plan Free agotado) | 409 | REQ-271-01 | `ProfileControllerTest.postProfiles_shouldReturn403PlanLimit_whenUserAlreadyHasProfile`; `PlanLimitIT.createProfile_shouldReturn403_whenFreeUserHasOneProfile` | `ERR POST /profiles — 403 cupo del Plan Free alcanzado (CA-2.2.3)` (la petición existente de la carpeta CM-16, que hoy espera 409) |
| CA-2.12.1 | ídem (Paywall de la interfaz) | 409 | REQ-271-01 | la misma | la misma |
| CA-2.12.15 | ídem, petición fuera de la interfaz | 409 | REQ-271-01, 02 | `PlanLimitIT.createProfile_shouldCreateNothing_whenLimitIsReached` | la misma + `OK GET` que cuenta 1 perfil |
| CA-2.12.18 | 403 con 3 perfiles y el Plan Free (Premium vencido) | 409 | REQ-271-03 | `PlanLimitIT.createProfile_shouldReturn403_whenFreeUserKeptThreeProfiles` | — (requiere 3 perfiles creados fuera del cupo: solo por base) |
| CA-2.2.6, 2.12.11 | 403 `PLAN_LIMIT` con 5 perfiles y Premium | — | Fuera de alcance: Perfil no conoce el plan (cupo fijo 1) | — | — |
| CA-2.14.3 | `POST /api/v1/profiles/{profile_id}/reactivate` → 403 `PLAN_LIMIT` | el endpoint no existe | Fuera de alcance (HU-2.14) | — | — |

## 4. Requisitos (EARS)

- **REQ-271-01 (CA-2.2.3, 2.12.1, 2.12.15).** Cuando un Usuario que ya tenía 1 o más Perfiles Profesionales antes de su petición llame a
  `POST /api/v1/profiles`, Perfil debe responder **403** con `code` `PLAN_LIMIT`, `title` «Cupo del plan alcanzado», `detail` «Tu Plan Free permite
  1 Perfil Profesional.», sin `errors`, `Content-Type` `application/problem+json;charset=UTF-8`, y no crear ningún perfil.
- **REQ-271-02 (FIA-01).** Tras el 403, el número de perfiles del Usuario es el mismo que antes de la petición.
- **REQ-271-03 (CA-2.12.18).** Con 3 perfiles del mismo Usuario en la base (cualquier estado), la creación responde el mismo 403 y no modifica ninguno
  de los 3.
- **REQ-271-04 (ADR 0003).** Las peticiones que llegan mientras la creación del mismo Usuario está en curso siguen recibiendo 201 con el mismo perfil;
  la que espera más de 2 s recibe 409 `PROFILE_CREATION_IN_PROGRESS` (CM-274). Solo el rechazo por cupo cambia.
- **REQ-271-05.** `PROFILE_LIMIT_REACHED` deja de emitirse: sale del enumerado `ErrorCode` y del catálogo, y pasa a «Códigos retirados» de
  `docs/errores.md` con «Ya no se emite; lo reemplaza `PLAN_LIMIT` (403).».
- **REQ-271-06 (SEG-04, R3).** El rechazo se registra una vez en `WARN` con `code=PLAN_LIMIT`, `requestId` y `firebaseUid`, sin traza.

## 5. Validación, base de datos y casos borde

- **Entrada:** `POST /api/v1/profiles` no tiene cuerpo (un cuerpo enviado se ignora, existente) y toma el dueño de `X-User-Id` (401 si falta).
- **Base de datos:** sin cambios.

| Grupo | Caso | Resultado | Prueba |
|---|---|---|---|
| Colecciones | 0 perfiles | 201 | existente (`ProfileCreationAppServiceTest`) |
| Colecciones | 1 perfil `IN_PROGRESS` / `COMPLETED` | 403 `PLAN_LIMIT` | `PlanLimitIT` (parametrizada por estado) |
| Colecciones | 3 perfiles (Premium vencido) | 403 | REQ-271-03 |
| Concurrencia | 5 `POST` simultáneos con 0 perfiles | 1 perfil; todas 201 (ADR 0003) | `ProfileCreationConcurrencyIT` (existente; sigue en verde) |
| Concurrencia | `POST` que llega con la creación anterior ya terminada | 403 `PLAN_LIMIT` (antes 409) | `ProfileCreationConcurrencyIT` (aserción por tipo de excepción; sin cambio) |
| Repetición | 20 `POST` seguidos con 1 perfil | 20 respuestas 403; sigue 1 perfil | `PlanLimitIT.createProfile_shouldStayAtOne_whenRepeatedTwentyTimes` |
| Propiedad | perfiles de otro Usuario no cuentan | 201 para un Usuario nuevo aunque otro tenga perfiles | existente (`ProfileCreationConcurrencyIT`, independencia entre Usuarios) |
| Dependencia lenta | otra creación retiene el bloqueo > 2 s | 409 `PROFILE_CREATION_IN_PROGRESS` (CM-274) | existente |
| Texto, fechas, enumerados, carga | no aplican: sin cuerpo | — | justificación |

## 6. Errores

| Código | HTTP | Título | Mensaje | Excepción | Cambio |
|---|---|---|---|---|---|
| `PLAN_LIMIT` | 403 | «Cupo del plan alcanzado» | «Tu Plan Free permite 1 Perfil Profesional.» | `ProfileLimitReachedException` | Nuevo (reservado por el estándar §A.5, con su forma propia) |
| `PROFILE_LIMIT_REACHED` | — | — | — | — | Retirado |

`PLAN_LIMIT` no sigue la forma `<SUJETO>_<CAUSA>`: el estándar de Backend lo conserva tal cual porque es el código común del cupo del plan (entrevistas,
HU-4.6, y perfiles). Un 403 de Perfil ya existía para el perfil ajeno (`PROFILE_NOT_ALLOWED`): el `code` los distingue y el Gateway usa 403 con
`EMAIL_NOT_VERIFIED`. Frontend debe resolver por `code`, no por estado (sección 9).

## 7. Seguridad

OWASP API4 (cupo por Usuario aplicado en el servidor, también fuera de la interfaz: CA-2.12.15) y API6 (flujo de creación con bloqueo por Usuario,
existente). ASVS 4.1.1 (charset, existente), 8.2.1 (la regla se aplica en el servidor). SEG-04: el registro del rechazo no lleva datos del perfil.
Las demás filas no aplican: sin cambios de identidad, cuerpo, rutas ni integraciones.

## 8. Atributos de calidad

| Atributo | Métrica | Cómo | Prueba |
|---|---|---|---|
| AC-0006 | INT | `code` estable que la interfaz traduce al Paywall | `ProfileControllerTest` y Newman |
| Compatibilidad de contrato | aviso a Frontend | documento de Frontend en `comunicaciones/` | — |
| AC-0004 | FIA-01, FIA-02 | 403 sin efecto; 20 repeticiones sin efecto | `PlanLimitIT` |
| AC-0003 | SEG-04 | registro sin datos personales | `ApiExceptionHandlerTest` (prueba del registro existente, con `code=PLAN_LIMIT`) |
| AC-0002 | DES-02 | p95 ≤ 2 s | Newman `responseTime < 2000` |
| MAN-01 | ≥ 90 % | `ProfileLimitReachedException`, `ErrorCatalog`, `ProfileCreationAppService` | JaCoCo |

## 9. Contrato y aviso a Frontend

`POST /api/v1/profiles`: 201, 401 `IDENTITY_REQUIRED`, **403 `PLAN_LIMIT`**, 409 `PROFILE_CREATION_IN_PROGRESS`, 500. El aviso (junto con los cambios de
CM-70 a CM-72 y el de Cuentas) está en `comunicaciones/10102026_v1_roles-objetivo-cupo-y-mi-cuenta-para-frontend.md`. Según su respuesta del 9-oct,
`cameia-web` ya trata como cupo agotado cualquier 409 o 403 de la creación y va a resolverlo por `code`: con este cambio debe mapear `PLAN_LIMIT` y puede
retirar `PROFILE_LIMIT_REACHED`.

## 10. Decisiones y fuera de alcance

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| D271-1 | Retirar `PROFILE_LIMIT_REACHED` y agregar `PLAN_LIMIT` | El backlog fija el nombre; un código publicado no se renombra (R3): se retira y se agrega, como hizo CM-274 con `PROFILE_CREATION_TIMEOUT` | Renombrar el valor del enumerado |
| D271-2 | Se conserva la clase `ProfileLimitReachedException` | Su nombre describe la regla (cupo de perfiles); solo cambia el código que emite | Clase nueva `PlanLimitException` (sin otra regla que la use en Perfil) |
| D271-3 | Mismo texto «Tu Plan Free permite 1 Perfil Profesional.» | Es el mensaje provisional de CA-2.2.3 mientras el Paywall no existe; la interfaz muestra el Paywall al recibir `PLAN_LIMIT` | Texto del Paywall (lo arma la interfaz, con botones) |

Fuera de alcance: Plan Premium y su mensaje «Alcanzaste el máximo de 5 Perfiles Profesionales de tu plan.» (CA-2.2.6, 2.12.11; requiere la réplica de
`entitlements.actualizados`, HU-2.12, Sprint 3); reactivar un perfil archivado (CA-2.14.3, HU-2.14); el Paywall (Frontend).

## 11. Línea base, lista de verificación y dictamen

**Línea base:** `clean verify` en la punta de CM-73 antes de la primera tarjeta.

| # | Comprobación | Comando o petición | Esperado |
|---|---|---|---|
| V1 | CA-2.2.3 | Newman `ERR POST /profiles — 403 cupo del Plan Free alcanzado` | 403, `code` `PLAN_LIMIT`, `detail` literal, `problem+json;charset=UTF-8` |
| V2 | CA-2.12.15 (adversarial: fuera de la interfaz) | `curl -s -X POST localhost:8082/api/v1/profiles -H "X-User-Id: {{user_id}}" -d '{"plan":"PREMIUM","status":"COMPLETED"}' -H "Content-Type: application/json"` | 403 `PLAN_LIMIT`; el cuerpo se ignora |
| V3 | CA-2.12.18 | `PlanLimitIT.createProfile_shouldReturn403_whenFreeUserKeptThreeProfiles` | verde |
| V4 | Repetición (adversarial) | `PlanLimitIT.createProfile_shouldStayAtOne_whenRepeatedTwentyTimes` | 1 perfil |
| V5 | Concurrencia | `ProfileCreationConcurrencyIT` | verde sin cambios |
| V6 | Código retirado | `git grep -n PROFILE_LIMIT_REACHED -- src docs` | solo la fila de «Códigos retirados» |
| V7 | Catálogo | `ErrorCatalogTest`, `ErrorCodeDocumentationTest` | verde |
| V8 | R10 | JaCoCo | ≥ 90 % en las clases tocadas |
| V9 | R1/R8 y tamaño | sin `CM-`; `--shortstat` ≤ 800 | |

**Regla → evidencia prevista:** R1/R8 → V9; R2 → §5 (sin cuerpo; cuerpo ignorado, V2); R3 → §6 y V6; R4 → no aplica; R5 → §5; R6 → §7 y V2;
R7 → D271-2; R9 → tarjetas; R10 → V8; R11 → T-271.3; R12 → §3; R13 → §8; R14 → sin push.

**Dictamen: `LISTA PARA EJECUTAR`.** Contraste hecho con `ProfileLimitReachedException`, `ErrorCatalog:72`, `ProfileController:70-110`,
`ProfileCreationAppService:43-55`, `docs/errores.md:29`, ADR 0003 (paso 6 y consecuencias) y la petición de Postman de la carpeta CM-16. Las pruebas que
fijaban el 409 (`ProfileControllerTest.postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile`, `ApiExceptionHandlerTest`) se cambian por
el CA del backlog v6; el cambio se anota en «Desvíos» de la cola.
