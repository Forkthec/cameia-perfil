# Tareas — CM-274 (cameia-perfil)

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Seis PR (bloques A a F). Una tarjeta a la vez: se ejecuta, se corre lo que dice «Comandos», se pega la salida real y recién entonces se marca `[x]`.

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`. Migraciones: `src/main/resources/db/migration/`.
2. **Idioma (R1).** En inglés: identificadores (clases, métodos, variables, constantes, paquetes), nombres de métodos de prueba y códigos de error. En español: Javadoc, comentarios de bloque y de línea (también los de SQL), `@DisplayName`, descripciones de OpenAPI (`@Schema`, `@Operation`, `@ApiResponse`), mensajes de log, los textos de `.because(...)` de ArchUnit y los mensajes para la persona. Los mensajes para la persona escritos entre « » se copian exactos (tilde y punto final). Los esqueletos de esta tarjeta ya traen el Javadoc en español: cópialo. Lo ajeno que esté en otro idioma no se renombra.
3. **Validación en dos capas.** Borde: `record` de petición con Bean Validation y `@Valid`. Dominio: fábricas `create` y agregado. Cada campo se prueba con: ausente, `null`, `""`, `"   "`, `"\t\n"`, U+00A0; n−1, n, n+1 contando puntos de código en NFC (tildes, `ñ`, emoji, combinantes); formato; fuera del enumerado; campos no esperados ignorados. Todos los errores de forma salen a la vez, uno por campo, con `field`, `code` y `message`.
4. **Errores.** Nunca 500 por una entrada del cliente. Un código = un estado = un mensaje = una excepción = al menos una prueba. Respuesta `application/problem+json;charset=UTF-8` con `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId` y, en 422, `errors[]` con `detail` «Revisa los campos marcados.». Ninguna respuesta lleva traza, SQL, nombre de clase ni el texto de una excepción de librería. Sin `catch` vacío. Log de un rechazo: una vez, en `WARN`, con `code`, `requestId` y `firebaseUid`; nunca empresa, cargo, institución, título, descripción ni fechas de nacimiento.
5. **Base de datos.** Cambio de esquema = migración nueva; nunca editar V1 a V4. Números fijos (plan de ejecución de Perfil): V5 es de CM-279 bloque P1, que se fusiona antes; esta tarea crea `V6__alinear_experiencia_laboral.sql` (PR 2) y `V7__alinear_educacion.sql` (PR 3); V8 es de CM-66 y V9 de CM-67. Flyway falla al arrancar si dos archivos tienen el mismo número o si uno menor llega después de uno ya aplicado: si al empezar no está `V5__replica_fecha_nacimiento.sql` en la base de la rama, o ya existe otro V6 o V7, detente y reporta. `VARCHAR(n)` = límite del dominio = `@CodePointSize(max)` = `@Schema(maxLength)` = `@Column(length)`. Cada `CHECK` tiene una prueba que lo viola.
6. **Código.** Métodos de ≈ 20 líneas, ≤ 3 parámetros (si hay más, un `record`), retornos tempranos, constantes con nombre. `domain` no importa Spring, JPA ni Jakarta (`ArquitecturaTest`). `@Transactional` solo en `application.service` (el adaptador conserva los que ya tiene). Sin `Utils`, sin dependencias nuevas, sin refactorizar fuera de la tarjeta. Antes de crear una clase, `git grep` por si ya existe.
7. **Documentación.** Javadoc en toda clase, `record`, `enum` y método nuevo o modificado (con el porqué, `@param`, `@return`, `@throws`). Comentario de bloque sobre la lógica importante. Sin `CM-NNN`, sin rutas a otros archivos, sin `TODO`, sin código comentado. Todo código de error nuevo o cuyo texto cambia se agrega o actualiza en `docs/errores.md` en el mismo PR, con las columnas `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`.
8. **Pruebas.** Nombre `method_shouldResult_whenCondition`, `@DisplayName` en español, Arrange-Act-Assert, un comportamiento por prueba, datos literales, sin `Thread.sleep`, reloj fijo. Toda prueba afirma valores concretos. Las `*IT` las corre Failsafe dentro de `verify` y usan Testcontainers (`postgres:16-alpine`); nunca H2.
9. **Comandos.** Una clase: `./mvnw.cmd -q -B "-Dtest=<Clase>" test` (o `"-Dit.test=<Clase>" verify` para una `*IT`). Suite: `./mvnw.cmd -B test`. Cierre de PR: `./mvnw.cmd -B clean verify` (necesita Docker encendido) y la cobertura en `target/site/jacoco/jacoco.csv`.
10. **Detente y reporta** (sin improvisar) si la tarjeta no coincide con el código real, falta un dato, una prueba existente se rompe sin causa clara, o hace falta algo que la tarjeta no lista. Reporta todo error con su salida real; no lo ocultes ni lo minimices.
11. **Commit:** `CM-274 | <tipo>(perfil): <resultado>` (tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `build`, `chore`), en español, sin `[IA-ASISTIDO]`, con el trailer `Co-Authored-By`. Push y PR solo con el sí de Paula.

---

# PR 1 — Base de escritura (`CM-274-base-escritura-perfil`, desde `origin/develop`)

## [x] T-A0 · Reproducir los defectos antes de corregir — ≤ 30 min

- **Objetivo:** dejar evidencia real de los defectos D1, D2, D4 a D7 y D10 sobre `develop` sin cambiar código de producción.
- **Pasos:** en una rama temporal desde `origin/develop`, `docker compose up -d --build`, y con `curl` (o Postman) contra `http://localhost:8082`:
  1. `POST /api/v1/profiles` con `X-User-Id: uid-repro-1` → guarda `id`.
  2. D1: `POST /api/v1/profiles/{id}/work-experiences` cuerpo `{"company":"Acme","position":"Dev","employmentStatus":"UNKNOWN_END","provenance":"MANUAL"}` → se espera ver 422 `START_DATE_REQUIRED`.
  3. D2: empresa de 101 `a` con `"employmentStatus":"CURRENT","startDate":"2024-06"` → se espera 201.
  4. D4: cuatro altas válidas más → la quinta experiencia responde 201.
  5. D7: `startDate` `"2099-01"` con `CURRENT` → 201.
  6. D10: `"company":"Acme\u0000"` → 500 `INTERNAL_ERROR`.
  7. D5: perfil completo (nombre, resumen, 1 formación, 1 habilidad, 1 rol, `POST …/completion`) y `DELETE …/educations/{eduId}` → 204.
- **Salida:** pegar cada respuesta (estado y cuerpo) en `specs/CM-274-experiencia-formacion-v4/evidencia-antes.md`. Si algún resultado no coincide con lo esperado, detenerse y reportar.
- **Terminado:** archivo con las siete respuestas reales; `docker compose down -v`.

- **Resultado (9-oct-2026):** los siete defectos se reprodujeron; las respuestas reales están en `evidencia-antes.md`. Entorno: jar de la cadena en el puerto 18082 y PostgreSQL desechable en el 55432 (el 5432 no se tocó). Las altas de formación y habilidad de preparación usan `level` `UNDERGRADUATE` y `skillName`, los nombres reales de los campos.

## [x] T-A1 · `SingleLineText` — ≤ 30 min, ≈ 120 líneas

- **Crear** `domain/model/SingleLineText.java`:
  ```java
  /**
   * Texto de una línea tal como lo entiende el negocio: sin espacios en los extremos y en Unicode NFC,
   * para que el mismo texto escrito de dos formas sea el mismo valor.
   *
   * <p>«Espacio» es lo que quita {@code String.prototype.trim} de JavaScript en el navegador: los
   * caracteres de {@link Character#isWhitespace(int)}, los separadores de espacio de Unicode (incluido
   * el espacio duro U+00A0) y U+FEFF. El largo se cuenta en puntos de código, así que un carácter fuera
   * del plano básico (un emoji) cuenta como uno.</p>
   */
  public final class SingleLineText {

      private SingleLineText() { }

      /**
       * Recorta y normaliza un texto recibido sin rechazar la ausencia.
       *
       * @param raw texto recibido; puede ser {@code null}
       * @return el texto recortado y en NFC, o {@code null} si {@code raw} es {@code null}
       */
      public static String normalize(String raw) { ... }

      /**
       * Igual que {@link #normalize(String)}, pero un resultado vacío pasa a {@code null}: es para los campos opcionales.
       *
       * @param raw texto recibido; puede ser {@code null}
       * @return el texto normalizado, o {@code null} si no queda nada
       */
      public static String normalizeOptional(String raw) { ... }

      /**
       * Largo del texto como lo cuenta el negocio.
       *
       * @param text texto ya normalizado; no {@code null}
       * @return cantidad de puntos de código
       */
      public static int length(String text) { return text.codePointCount(0, text.length()); }

      // trim(String) e isSpace(int): recorren por puntos de código desde los dos extremos (ver el Javadoc de la clase).
  }
  ```
  `normalize`: `Normalizer.normalize(raw, Normalizer.Form.NFC)` y luego recorte por puntos de código con `isSpace(cp) = Character.isWhitespace(cp) || Character.getType(cp) == Character.SPACE_SEPARATOR || cp == 0xFEFF`.
- **Pruebas** `domain/model/SingleLineTextTest.java` (sin Spring):
  - `normalize_shouldTrimJavaScriptSpaces_whenTextHasThem` (parametrizada): `"  Acme  "`→`"Acme"`; `" Acme "`→`"Acme"`; `"﻿Acme"`→`"Acme"`; `"\t\nAcme\r\n"`→`"Acme"`.
  - `normalize_shouldKeepInnerSpaces_whenTextHasThem`: `"Acme  S.A."`→`"Acme  S.A."`.
  - `normalize_shouldKeepZeroWidthSpace_whenAtTheEdge`: `"​Acme"` se conserva igual (no es espacio para JavaScript).
  - `normalize_shouldComposeToNfc_whenTextIsDecomposed`: `"é"`→`"é"` y `length` 1.
  - `normalize_shouldReturnNull_whenTextIsNull`.
  - `normalizeOptional_shouldReturnNull_whenOnlySpacesRemain`: `"   "`, `" "`, `""` → `null`.
  - `length_shouldCountCodePoints_whenTextHasEmoji`: `"😀"`→1; `"a😀b"`→3.
- **Trampa:** las pruebas con caracteres invisibles se escriben con escapes `\uXXXX` en el código fuente, nunca con el carácter pegado (la herramienta de edición los convierte: verifica con `git diff` que el archivo contiene la barra y la `u`).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=SingleLineTextTest" test` → 0 fallos.

- **Resultado (9-oct-2026):** rojo: `SingleLineTextTest` no compilaba (faltaba la clase). Verde: 24/24 (`./mvnw -B test -Dtest=SingleLineTextTest`). `hasControlCharacter` se escribió aquí porque las mismas pruebas la cubren; T-A3 ya no la crea. Los invisibles de la prueba están con escapes `\uXXXX` (la herramienta de edición los convertía en el carácter real).

## [x] T-A2 · Acumulador de errores en `InvalidFieldsException` — ≤ 20 min, ≈ 80 líneas

- **Modificar** `domain/exception/InvalidFieldsException.java`: agregar la clase anidada
  ```java
  /**
   * Junta los errores de campo mientras corren todas las reglas de un registro y conserva solo el
   * primero de cada campo, para que una sola respuesta liste todos los campos rechazados a la vez.
   */
  public static final class Collector {
      private final Map<String, FieldError> firstPerField = new LinkedHashMap<>();

      /**
       * Anota un error salvo que el campo ya tenga uno.
       *
       * @param field   nombre del campo en el JSON
       * @param code    código estable del error
       * @param message mensaje para la persona
       */
      public void add(String field, ErrorCode code, String message) { firstPerField.putIfAbsent(field, new FieldError(field, code, message)); }

      /** @param field nombre del campo en el JSON @return {@code true} si el campo ya tiene un error */
      public boolean hasError(String field) { return firstPerField.containsKey(field); }

      /** @throws InvalidFieldsException con todos los errores anotados, si hay al menos uno */
      public void throwIfAny() { if (!firstPerField.isEmpty()) throw new InvalidFieldsException(List.copyOf(firstPerField.values())); }
  }
  ```
  El Javadoc de la clase ya está en español; el de lo nuevo también va en español.
- **Pruebas** `domain/exception/InvalidFieldsExceptionTest.java`: `throwIfAny_shouldDoNothing_whenNoErrors`; `throwIfAny_shouldThrowEveryField_whenTwoFieldsFail` (`company`/`COMPANY_TOO_LONG`, `position`/`POSITION_TOO_LONG` → dos errores en ese orden); `add_shouldKeepFirstError_whenFieldFailsTwice` (`endDate` con `END_DATE_NOT_ALLOWED` y luego `END_DATE_IN_THE_FUTURE` → solo el primero); `constructor_shouldRejectEmptyList_whenNoErrors` (`IllegalArgumentException`); `getErrors_shouldBeUnmodifiable_whenReturned`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=InvalidFieldsExceptionTest" test`.

- **Resultado (9-oct-2026):** rojo: `InvalidFieldsExceptionTest` no compilaba (faltaba `Collector`). Verde: 8/8 (`./mvnw -B test -Dtest=InvalidFieldsExceptionTest`). Se agregaron también `of(FieldError)` y `Collector.add(FieldError)`, que pide T-A3. La prueba del campo repetido usa `END_DATE_NOT_ALLOWED` y `END_DATE_BEFORE_START_DATE` porque `END_DATE_IN_THE_FUTURE` aún no existe.

## [x] T-A3 · Restricciones de borde: largo y reglas del dominio — ≤ 30 min, ≈ 240 líneas

- **Por qué así:** cada regla de forma se escribe una sola vez. `@NotBlank` y `@CodePointSize` informan la ausencia y el largo (código y mensaje desde `ErrorCatalog`, como hoy). Los caracteres de control, el formato de mes y las opciones de los enumerados los decide el dominio (o `CommandValues`, que ya lanza `InvalidFieldsException` con su código y su mensaje); `@DomainRule` ejecuta esa misma regla en el borde y toma de la excepción el código y el mensaje. Así todos los errores de forma salen a la vez sin copiar ninguna regla en un validador. Es el patrón vigente en `cameia-cuentas` (`presentation/dto/DomainRule.java`); aquí se escribe completo porque los servicios no comparten código.
- **Modificar** `domain/model/SingleLineText.java` (de T-A1): agregar
  ```java
  /**
   * Indica si el texto tiene un carácter de control que el campo no admite.
   *
   * <p>Solo cuenta la categoría Unicode Cc (U+0000–U+001F y U+007F–U+009F): PostgreSQL rechaza
   * U+0000 y los demás son invisibles. Los de formato (Cf) se admiten a propósito: la unión de ancho
   * cero U+200D forma parte de emojis compuestos, como el de una familia.</p>
   *
   * @param text            texto ya normalizado; no {@code null}
   * @param allowLineBreaks {@code true} en los campos de varias líneas, que admiten tabulador, salto de línea y retorno
   * @return {@code true} si hay un carácter de control prohibido
   */
  public static boolean hasControlCharacter(String text, boolean allowLineBreaks) { ... }
  ```
  Recorre por puntos de código: prohibido si `Character.getType(cp) == Character.CONTROL` y no (`allowLineBreaks` y `cp` es `0x09`, `0x0A` o `0x0D`).
- **Modificar** `domain/exception/InvalidFieldsException.java`: agregar `public static InvalidFieldsException of(FieldError error)` y, en `Collector`, `public void add(FieldError error)` (mismo efecto que `add(field, code, message)`).
- **Modificar** `domain/exception/ErrorCode.java`: agregar, con Javadoc en español («{El dato} tiene un carácter de control que el campo no admite.»), `COMPANY_INVALID_CHARACTERS`, `POSITION_INVALID_CHARACTERS`, `DESCRIPTION_INVALID_CHARACTERS`, `INSTITUTION_INVALID_CHARACTERS`, `DEGREE_INVALID_CHARACTERS`. Agregarlos a `ErrorCatalogTest.DOMAIN_FIELD_CODES` (los emite el dominio).
- **Modificar** `domain/model/WorkExperience.java` y `domain/model/Education.java`: solo agregar las constantes del rechazo por caracteres (el resto de cada clase lo cambian T-B2 y T-C2):
  ```java
  /** Rechazo de una empresa con un carácter de control. */
  public static final FieldError COMPANY_CHARACTERS =
          new FieldError("company", ErrorCode.COMPANY_INVALID_CHARACTERS, "La empresa tiene caracteres no permitidos.");
  ```
  Con los textos exactos: `POSITION_CHARACTERS` («position», «El cargo tiene caracteres no permitidos.»), `DESCRIPTION_CHARACTERS` («description», «La descripción tiene caracteres no permitidos.») en `WorkExperience`; `INSTITUTION_CHARACTERS` («institution», «La institución tiene caracteres no permitidos.») y `DEGREE_CHARACTERS` («degree», «El título obtenido tiene caracteres no permitidos.») en `Education`.
- **Crear** en `presentation/dto/validation/` (paquete nuevo con `package-info.java`: «Restricciones de Bean Validation de los records de petición. Cada una ignora el valor ausente o en blanco, así que solo @NotBlank informa la ausencia y cada campo tiene a lo sumo una violación.»):
  - `@CodePointSize(int max)` + `CodePointSizeValidator`: válido si `value == null || SingleLineText.length(value) <= max`.
  - `@DomainRule(Rule value)` + `DomainRuleValidator`:
    ```java
    /**
     * Ejecuta en el borde una regla de forma escrita una sola vez en el dominio, para que se informe
     * junto con los demás errores de campo. El código y el mensaje salen del rechazo del dominio.
     */
    @Documented
    @Constraint(validatedBy = DomainRuleValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DomainRule {

        /** @return regla que se aplica al campo */
        Rule value();

        /** @return texto de respaldo; el real es el mensaje del rechazo del dominio */
        String message() default "Valor no válido";

        /** @return grupos de validación; las peticiones no usan ninguno */
        Class<?>[] groups() default {};

        /** @return carga de la restricción; las peticiones no usan ninguna */
        Class<? extends Payload>[] payload() default {};

        /** Reglas de forma de las peticiones, con la comprobación del dominio que corre y los códigos que informa. */
        enum Rule {
            COMPANY(text -> rejectControl(text, false, WorkExperience.COMPANY_CHARACTERS), Set.of(COMPANY_INVALID_CHARACTERS)),
            POSITION(text -> rejectControl(text, false, WorkExperience.POSITION_CHARACTERS), Set.of(POSITION_INVALID_CHARACTERS)),
            DESCRIPTION(text -> rejectControl(text, true, WorkExperience.DESCRIPTION_CHARACTERS), Set.of(DESCRIPTION_INVALID_CHARACTERS)),
            INSTITUTION(text -> rejectControl(text, false, Education.INSTITUTION_CHARACTERS), Set.of(INSTITUTION_INVALID_CHARACTERS)),
            DEGREE(text -> rejectControl(text, false, Education.DEGREE_CHARACTERS), Set.of(DEGREE_INVALID_CHARACTERS)),
            EMPLOYMENT_STATUS(text -> CommandValues.option(EmploymentStatus.class, text, EMPLOYMENT_STATUS_INVALID_VALUE, "employmentStatus"),
                    Set.of(EMPLOYMENT_STATUS_INVALID_VALUE)),
            EDUCATION_LEVEL(text -> CommandValues.option(EducationLevel.class, text, EDUCATION_LEVEL_INVALID_VALUE, "level"),
                    Set.of(EDUCATION_LEVEL_INVALID_VALUE)),
            START_DATE(text -> CommandValues.yearMonth(text, false, START_DATE_INVALID_FORMAT, "startDate"), Set.of(START_DATE_INVALID_FORMAT)),
            END_DATE(text -> CommandValues.yearMonth(text, false, END_DATE_INVALID_FORMAT, "endDate"), Set.of(END_DATE_INVALID_FORMAT));

            private final Consumer<String> validation;
            private final Set<ErrorCode> reportedCodes;

            Rule(Consumer<String> validation, Set<ErrorCode> reportedCodes) {
                this.validation = validation;
                this.reportedCodes = reportedCodes;
            }

            /** @param text valor recibido, presente y no en blanco @throws InvalidFieldsException si el dominio lo rechaza */
            void requireValid(String text) { validation.accept(text); }

            /** @param code código del rechazo del dominio @return {@code true} si esta regla lo informa */
            boolean canReport(ErrorCode code) { return reportedCodes.contains(code); }

            private static void rejectControl(String text, boolean allowLineBreaks, FieldError rejection) {
                if (SingleLineText.hasControlCharacter(text, allowLineBreaks)) {
                    throw InvalidFieldsException.of(rejection);
                }
            }
        }
    }
    ```
    `CommandValues.yearMonth` conserva aquí el argumento `false` de `yearOnly`; T-C3 lo quita de la firma y de estas dos líneas. CM-54 y CM-66 agregan después sus constantes al final del `enum` (punto de extensión).
    ```java
    /** Ejecuta la regla del dominio que nombra {@link DomainRule}; cualquier otro rechazo es de otra restricción. */
    public class DomainRuleValidator implements ConstraintValidator<DomainRule, String> {

        private DomainRule.Rule rule;

        @Override
        public void initialize(DomainRule annotation) { this.rule = annotation.value(); }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            try {
                rule.requireValid(value);
                return true;
            } catch (InvalidFieldsException rejection) {
                var error = rejection.getErrors().get(0);
                if (!rule.canReport(error.code())) {
                    return true;
                }
                context.disableDefaultConstraintViolation();
                context.unwrap(HibernateConstraintValidatorContext.class)
                        .withDynamicPayload(error.code())
                        .buildConstraintViolationWithTemplate(literal(error.message()))
                        .addConstraintViolation();
                return false;
            }
            // Cualquier otra excepción no se captura: un invariante que la regla no espera debe salir como falla.
        }

        /** Escapa el texto para que la interpolación del mensaje no lea llaves ni expresiones. */
        private static String literal(String message) {
            return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
        }
    }
    ```
  - Cada anotación: `@Documented @Constraint(validatedBy = …) @Target({FIELD, METHOD, PARAMETER}) @Retention(RUNTIME)`, `message()`, `groups()`, `payload()`, con Javadoc en español.
- **Modificar** `presentation/advice/ApiExceptionHandler.fieldProblems`: si el error del campo trae un código de `@DomainRule`, se usan ese código y su mensaje; si no, la tabla de siempre.
  ```java
  ex.getBindingResult().getFieldErrors().forEach(fe -> {
      var domainCode = domainRuleCode(fe);
      var code = domainCode != null ? domainCode : ErrorCatalog.fieldCode(owner + "." + fe.getField() + "." + fe.getCode());
      var message = domainCode != null ? fe.getDefaultMessage() : ErrorCatalog.fieldMessage(code);
      firstPerField.putIfAbsent(fe.getField(), new FieldProblem(fe.getField(), code, message));
  });

  /** @return el código que una regla del dominio adjuntó a la violación, o {@code null} para cualquier otra restricción */
  private static ErrorCode domainRuleCode(FieldError fe) {
      if (fe.contains(ConstraintViolation.class)
              && fe.unwrap(ConstraintViolation.class) instanceof HibernateConstraintViolation<?> violation) {
          return violation.getDynamicPayload(ErrorCode.class);
      }
      return null;
  }
  ```
- **Modificar** `presentation/advice/ErrorCatalogTest.constraintNames`: en lugar de mirar solo `NotBlank`, `NotNull` y `Size`, agregar el nombre simple de toda anotación del campo que esté anotada con `jakarta.validation.Constraint` (`annotation.annotationType().isAnnotationPresent(Constraint.class)`), **excepto `DomainRule`**, cuyo código no sale de la tabla sino de la regla.
- **Modificar** `application/command/CommandValues.java`: ningún cambio de comportamiento; solo comprobar que `option` y `yearMonth` siguen lanzando `InvalidFieldsException.of(field, code, mensaje)`, que es lo que lee `DomainRuleValidator`.
- **Pruebas** (sin Spring):
  - `CodePointSizeValidatorTest` (llamando `isValid(value, null)`): max 100 → `null` válido; 100 `a` válido; 101 `a` inválido; 99 `a` + `"😀"` válido; 100 `a` + `"😀"` inválido.
  - `SingleLineTextTest.hasControlCharacter_shouldDetectForbiddenCharacters` (parametrizada, escritos con escapes `\uXXXX`): `"Acme\u0000"`, `"Acme\u0007"`, `"A\u007Fcme"`, `"A\u0085"` → `true` en los dos modos; `"a\nb\tc\r"` → `true` sin saltos y `false` con saltos; `"Acme"`, `"Ñandú"` y la familia `"👨‍👩‍👧"` → `false`.
  - `DomainRuleValidatorTest` con un validador real (`Validation.buildDefaultValidatorFactory().getValidator()`) sobre un `record` de prueba por regla (por ejemplo `record CompanyProbe(@DomainRule(Rule.COMPANY) String value) { }`). Para cada violación comprueba el mensaje y `violation.unwrap(HibernateConstraintViolation.class).getDynamicPayload(ErrorCode.class)`:
    - `COMPANY`: `"Acme\u0000"` → `COMPANY_INVALID_CHARACTERS`, «La empresa tiene caracteres no permitidos.»; `"Acme"` sin violación. Lo mismo para `POSITION`, `INSTITUTION` y `DEGREE` con su texto.
    - `DESCRIPTION`: `"a\nb\tc\r"` sin violación; `"Hola\u0000"` → `DESCRIPTION_INVALID_CHARACTERS`, «La descripción tiene caracteres no permitidos.».
    - `EMPLOYMENT_STATUS`: `"CURRENT"`, `"ENDED"`, `"UNKNOWN_END"` sin violación; `"current"`, `"FREELANCE"` → `EMPLOYMENT_STATUS_INVALID_VALUE`, «Selecciona una opción.». `EDUCATION_LEVEL` con `"DOCTORATE"`, igual.
    - `START_DATE` y `END_DATE` válidos: `"2024-01"`, `"0001-01"`, `"9999-12"`; inválidos: `"2024-13"`, `"2024-00"`, `"2024-1"`, `"24-01"`, `"13/2024"`, `"2024"`, `"2024-01-01"`, `"abc"`, `"0000-01"`, `"２０２４-０１"` → `START_DATE_INVALID_FORMAT`/`END_DATE_INVALID_FORMAT`, «Ingresa una fecha válida con el formato mm/aaaa.».
    - Cualquier regla con `null`, `""` y `"   "`: sin violación (los reporta `@NotBlank`).
  - `CommandValuesTest`: sin cambios (debe seguir en verde).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=*ValidatorTest,SingleLineTextTest,CommandValuesTest,ErrorCatalogTest" test`.
- **Detente si** `HibernateConstraintValidatorContext` no está en el classpath (`git grep hibernate-validator pom.xml` y `./mvnw.cmd dependency:tree`): llega con `spring-boot-starter-validation`; si falta, reporta antes de agregar nada.

- **Resultado (9-oct-2026):** rojo: `DomainRuleValidatorTest` y `CodePointSizeValidatorTest` no compilaban (faltaban las restricciones). Verde: `DomainRuleValidatorTest` 32/32, `CodePointSizeValidatorTest` 5/5, `ErrorCatalogTest` 6/6 con la nueva `constraintNames`, `CommandValuesTest` 21/21, `ApiExceptionHandlerTest` 48/48, `ArquitecturaTest` 8/8 (152 pruebas, BUILD SUCCESS). `hasControlCharacter` y `InvalidFieldsException.of(FieldError)` ya existían por T-A1 y T-A2. Se agregó `ApiExceptionHandlerDomainRuleTest` (2 pruebas) porque la tarjeta no probaba el cambio de `fieldProblems` por la ruta HTTP real. Los 5 códigos `*_INVALID_CHARACTERS` se documentan en `docs/errores.md` en T-A7. hibernate-validator llega con `spring-boot-starter-validation` (verificado en `pom.xml`).

## [x] T-A4 · Reloj UTC — ≤ 15 min, ≈ 30 líneas

- **Reutilizar** `infrastructure/config/ClockConfig.java`, que crea CM-279 (bloque P1) con `@Bean Clock clock()` = `Clock.systemUTC()`. **No crear** otra clase de reloj: dos `@Bean Clock` hacen fallar el arranque (`NoUniqueBeanDefinitionException`). Comprueba con `git grep -n "Clock.systemUTC" -- src/main` que existe exactamente uno; si no existe, detente y reporta (falta P1 en la base).
- **Modificar** `ProfileAppService`: nuevo parámetro de constructor `Clock clock` (tercer parámetro) guardado en un campo `final`; aún no se usa en reglas (lo usan T-B4 y T-C4). Actualiza `ProfileAppServiceTest.setUp`: `service = new ProfileAppService(repository, roleRepository, Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest" test`.

- **Resultado (9-oct-2026):** rojo: `ProfileAppServiceTest` no compilaba (el constructor solo recibía dos parámetros). Verde: 28/28 (`./mvnw -B test -Dtest=ProfileAppServiceTest`). Exactamente un `Clock.systemUTC` en `src/main` (el de `ClockConfig`, de CM-279 P1): no se creó otro reloj. El campo `clock` queda sin uso hasta T-B4 y T-C4.

## [x] T-A5 · Bloqueo del perfil en toda escritura — ≤ 30 min, ≈ 120 líneas

- **Cubre:** REQ-EF-05, REQ-EF-06.
- **Modificar** `infrastructure/persistence/repository/ProfessionalProfileJpaRepository.java`:
  ```java
  /**
   * Lee el perfil y bloquea su fila hasta que termine la transacción en curso.
   *
   * @param id identificador del perfil
   * @return el perfil bloqueado, o vacío si no existe
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ProfessionalProfileEntity p where p.id = :id")
  Optional<ProfessionalProfileEntity> findLockedById(@Param("id") UUID id);
  ```
- **Modificar** `domain/port/ProfessionalProfileRepository.java`: `Optional<ProfessionalProfile> findByIdForUpdate(ProfileId id);` con Javadoc («Lee el perfil y bloquea su fila hasta que termine la transacción en curso, para que dos escrituras sobre el mismo perfil se ejecuten una después de la otra. Exige una transacción abierta. @throws ProfileUpdateInProgressException si la fila sigue bloqueada después de la espera máxima»).
- **Modificar** `ProfessionalProfileRepositoryAdapter.java`:
  ```java
  /** Espera máxima por otra escritura sobre el mismo perfil; es una constante, nunca un dato recibido. */
  static final String UPDATE_LOCK_TIMEOUT = "2s";

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<ProfessionalProfile> findByIdForUpdate(ProfileId id) {
      // Una escritura atascada no retiene la conexión de las que esperan: a los 2 s PostgreSQL corta
      // la espera (55P03) y la petición responde 409. El límite rige solo mientras se espera el bloqueo.
      em.createNativeQuery("set local lock_timeout = '" + UPDATE_LOCK_TIMEOUT + "'").executeUpdate();
      Optional<ProfessionalProfileEntity> locked;
      try {
          locked = jpa.findLockedById(id.value());
      } catch (PersistenceException | PessimisticLockingFailureException e) {
          throw isLockNotAvailable(e) ? new ProfileUpdateInProgressException() : e;
      }
      // El límite era para esperar el bloqueo: las demás sentencias de la transacción no lo heredan.
      em.createNativeQuery("set local lock_timeout to default").executeUpdate();
      return locked.map(ProfessionalProfileMapping::toDomain);
  }
  ```
  **Trampa (no usar `finally`):** cuando PostgreSQL corta la espera, la transacción queda abortada y cualquier sentencia siguiente falla con `25P02` («current transaction is aborted»). Un `set local … to default` dentro de un `finally` lanzaría esa segunda excepción, que reemplaza al 409 y termina en el 500 genérico. Por eso el valor por defecto se restablece solo en el camino feliz, igual que en `lockCreationFor`.
  **Trampa:** Spring Data traduce la excepción de JPA a `PessimisticLockingFailureException`/`CannotAcquireLockException` (no a `PersistenceException`), por eso se capturan ambas y se usa `isLockNotAvailable`, que recorre las causas hasta el `SQLException` con estado `55P03`. Si la prueba de T-A7 muestra otra excepción, detenerse y reportar su tipo real.
- **Crear** `domain/exception/ProfileUpdateInProgressException.java` (`ErrorCode.PROFILE_UPDATE_IN_PROGRESS`, mensaje «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.»). Es un 409: se registra como todo rechazo 4xx (`WARN` vía `ApiExceptionHandler.reject`).
- **Modificar** `ErrorCode` (constante `PROFILE_UPDATE_IN_PROGRESS` con Javadoc «Otra escritura retuvo el perfil más que la espera máxima; no cambió nada.») y `ErrorCatalog.RESPONSES`: `entry(PROFILE_UPDATE_IN_PROGRESS, CONFLICT, "Perfil ocupado", null)`.
- **Modificar** `ProfileAppService`: crear
  ```java
  /** Carga el perfil de quien llama bloqueando su fila; todo caso de uso que modifica un perfil empieza aquí. */
  private ProfessionalProfile loadForUserForUpdate(UUID profileId, String uid) { ... }
  ```
  con la misma comprobación de identidad, existencia (404) y dueño (403) que `loadForUser`, pero con `findByIdForUpdate`. Reemplazar `loadForUser` por `loadForUserForUpdate` en los 13 métodos `@Transactional` que modifican (`updateProfileInfo`, `addWorkExperience`, `removeWorkExperience`, `addEducation`, `removeEducation`, `updateSalaryExpectation`, `addSkill`, `removeSkill`, `requestReview`, `addTargetRole`, `updateTargetRole`, `removeTargetRole`, `completeProfile`). `getProfile` sigue con `loadForUser`.
- **Pruebas** `ProfileAppServiceTest`: actualizar los `when(repository.findById(...))` de las escrituras a `findByIdForUpdate`; nueva `writes_shouldLoadProfileForUpdate_whenProfileChanges` (parametrizada por los 13 casos de uso: `verify(repository).findByIdForUpdate(ProfileId.of(PROFILE_ID))` y `verify(repository, never()).findById(any())`); `getProfile_shouldNotLock_whenReading`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,ErrorCatalogTest" test`.

- **Resultado (10-oct-2026):** rojo: `ProfileAppServiceTest` no compilaba (el puerto no tenía `findByIdForUpdate`). Verde: `./mvnw -q -B "-Dtest=ProfileAppServiceTest,ErrorCatalogTest" test` sin fallos. `getProfile` sigue con `loadForUser` (sin bloqueo); los 13 casos de uso que modifican usan `loadForUserForUpdate`. El adaptador captura `PersistenceException` y `PessimisticLockingFailureException`; si la integración de T-A6 muestra otra excepción, se reporta aquí.

## [x] T-A6 · Prueba de integración del bloqueo — ≤ 30 min, ≈ 160 líneas

- **Crear** `infrastructure/persistence/ProfileWriteLockIT.java` con la forma de `ProfileCreationConcurrencyIT` (`@SpringBootTest`, `@Testcontainers`, `@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")`, `ExecutorService`, limpieza de `firebase_uid like 'uid-lock-%'`).
- **Pruebas:**
  - `write_shouldWaitForOtherWrite_whenSameProfileIsLocked`: hilo A abre una transacción (`TransactionTemplate`), llama `repository.findByIdForUpdate(id)` y espera en un `CountDownLatch release`; hilo B llama `profileAppService.updateProfileInfo(...)` con nombre `"Nuevo"`. Afirmar que `futureB.get(500, MILLISECONDS)` lanza `TimeoutException` (B sigue esperando), luego `release.countDown()`, y que B termina en ≤ 10 s y el nombre en la base es `"Nuevo"`.
  - `write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout`: A retiene el bloqueo 4 s (espera en el latch con `await(4, SECONDS)`); B debe terminar con `ProfileUpdateInProgressException` entre 1,5 s y 3,5 s; el perfil no cambió.
  - `read_shouldNotWait_whenProfileIsLocked`: con A reteniendo el bloqueo, `profileAppService.getProfile(...)` responde en < 1 s.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfileWriteLockIT" verify` (Docker encendido).
- **Detente si** B no espera en la primera prueba: el bloqueo no se está tomando.

- **Resultado (10-oct-2026):** verde en la primera corrida: `./mvnw -B "-Dit.test=ProfileWriteLockIT" verify` → Tests run: 5, Failures: 0, Errors: 0 (BUILD SUCCESS). La espera agotada responde `ProfileUpdateInProgressException` entre 1,5 s y 3,5 s con el perfil sin cambios; B espera con A reteniendo la fila (primera prueba) y la lectura no espera. La excepción de Spring Data que llega al adaptador es capturada (`PersistenceException | PessimisticLockingFailureException`); no hizo falta otra. Se agregaron dos pruebas: el bloqueo exige transacción (`IllegalTransactionStateException`) y el límite de espera vuelve a `0` tras tomarlo.

## [x] T-A7 · Catálogo documentado y probado — ≤ 20 min, ≈ 90 líneas

- **Crear** `domain/exception/ErrorCodeDocumentationTest.java`:
  - `everyCode_shouldBeDocumented_whenCatalogIsRead`: lee `docs/errores.md` (ruta relativa a la raíz del módulo, `Path.of("docs", "errores.md")`) y comprueba que cada `ErrorCode.name()` aparece entre comillas invertidas en alguna fila de tabla (línea que empieza con `| \``).
  - `everyCode_shouldBeTested_whenTestSourcesAreScanned`: recorre `src/test/java` y comprueba que cada nombre aparece en al menos un archivo distinto de `ErrorCodeTest.java` y de esta clase.
- **Modificar** `docs/errores.md`: cambiar el libro citado a `09102026_01_Backlog_v6.xlsx`; agregar la fila de `PROFILE_UPDATE_IN_PROGRESS` (409, «todo `PATCH`, `POST` y `DELETE` sobre `/api/v1/profiles/{id}…`», «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.», `ProfileUpdateInProgressException`, `ProfileWriteLockIT.write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout`) y las de los cinco `*_INVALID_CHARACTERS` de T-A3 (422, endpoint, campo, texto, `@DomainRule` y dominio, `DomainRuleValidatorTest`). Si la prueba encuentra códigos ya existentes sin documentar o sin probar, agrégalos (documentación o prueba) y lista cuáles en el PR.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ErrorCodeDocumentationTest" test`.
- **Trampa:** la prueba lee archivos con rutas relativas al módulo; Surefire corre con el directorio del módulo como directorio de trabajo, así que `Path.of("docs", "errores.md")` funciona con `./mvnw.cmd` desde la raíz del repo. Lee con `Files.readString(path, StandardCharsets.UTF_8)`: el archivo tiene tildes.

- **Resultado (10-oct-2026):** rojo: `ErrorCodeDocumentationTest` falló en sus dos pruebas. Sin fila en `docs/errores.md`: `PROFILE_UPDATE_IN_PROGRESS` y los cinco `*_INVALID_CHARACTERS`; sin prueba que los nombre: `PROFILE_UPDATE_IN_PROGRESS`, `WORK_EXPERIENCE_ID_INVALID_FORMAT`, `EDUCATION_ID_INVALID_FORMAT` y `TARGET_ROLE_ID_INVALID_FORMAT` (estos tres ya estaban documentados con una prueba que no los nombraba). Se agregaron las seis filas, `ApiExceptionHandlerTest.profileUpdateInProgress_shouldReturn409_whenThrown` y tres casos de `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected`. Verde: `ErrorCodeDocumentationTest, ApiExceptionHandlerTest, ProfileControllerTest, ErrorCatalogTest, ErrorCodeTest` → 172 pruebas, 0 fallos.

## [x] T-A9 · Lectura de opciones y fechas en el dominio, vigilada por ArchUnit — ≤ 25 min, ≈ 90 líneas

- **Por qué:** CM-271 dejó a esta tarea mover `CommandValues` al dominio y prohibir `Enum.valueOf` y `YearMonth.parse` sueltos en `application` (hallazgo de su sección 11). Convertir un texto en una opción o en un mes es una regla de forma del dominio, y `@DomainRule` (T-A3) la ejecuta desde el borde.
- **Mover** `application/command/CommandValues.java` a `domain/model/FieldValues.java` con los mismos métodos públicos (`option`, `yearMonth` y las constantes de mensaje) y su Javadoc en español (ya lo está). Actualizar las referencias (`git grep -n CommandValues -- src`): `ProfileAppService`, `DomainRule` (T-A3) y `CommandValuesTest` → `domain/model/FieldValuesTest`.
- **Agregar** a `ArquitecturaTest`:
  ```java
  @ArchTest
  static final ArchRule applicationDoesNotParseRawValues = noClasses()
          .that().resideInAPackage("..application..")
          .should().callMethod(YearMonth.class, "parse", CharSequence.class)
          .orShould().callMethodWhere(target(name("valueOf")).and(target(owner(assignableTo(Enum.class)))))
          .because("las opciones y los meses se leen con FieldValues, que los rechaza con su propio código y mensaje");
  ```
  (los imports estáticos de `JavaCall.Predicates.target`, `HasName.Predicates.name`, `HasOwner.Predicates.With.owner` y `JavaClass.Predicates.assignableTo`).
- **Pruebas:** `ArquitecturaTest` en verde; para comprobar que la regla muerde, agrega temporalmente un `EmploymentStatus.valueOf("X")` en `ProfileAppService`, corre la prueba, pega la falla y quítalo.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ArquitecturaTest,FieldValuesTest" test`.
- **Orden:** después de T-A3; si T-A3 aún no está, detente.

- **Resultado (10-oct-2026):** `CommandValues` pasó a `domain/model/FieldValues` y su prueba a `FieldValuesTest` (movidos con `git mv`; referencias en `ProfileAppService`, `DomainRule` y `docs/errores.md`). Nueva regla `ArquitecturaTest.applicationDoesNotParseRawValues`. Verde: `ArquitecturaTest, FieldValuesTest, DomainRuleValidatorTest, ProfileAppServiceTest` → 104 pruebas, 0 fallos. La regla muerde: con un `EmploymentStatus.valueOf("X")` temporal en `ProfileAppService.loadProfile` falló con `Method <ProfileAppService.loadProfile(java.util.UUID)> calls method <EmploymentStatus.valueOf(java.lang.String)> in (ProfileAppService.java:178)`; el temporal se retiró (el archivo ya no contiene `valueOf("X")`).

## [x] T-A10 · Creación del perfil alineada: 2 s y 409 — ≤ 20 min, ≈ 80 líneas

- **Por qué:** decisión de Paula del 9-oct-2026: la escritura que encuentra el perfil ocupado espera como máximo 2 s (presupuesto de DES-02) y responde 409; la creación del perfil hace lo mismo para no tener dos comportamientos.
- **Modificar** `ProfessionalProfileRepositoryAdapter.CREATION_LOCK_TIMEOUT` de `"5s"` a `"2s"` y su comentario.
- **Retirar y agregar (no renombrar):** un código publicado nunca se renombra ni se reutiliza (R3). `PROFILE_CREATION_TIMEOUT` (503) se **retira**: se borran el valor del `enum`, su entrada de `ErrorCatalog` y `ProfileCreationTimeoutException`. Se **agrega** `ErrorCode.PROFILE_CREATION_IN_PROGRESS` (Javadoc: «Otra creación del mismo Usuario sigue en curso; no se creó nada.») con la excepción nueva `domain/exception/ProfileCreationInProgressException` (hereda de `BusinessException`, mismo patrón que la retirada) y el mensaje «Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.». `ErrorCatalog`: `entry(PROFILE_CREATION_IN_PROGRESS, CONFLICT, "Creación en proceso", null)`. `lockCreationFor` lanza la nueva.
- **Modificar** `ProfileController` (ejemplo y `@ApiResponse` del `POST /api/v1/profiles`: 409 en lugar de 503), `docs/errores.md` (fila nueva de `PROFILE_CREATION_IN_PROGRESS` 409; la de `PROFILE_CREATION_TIMEOUT` pasa a la sección «Códigos retirados» con «Ya no se emite desde esta versión; lo reemplaza `PROFILE_CREATION_IN_PROGRESS` (409).») y `docs/adr/0003-bloqueo-de-creacion-de-perfil.md` (2 s y 409, con la razón: DES-02 y que no es un fallo del servicio). `ErrorCodeDocumentationTest` (T-A7) solo exige los valores vivos del `enum`; la sección de retirados no se cruza.
- **Pruebas:** actualizar `ProfileCreationConcurrencyIT` y las pruebas que nombran el código o la excepción (`git grep -n "PROFILE_CREATION_TIMEOUT\|ProfileCreationTimeoutException" -- src docs`); la prueba de la espera agotada pasa a esperar el 409 entre 1,5 s y 3,5 s. Postman: la petición de la creación concurrente, si existe, espera 409.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfileCreationConcurrencyIT" verify` y `./mvnw.cmd -q -B "-Dtest=ErrorCatalogTest,ErrorCodeTest" test`.

- **Resultado (10-oct-2026):** `PROFILE_CREATION_TIMEOUT` (503) se retiró y se agregó `PROFILE_CREATION_IN_PROGRESS` (409) con `ProfileCreationInProgressException`; la espera de la creación pasó de 5 s a 2 s; OpenAPI del `POST /api/v1/profiles` agrupa los dos 409 en una sola respuesta con dos ejemplos (una respuesta no puede repetir su estado); `docs/errores.md` gana la sección «Códigos retirados». Nota de ejecución: el código y sus pruebas se cambiaron en el mismo paso (es un retiro y un reemplazo), así que no se capturó una corrida en rojo; las pruebas que fijaban 503 y 5 s son las que ahora esperan 409 y 1,5 a 3,5 s. Verde: `ErrorCatalogTest, ErrorCodeTest, ErrorCodeDocumentationTest, ProfileControllerTest, ArquitecturaTest` → 132 pruebas, 0 fallos; `ProfileCreationConcurrencyIT` (8) y `OpenApiDocumentIT` (1) en verde. La colección de Postman no tenía la petición de creación concurrente, así que no cambia.

## [x] T-A8 · ADR y cierre del PR 1 — ≤ 30 min

- **Crear** `docs/adr/0005-bloqueo-del-perfil-en-escrituras.md` (en español): contexto (máximos y mínimos que cruzan filas; dos pestañas), decisión (bloqueo pesimista de `perfil_profesional` en toda escritura, espera máxima 2 s, 409 `PROFILE_UPDATE_IN_PROGRESS`), alternativas (`@Version` con reintentos: la perdedora repite la regla y el `save` hace `merge`; bloquear solo al eliminar: no cubre los máximos), consecuencias (las escrituras de un mismo perfil se serializan; las lecturas no esperan).
- **Cierre:** `./mvnw.cmd -B clean verify` en verde; extraer de `target/site/jacoco/jacoco.csv` líneas y ramas de `SingleLineText`, `InvalidFieldsException`, `ProfileUpdateInProgressException`, `CodePointSizeValidator`, `DomainRuleValidator`, `ApiExceptionHandler`, `FieldValues`, `ProfileAppService`, `ProfessionalProfileRepositoryAdapter`, `ErrorCatalog`, `ProfileCreationInProgressException` (≥ 90 % cada una; cada línea o rama sin cubrir con su razón) y la cobertura global (anotarla: es la línea base que los PR siguientes no pueden bajar). Medir el diff con `git diff --stat origin/develop...HEAD` (≤ 1000 líneas agregadas + eliminadas).
- **Terminado:** salida de `clean verify`, tabla de cobertura y tamaño del diff pegados en el PR.

- **Resultado (10-oct-2026):** ADR `0005-bloqueo-del-perfil-en-escrituras.md` creado y ADR 0003 ajustado (2 s y 409). El PR 1 se partió en 1a y 1b (Desvío 12 de la cola): 1a = T-A0 a T-A3 (rama `CM-274-base-escritura-perfil`, 943 líneas contra el PR 0; `clean verify` BUILD SUCCESS, 410 unitarias + 44 IT, 0 fallos); 1b = T-A4 a T-A10 (rama `CM-274-bloqueo-escrituras-perfil`, base 1a; `clean verify` BUILD SUCCESS, 433 unitarias + 49 IT, 0 fallos). Cobertura de 1b (líneas · ramas): `SingleLineText` 18/18 · 32/32; `InvalidFieldsException` 8/8 · 2/2; `ProfileUpdateInProgressException` 2/2; `ProfileCreationInProgressException` 2/2; `CodePointSizeValidator` 4/4 · 4/4; `FieldValues` 14/14 · 12/12; `ProfessionalProfileRepositoryAdapter` 28/28 · 12/12; `ErrorCatalog` 73/73 · 2/2; `ApiExceptionHandler` 64/64 · 27/28; `ProfileAppService` 62/65 · 12/12; `DomainRuleValidator` 17/18 · 5/6. Sin cubrir y su razón: `DomainRuleValidator` línea 28, rama `!rule.canReport(code)` — defensa que hoy no se alcanza porque cada regla solo lanza los códigos que informa (queda como cláusula de seguridad si una regla futura lanza otro); `ApiExceptionHandler` línea 214, una rama de `unwrap(...) instanceof HibernateConstraintViolation` (de la capa de CM-279, no de esta); `ProfileAppService` líneas 102 y 153, 178 (`removeEducation`, `removeTargetRole` y `loadProfile` no tienen prueba unitaria de su camino feliz; la prueba parametrizada de las 13 escrituras solo llega hasta la carga del perfil). Cobertura global del repo: 93,5 % de líneas (1154/1234) y 88,8 % de ramas (300/338): es la línea base que los PR siguientes no bajan. Logs: `ejecucion/verify-CM-274-1a.log` y `ejecucion/verify-CM-274-1b.log`.

---

# PR 2 — Experiencia laboral (`CM-274-experiencia-laboral`, sobre el PR 1)

## [x] T-B1 · Petición de la experiencia: normalización, restricciones y OpenAPI — ≤ 30 min, ≈ 140 líneas

- **Modificar** `presentation/dto/AddWorkExperienceRequest.java`:
  ```java
  /** Cuerpo de POST /api/v1/profiles/{id}/work-experiences; los textos llegan recortados y en NFC. */
  @Schema(description = "Experiencia laboral que se agrega al perfil")
  public record AddWorkExperienceRequest(
          @Schema(description = "Empresa", example = "Bancolombia", requiredMode = REQUIRED, minLength = 1, maxLength = 100)
          @NotBlank @CodePointSize(max = 100) @DomainRule(Rule.COMPANY) String company,
          @Schema(description = "Cargo", example = "Desarrolladora Backend", requiredMode = REQUIRED, minLength = 1, maxLength = 100)
          @NotBlank @CodePointSize(max = 100) @DomainRule(Rule.POSITION) String position,
          @Schema(description = "Descripción opcional; vacía se guarda como null", example = "APIs REST con Spring Boot", maxLength = 500, nullable = true)
          @CodePointSize(max = 500) @DomainRule(Rule.DESCRIPTION) String description,
          @Schema(description = "Mes de inicio, opcional", example = "2024-06", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true)
          @DomainRule(Rule.START_DATE) String startDate,
          @Schema(description = "Mes de fin; obligatorio solo con ENDED", example = "2024-12", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", nullable = true)
          @DomainRule(Rule.END_DATE) String endDate,
          @Schema(description = "Estado de la experiencia", example = "ENDED", requiredMode = REQUIRED, allowableValues = {"CURRENT", "ENDED", "UNKNOWN_END"})
          @NotBlank @DomainRule(Rule.EMPLOYMENT_STATUS) String employmentStatus) {
      // Sin procedencia: todo lo que se agrega por esta ruta es MANUAL; lo que escribe la IA usa sus propias rutas.

      /** Normaliza antes de que corra Bean Validation, para que los límites se midan sobre lo que se guardará. */
      public AddWorkExperienceRequest {
          company = SingleLineText.normalize(company);
          position = SingleLineText.normalize(position);
          description = SingleLineText.normalizeOptional(description); // recorta los extremos; los saltos internos se conservan
          startDate = SingleLineText.normalizeOptional(startDate);
          endDate = SingleLineText.normalizeOptional(endDate);
          employmentStatus = SingleLineText.normalize(employmentStatus);
      }
  }
  ```
  `normalizeOptional` recorta los extremos de la descripción (incluidos saltos de línea al principio o al final) y conserva los internos.
- **Modificar** `ErrorCatalog`:
  - `FIELD_CODES`: quitar `AddWorkExperienceRequest.startDate.NotBlank`, `…employmentStatus.NotNull`, `…provenance.NotNull` y toda clave de `provenance`; agregar `…company.CodePointSize`→`COMPANY_TOO_LONG`, `…position.CodePointSize`→`POSITION_TOO_LONG`, `…description.CodePointSize`→`DESCRIPTION_TOO_LONG`, `…employmentStatus.NotBlank`→`EMPLOYMENT_STATUS_REQUIRED`. Los campos con `@DomainRule` no llevan clave: su código viaja en la violación (T-A3).
  - `FIELD_MESSAGES`: `COMPANY_TOO_LONG` «La empresa no puede superar los 100 caracteres.», `POSITION_TOO_LONG` «El cargo no puede superar los 100 caracteres.», `DESCRIPTION_TOO_LONG` «La descripción no puede superar los 500 caracteres.», `EMPLOYMENT_STATUS_REQUIRED` «Selecciona una opción.». Los mensajes de fecha, opción y caracteres no van aquí: salen del dominio y de `FieldValues`.
  - En `ErrorCatalogTest.DOMAIN_FIELD_CODES` quita los códigos que pasaron a `FIELD_MESSAGES` (la prueba exige que cada código esté en un solo grupo).
  - Agregar `ErrorCode` `START_DATE_IN_THE_FUTURE`, `END_DATE_IN_THE_FUTURE`, `WORK_EXPERIENCE_LIMIT_REACHED`, con Javadoc en español (los `*_INVALID_CHARACTERS` ya los creó T-A3).
- **Pruebas** en `ProfileControllerTest` (estándar `standaloneSetup` del archivo):
  - `addWorkExperience_shouldReturnRequired_whenTextIsMissingOrBlank` (parametrizada): para `company` y `position`, cuerpos con el campo ausente, `null`, `""`, `"   "`, `"\t\n"`, `" "` → 422, `$.code` `VALIDATION_FAILED`, `$.detail` «Revisa los campos marcados.», `$.errors[0].field` el campo, `code` `COMPANY_REQUIRED`/`POSITION_REQUIRED`, `message` «Ingresa la empresa.»/«Ingresa el cargo.».
  - `addWorkExperience_shouldReturnTooLong_whenTextExceedsLimit`: `company` 101 `a`; `company` 100 `a` + `"😀"`; `position` 101; `description` 501; `description` 499 `a` + `"😀😀"` → código y texto de esta tarjeta.
  - `addWorkExperience_shouldReturnDateInvalidFormat_whenDateIsMalformed`: `startDate` y `endDate` con cada valor inválido de `DomainRuleValidatorTest` (T-A3); código y mensaje comprobados en `errors[0]`.
  - `addWorkExperience_shouldReturnStatusInvalid_whenStatusIsUnknown`: `FREELANCE`, `current`, `Current` → `EMPLOYMENT_STATUS_INVALID_VALUE`; `""` → `EMPLOYMENT_STATUS_REQUIRED`.
  - `addWorkExperience_shouldSaveManual_whenBodyDeclaresAiProvenance`: cuerpo válido con `"provenance":"AI_SUGGESTED"` → el comando llega sin procedencia y la respuesta trae `MANUAL`.
  - `addWorkExperience_shouldReturnEveryInvalidField_whenSeveralFail`: `company` `""`, `position` 101 `a`, `startDate` `"2024-13"` → tres elementos (`containsExactlyInAnyOrder` sobre `field`).
  - `addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul`: `"Acme\u0000"` → `COMPANY_INVALID_CHARACTERS`, «La empresa tiene caracteres no permitidos.»; y `addWorkExperience_shouldKeepInnerLineBreaks_whenDescriptionHasThem`: `"\nLínea 1\nLínea 2\n"` llega al servicio como `"Línea 1\nLínea 2"`.
  - `addWorkExperience_shouldTreatNumberAsText_whenCompanyIsNumber` (V1): `"company": 123` → la petición llega al servicio con `"123"` (verificar con el mock); si Jackson la rechaza, ajustar la prueba a 422 `REQUEST_BODY_INVALID_FORMAT` y reportarlo.
  - Cada caso comprueba `Content-Type` `application/problem+json;charset=UTF-8` y que el cuerpo no contiene `"trace"`, `"exception"` ni `co.edu`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ErrorCatalogTest" test`.

## [x] T-B2 · `WorkExperienceDraft`, `DateBounds` y fábricas de la experiencia — ≤ 30 min, ≈ 150 líneas

- **Crear** `domain/model/WorkExperienceDraft.java`:
  ```java
  /**
   * Datos recibidos para crear una experiencia laboral, ya convertidos a tipos del dominio. También
   * es la forma en que se reconstruye una fila guardada.
   *
   * @param company          empresa; puede ser {@code null} o en blanco (se informa como requerida)
   * @param position         cargo; puede ser {@code null} o en blanco
   * @param description      descripción opcional
   * @param startDate        mes de inicio opcional
   * @param endDate          mes de fin opcional
   * @param employmentStatus estado; puede ser {@code null} (se informa como requerido)
   */
  public record WorkExperienceDraft(String company, String position, String description,
          YearMonth startDate, YearMonth endDate, EmploymentStatus employmentStatus) { }
  ```
- **Crear** `domain/model/DateBounds.java`:
  ```java
  /**
   * Meses que limitan las fechas de un registro de experiencia o formación.
   *
   * @param currentMonth mes actual en UTC; ninguna fecha puede ser posterior
   * @param birthMonth   mes de nacimiento del dueño, o {@code null} si el registro no trae fechas
   */
  public record DateBounds(YearMonth currentMonth, YearMonth birthMonth) {
      public DateBounds { Objects.requireNonNull(currentMonth, "currentMonth"); }
  }
  ```
- **Modificar** `domain/model/WorkExperience.java`:
  - Constantes `public static final int MAX_COMPANY_LENGTH = 100, MAX_POSITION_LENGTH = 100, MAX_DESCRIPTION_LENGTH = 500;` (quitar `MAX_TEXT_LENGTH` y el viejo `MAX_DESCRIPTION_LENGTH = 2000`).
  - Constructor `private`, sin validación de reglas (solo `requireNonNull` de `id`, `company`, `position`, `employmentStatus`, `provenance`).
  - `public static WorkExperience create(WorkExperienceDraft draft, DateBounds bounds)`: normaliza `company`, `position` con `SingleLineText.normalize` y `description` con `normalizeOptional`; con un `InvalidFieldsException.Collector`, en este orden: `requireText(company)`, `requireText(position)`, `checkDescription`, `checkStatus`, `checkProvenance`, `checkStartDate`, `checkEndDate`; `collector.throwIfAny()`; devuelve `new WorkExperience(UUID.randomUUID(), …)`.
  - Reglas (cada una en un método privado corto que recibe el `Collector`):
    - texto obligatorio: `null`/vacío → `*_REQUIRED` con «Ingresa la empresa.»/«Ingresa el cargo.»; carácter de control (`SingleLineText.hasControlCharacter(texto, false)`; `true` para la descripción) → `collector.add(COMPANY_CHARACTERS)` / `POSITION_CHARACTERS` / `DESCRIPTION_CHARACTERS` (constantes de T-A3); largo → `*_TOO_LONG` con el texto de T-B1.
    - `employmentStatus == null` → `EMPLOYMENT_STATUS_REQUIRED` «Selecciona una opción.». La procedencia no viene en el borrador: `create` asigna `DataProvenance.MANUAL` (Javadoc: «Lo que se agrega por las rutas manuales es siempre MANUAL; lo que escribe la IA se crea con su propia fábrica.»).
    - `startDate`: si `startDate.isAfter(bounds.currentMonth())` → `START_DATE_IN_THE_FUTURE` «La fecha no puede ser posterior al mes actual.».
    - `endDate`, en orden y deteniéndose en el primero: presente con `CURRENT`/`UNKNOWN_END` → `END_DATE_NOT_ALLOWED` «La fecha de fin debe quedar vacía.»; ausente con `ENDED` → `END_DATE_REQUIRED` «Ingresa la fecha de fin.»; posterior al mes actual → `END_DATE_IN_THE_FUTURE`; anterior a `startDate` (si hay inicio) → `END_DATE_BEFORE_START_DATE` «La fecha de fin no puede ser anterior a la de inicio.». Con `employmentStatus == null` no se evalúa la regla de estado.
  - `public static WorkExperience rebuild(UUID id, WorkExperienceDraft stored, DataProvenance provenance)` — tres parámetros: reutiliza el borrador como forma de la fila guardada. Javadoc: «Reconstruye una experiencia guardada sin aplicar reglas: lo guardado ya fue válido con las reglas de su momento.»
  - Comentario de bloque sobre `create`: «Corren todas las reglas y se informan a la vez todos los campos rechazados; en cada campo gana la primera regla que falla, en el orden en que los criterios de aceptación las listan.»
  - Elimina `FieldRules` si ya nadie lo usa tras T-C2 (no en esta tarjeta: `Education` aún lo usa).
- **Pruebas** `domain/model/WorkExperienceTest.java` (reescribir las existentes con `create`; reloj del mes actual `YearMonth.of(2026, 10)`):
  - `create_shouldAccept_whenStartIsCurrentMonth` (`2026-10`), `create_shouldAccept_whenEndIsCurrentMonth` (inicio `2025-01`, fin `2026-10`, `ENDED`), `create_shouldAccept_whenEndEqualsStart` (`2024-05`/`2024-05`).
  - `create_shouldRejectStartInTheFuture_whenStartIsNextMonth` (`2026-11`), `create_shouldRejectEndInTheFuture_whenEndIsNextMonth` (`2026-11`, `ENDED`).
  - `create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier` (`2024-05`/`2024-04`; y `2025-01`/`2024-12`).
  - `create_shouldRejectEndDateRequired_whenEndedWithoutEndDate`.
  - `create_shouldRejectEndDateNotAllowed_whenStatusIsNotEnded` (`CURRENT` y `UNKNOWN_END` con fin `2026-09`).
  - `create_shouldAcceptWithoutDates_whenUnknownEnd` (`startDate` y `endDate` `null`, `description` `null`).
  - `create_shouldAcceptEndWithoutStart_whenEnded` (sin inicio, fin `2023-03`).
  - `create_shouldReportEveryField_whenSeveralRulesFail`: `company` `""`, `position` 101 `a`, `startDate` `2026-11` → tres errores, uno por campo.
  - `create_shouldReportFirstRuleOnly_whenEndDateBreaksTwo`: `CURRENT`, fin `2026-11` → solo `END_DATE_NOT_ALLOWED`.
  - `create_shouldNormalizeText_whenTextHasSpacesAndDecomposedLetters`: `"  Acme  "` → `"Acme"`; `"é"` × 100 → aceptado, 100 puntos de código.
  - `create_shouldStoreNullDescription_whenDescriptionIsBlank` (`"   "`).
  - `rebuild_shouldNotValidate_whenStoredTextIsLongerThanLimit`: empresa de 150 → se reconstruye sin excepción.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=WorkExperienceTest" test`.

## [x] T-B3 · Servicio y comando de la experiencia — ≤ 25 min, ≈ 90 líneas

- **Modificar** `application/command/AddWorkExperienceCommand.java`: el constructor compacto solo hace `Objects.requireNonNull(profileId, "profileId")`; quitar las demás validaciones (las hace el dominio con su código).
- **No modificar** `domain/model/FieldValues.java` (lo movió T-A9 desde `CommandValues`): `yearMonth` conserva aquí `yearOnly` y se pasa `false`; T-C3 lo quita. No se agrega ningún método nuevo: el estado ausente se resuelve en el servicio (abajo), para no crear un método de 4 parámetros.
- **Modificar** `ProfileAppService.addWorkExperience`:
  ```java
  @Transactional
  public ProfessionalProfile addWorkExperience(AddWorkExperienceCommand cmd) {
      var profile = loadForUserForUpdate(cmd.profileId(), cmd.uid());
      var draft = new WorkExperienceDraft(cmd.company(), cmd.position(), cmd.description(),
              startDate(cmd.startDate(), false), endDate(cmd.endDate(), false),
              employmentStatus(cmd.employmentStatus()));
      profile.addWorkExperience(WorkExperience.create(draft, dateBounds()));
      repository.save(profile);
      return profile;
  }

  /** Mes actual en UTC; el mes de nacimiento se suma cuando exista la réplica de la fecha de nacimiento. */
  private DateBounds dateBounds() { return new DateBounds(YearMonth.now(clock), null); }

  /** Estado ausente → {@code null}, que el dominio informa como requerido; un valor desconocido se rechaza aquí con su código. */
  private static EmploymentStatus employmentStatus(String value) {
      return value == null ? null : FieldValues.option(EmploymentStatus.class, value, EMPLOYMENT_STATUS_INVALID_VALUE, "employmentStatus");
  }
  ```
- **Pruebas** `ProfileAppServiceTest`:
  - `addWorkExperience_shouldThrowFieldErrors_whenCommandHasNullRequiredFields`: comando con `company`, `position`, `employmentStatus` `null` → `InvalidFieldsException` con los tres campos; `verify(repository, never()).save(any())`.
  - `addWorkExperience_shouldUseUtcClockMonth_whenStartIsCurrentMonth`: reloj `2026-10-31T23:59:59Z`, inicio `2026-10` → se guarda; reloj `2026-10-31T23:59:59Z`, inicio `2026-11` → `START_DATE_IN_THE_FUTURE`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,FieldValuesTest" test`.

## [x] T-B4 · Máximo de 4 experiencias — ≤ 15 min, ≈ 70 líneas

- **Crear** `domain/exception/WorkExperienceLimitReachedException.java` (`WORK_EXPERIENCE_LIMIT_REACHED`, «Ya tienes el máximo de 4 experiencias laborales.»); `ErrorCatalog.RESPONSES`: `entry(WORK_EXPERIENCE_LIMIT_REACHED, CONFLICT, "Máximo de experiencias alcanzado", null)`.
- **Modificar** `ProfessionalProfile`: `public static final int MAX_WORK_EXPERIENCES = 4;` y en `addWorkExperience`: `if (workExperiences.size() >= MAX_WORK_EXPERIENCES) throw new WorkExperienceLimitReachedException();` antes de agregar. Javadoc en español del método.
- **Pruebas** `ProfessionalProfileTest`: `addWorkExperience_shouldAcceptFourth_whenProfileHasThree`; `addWorkExperience_shouldRejectFifth_whenProfileHasFour` (y la lista sigue con 4); `removeWorkExperience_shouldRemoveOnly_whenCompletedProfileHasOne` (perfil `COMPLETED` con 1 experiencia → se elimina, estado sigue `COMPLETED`). `ProfileControllerTest.addWorkExperience_shouldReturn409_whenLimitReached`: 409, `code` `WORK_EXPERIENCE_LIMIT_REACHED`, `detail` literal, sin `errors`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalProfileTest,ProfileControllerTest" test`.

## [x] T-B5 · Migración V6, entidad, mapeo y respuesta — ≤ 30 min, ≈ 150 líneas

- **Comprobar:** `git ls-tree --name-only origin/develop src/main/resources/db/migration/` termina en `V5__replica_fecha_nacimiento.sql` (de CM-279). Si falta ese archivo o ya existe un V6, detente y reporta (el número es fijo por el plan de ejecución; no se toma «el siguiente libre»).
- **Crear** `V6__alinear_experiencia_laboral.sql` con el texto exacto siguiente:
  ```sql
  ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_pkey TO pk_experiencia_laboral;
  ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_perfil_id_fkey TO fk_experiencia_laboral_perfil;
  ALTER TABLE experiencia_laboral ALTER COLUMN empresa TYPE VARCHAR(100);
  ALTER TABLE experiencia_laboral ALTER COLUMN cargo TYPE VARCHAR(100);
  ALTER TABLE experiencia_laboral ALTER COLUMN descripcion TYPE VARCHAR(500);
  ALTER TABLE experiencia_laboral ALTER COLUMN fecha_inicio DROP NOT NULL;
  ALTER TABLE experiencia_laboral
      ADD CONSTRAINT ck_experiencia_laboral_empresa_presente CHECK (length(btrim(empresa)) > 0),
      ADD CONSTRAINT ck_experiencia_laboral_cargo_presente CHECK (length(btrim(cargo)) > 0),
      ADD CONSTRAINT ck_experiencia_laboral_estado_empleo CHECK (estado_empleo IN ('CURRENT', 'ENDED', 'UNKNOWN_END')),
      ADD CONSTRAINT ck_experiencia_laboral_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
      ADD CONSTRAINT ck_experiencia_laboral_fin_segun_estado CHECK ((estado_empleo = 'ENDED') = (fecha_fin IS NOT NULL)),
      ADD CONSTRAINT ck_experiencia_laboral_fechas CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio),
      ADD CONSTRAINT ck_experiencia_laboral_primer_dia
          CHECK ((fecha_inicio IS NULL OR extract(day FROM fecha_inicio) = 1)
             AND (fecha_fin IS NULL OR extract(day FROM fecha_fin) = 1));
  CREATE INDEX ix_experiencia_laboral_perfil_id ON experiencia_laboral (perfil_id);
  ```
  (Un comentario SQL de cabecera en español explica el propósito: «Alinea experiencia_laboral con las reglas de HU-2.4: largos de 100/100/500, inicio opcional, restricciones con nombre e índice de la clave foránea.»)
- **Modificar** `WorkExperienceEntity`: `@Column(name = "empresa", nullable = false, length = 100)`, `cargo` 100, `descripcion` 500, `fecha_inicio` sin `nullable = false`.
- **Modificar** `ProfessionalProfileMapping`: `toWorkExpEntity` con `d.getStartDate() != null ? toFirstDayOfMonth(...) : null`; `toWorkExpDomain` con `WorkExperience.rebuild(id, new WorkExperienceDraft(...), provenance)` y `null` ↔ `null` en `startDate`.
- **Modificar** `ProfileResponse.WorkExperienceItem.from`: `startDate` `null` si no hay; agregar `@Schema` en cada componente de `WorkExperienceItem` (descripción, ejemplo, `nullable` en `description`, `startDate`, `endDate`).
- **Trampa:** `ProfessionalProfileRepositoryAdapterIT` y `ProfileValueRulesTest` construyen experiencias con el constructor viejo: cámbialos a `WorkExperience.create(...)` o `rebuild(...)` según lo que prueben.
- **Comandos:** `./mvnw.cmd -B test` (todo en verde).

- **Resultado (10-oct-2026, PR 2a):** El PR 2 se partió en 2a (T-B1 a T-B5, rama `CM-274-experiencia-laboral`) y 2b (T-B6 y T-B7: integración, OpenAPI y cierre) por tamaño (Desvío 17 de la cola). Rojo previo no capturado: el código se escribió antes que las pruebas (precedente de CM-70). Verde: `clean verify` BUILD SUCCESS, 542 unitarias + 125 IT, 0 fallos (`ejecucion/verify-CM-274-2a.log`); las 125 IT existentes pasan contra V6. Newman contra la imagen empaquetada con base limpia: 111 peticiones, 0 fallidas, 361 aserciones (`ejecucion/newman-CM-274-2a.log`); dos peticiones de Postman existentes cambian de límite (empresa 101 en lugar de 501 y descripción 501 en lugar de 2001) porque el contrato cambió. Adversarial sin 5xx (`ejecucion/adversarial-CM-274-2a.log`): carácter nulo → 422, JSON mal formado → 422, 1 MiB de descripción → 422, texto con marcado se guarda literal, campos extra e `id` ignorados, procedencia `AI_SUGGESTED` del cuerpo → `MANUAL`, sin tipo de contenido → 415, quinta experiencia → 409. Cobertura: `WorkExperience` 67/69 y 38/40; `WorkExperienceDraft` 1/1; `DateBounds` 3/3; `ProfessionalProfile` 119/121 y 47/48; `WorkExperienceLimitReachedException` 2/2; `FieldValues` 14/14 y 12/12; `ProfileAppService` 68/70 y 14/14; `ProfessionalProfileMapping` 100/100 y 24/26; `AddWorkExperienceRequest` 8/8; `ErrorCatalog` 77/77 y 2/2; global 96,7 % líneas y 94,4 % ramas. Pendiente de cobertura: `ProfileResponse.WorkExperienceItem` 2/4 ramas (inicio `null`), que cubre 2b. Decisiones: `DESCRIPTION_TOO_LONG`, `COMPANY_TOO_LONG` y `POSITION_TOO_LONG` pasan al catálogo de campos del borde (`@CodePointSize`) y siguen también en el dominio; `ErrorCatalogTest` mantiene cada código en un solo grupo.

## [x] T-B6 · Pruebas de integración de la experiencia — ≤ 30 min, ≈ 220 líneas

- **Crear** `presentation/WorkHistoryEndToEndIT.java`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, contenedor `postgres:16-alpine` con `@ServiceConnection`, `@TestConfiguration` interna con `@Bean @Primary Clock fixedClock()` = `Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)`, cliente HTTP `TestRestTemplate`. Cada prueba crea su Usuario `uid-e2e-<UUID>` y su perfil con `POST /api/v1/profiles`.
  - `addWorkExperience_shouldReturn201WithNullEndDate_whenCurrent`: `CURRENT`, inicio `2024-06` → 201, `workExperiences[0].endDate` `null`, `employmentStatus` `CURRENT`, `provenance` `MANUAL`.
  - `addWorkExperience_shouldReturn201WithoutDates_whenUnknownEndHasNoDates`: → 201 con `startDate`, `endDate` y `description` `null`; un segundo cuerpo con inicio `2024-06` → `startDate` `"2024-06"`, `endDate` `null`.
  - `getProfile_shouldReturnNullStartDate_whenExperienceHasNoStart` (`GET` tras la anterior).
  - `addWorkExperience_shouldAcceptOverlap_whenPeriodsOverlap`: terminada `2023-03`–`2024-12` y actual desde `2024-06` → 201 y 2 experiencias.
  - `addWorkExperience_shouldAcceptIdenticalRecords_whenSentTwice` → 2 experiencias.
  - `addWorkExperience_shouldIgnoreUnexpectedFields_whenBodyHasThem`: cuerpo válido + `"id":"…","profileId":"…","status":"COMPLETED","firebaseUid":"otro","seniority":"JUNIOR"` → 201; el perfil sigue `IN_PROGRESS` y del Usuario del encabezado.
  - `addWorkExperience_shouldReturnTextLiterally_whenTextHasMarkup`: `description` `"<script>alert(1)</script>"` → la respuesta y la base guardan exactamente ese texto.
  - `addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul` → 422, no 500.
  - `addWorkExperience_shouldReturn422_whenDescriptionIsOneMebibyte`: 1 048 576 `a` → 422 `DESCRIPTION_TOO_LONG`.
  - `addWorkExperience_shouldAcceptNovember_whenUtcIsAlreadyNovember`: en una clase anidada o un `@TestConfiguration` aparte con reloj `2026-11-01T01:00:00Z`, inicio `2026-11` → 201 (RT-07-CA04).
  - `removeWorkExperience_shouldReturn204AndKeepActive_whenOnlyExperienceOfActiveProfile`: perfil completo con 1 experiencia, finalizar, eliminar → 204 y `GET` muestra `status` `COMPLETED` y 0 experiencias.
  - `removeWorkExperience_shouldKeepOtherProfileExperience_whenIdBelongsToAnotherProfile`: experiencia del Usuario B, `DELETE` desde el perfil de A con ese id → 404 `WORK_EXPERIENCE_NOT_FOUND`; la experiencia de B sigue en la base.
  - `workHistory_shouldNotChangeOtherProfile_whenUserIsNotOwner`: `POST` y `DELETE` de experiencia sobre el perfil de B con identidad de A → 403 y el perfil de B sin cambios.
  - `removeWorkExperience_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02): perfil con 2 experiencias E1 y E2; 20 `DELETE` de E1 en secuencia → el primero 204 y los diecinueve siguientes 404 `WORK_EXPERIENCE_NOT_FOUND`; al final el perfil tiene solo E2, mismo `status` y misma cantidad de las demás listas.
- **Crear** `infrastructure/persistence/WorkHistorySchemaIT.java`:
  - `migration_shouldKeepExistingRow_whenAppliedOnDataFromV4`: con Flyway `target("4")` sobre un contenedor propio, insertar una experiencia válida (`fecha_inicio` `2023-06-01`, `ENDED`, `fecha_fin` `2024-12-01`), migrar al final y comprobar que la fila sigue igual.
  - `constraint_shouldReject_whenRowBreaksIt` (parametrizada): un `INSERT` por JDBC por cada `ck_experiencia_laboral_*` (empresa `'  '`; estado `'FREELANCE'`; procedencia `'HUMAN'`; `ENDED` sin fin; `CURRENT` con fin; fin `2024-04-01` con inicio `2024-05-01`; inicio `2024-05-15`) → `SQLState` `23514` y el nombre de la restricción en el mensaje.
  - `constraints_shouldHaveStandardNames_whenSchemaIsMigrated`: `pg_constraint` contiene `pk_experiencia_laboral` y `fk_experiencia_laboral_perfil`; `pg_indexes` contiene `ix_experiencia_laboral_perfil_id`.
- **Crear** `infrastructure/persistence/ColumnLengthConsistencyIT.java`: `columnLengths_shouldMatchDomainLimits_whenSchemaIsMigrated` compara `information_schema.columns.character_maximum_length` de `empresa`, `cargo`, `descripcion` con `WorkExperience.MAX_COMPANY_LENGTH`, `MAX_POSITION_LENGTH`, `MAX_DESCRIPTION_LENGTH`.
- **Crear** `presentation/advice/FieldMessagesConsistencyTest.java`: `domainMessage_shouldMatchCatalog_whenSameCodeIsRaisedByBothLayers`: provoca en el dominio `COMPANY_REQUIRED`, `COMPANY_TOO_LONG`, `POSITION_REQUIRED`, `POSITION_TOO_LONG`, `DESCRIPTION_TOO_LONG`, `EMPLOYMENT_STATUS_REQUIRED` y compara cada mensaje con `ErrorCatalog.FIELD_MESSAGES`.
- **Agregar** a `ProfileWriteLockIT`: `addWorkExperience_shouldKeepFour_whenTwoRequestsArriveWithThree`: perfil con 3; hilo A en `TransactionTemplate` llama `addWorkExperience` y espera un latch antes de confirmar; hilo B llama `addWorkExperience`; soltar A → una termina bien y la otra con `WorkExperienceLimitReachedException`; la base tiene 4.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=WorkHistoryEndToEndIT,WorkHistorySchemaIT,ColumnLengthConsistencyIT,ProfileWriteLockIT" verify`.

## [x] T-B7 · OpenAPI, catálogo y cierre del PR 2 — ≤ 30 min

- **Modificar** `ProfileController` (`addWorkExperience`, `removeWorkExperience`): `@ApiResponse` para 201/204, 401, 403, 404, 409 (`WORK_EXPERIENCE_LIMIT_REACHED`), 415, 422 (lista completa de códigos de la experiencia), 409 (`PROFILE_UPDATE_IN_PROGRESS`, perfil ocupado), cada uno con `ApiErrorResponse` y un ejemplo con `code`.
- **Modificar** `OpenApiDocumentIT`: `addWorkExperienceRequest_shouldDocumentLimits_whenApiDocsAreGenerated` comprueba `maxLength` 100, 100, 500, el `pattern` de las fechas y `allowableValues` de `employmentStatus`.
- **Modificar** `docs/errores.md`: filas nuevas y textos cambiados de la experiencia (spec 8.1 y 8.2).
- **Cierre:** `./mvnw.cmd -B clean verify`; cobertura de `WorkExperience`, `WorkExperienceDraft`, `DateBounds`, `ProfessionalProfile`, `WorkExperienceLimitReachedException`, `FieldValues`, `ProfileAppService`, `ProfessionalProfileMapping`, `ProfileResponse`, `AddWorkExperienceRequest`, `ErrorCatalog` (≥ 90 % cada una) y global (no baja de la del PR 1); diff ≤ 1000.

- **Resultado (10-oct-2026, PR 2b):** `WorkHistoryEndToEndIT` 13 pruebas, `WorkHistoryNovemberUtcIT` 1, `WorkHistorySchemaIT` 11 (migración desde V4 con datos, 8 restricciones, ausencia de fechas, nombres estándar), `ColumnLengthConsistencyIT` 1, `ProfileWriteLockIT.addWorkExperience_shouldKeepFour_whenTwoRequestsArriveWithThree`, `OpenApiDocumentIT.addWorkExperienceRequest_shouldDocumentLimits_whenApiDocsAreGenerated` y `FieldMessagesConsistencyTest`. `clean verify` BUILD SUCCESS: 543 unitarias + 153 IT, 0 fallos (`ejecucion/verify-CM-274-2b.log`). Que pueden fallar: con el máximo en 5 fallan la prueba de carrera; sin la rama de inicio nulo en el mapeo fallan tres de la E2E (cambios revertidos). Cobertura: `ProfileResponse.WorkExperienceItem` 7/7 y 4/4; `ProfessionalProfileMapping` 100/100 y 26/26; `WorkExperience` 67/69 y 38/40; `ProfessionalProfile` 119/121 y 47/48; `ProfileAppService` 68/70 y 14/14; `ErrorCatalog` 77/77 y 2/2; global 96,8 % líneas y 95,4 % ramas. Líneas sin cubrir de `WorkExperience` (2) y de `ProfessionalProfile` (2): ramas de defensa de `requireNonNull` en el constructor privado y de `reconstitute`, previas. Newman no se repite en 2b: no cambia ninguna ruta ni campo respecto de 2a (solo anotaciones de OpenAPI y pruebas). La numeración de la sección 10 de la spec («Verificación previa en staging» de V6) la ejecuta Paula antes de desplegar.

---

# PR 3 — Formación académica (`CM-274-formacion-academica`, sobre el PR 2)

## [x] T-C1 · Petición de la formación — ≤ 30 min, ≈ 120 líneas

- **Modificar** `AddEducationRequest` con el mismo patrón de T-B1:
  - `institution`, `degree`: `@NotBlank @CodePointSize(max = 150) @DomainRule(Rule.INSTITUTION)` / `@DomainRule(Rule.DEGREE)`, `@Schema(requiredMode = REQUIRED, minLength = 1, maxLength = 150)`.
  - `level`: `@NotBlank @DomainRule(Rule.EDUCATION_LEVEL)`, `allowableValues = {"TECHNICAL", "UNDERGRADUATE", "POSTGRADUATE"}`.
  - `startDate`: `@NotBlank @DomainRule(Rule.START_DATE)`; `endDate`: `@DomainRule(Rule.END_DATE)` (opcional).
  - `inProgress`: `Boolean`, `@Schema(description = "En curso; ausente o null cuenta como false", nullable = true)`.
  - `provenance`: se quita del `record` (se guarda `MANUAL`; decisión de Paula del 9-oct-2026).
  - `fieldOfStudy`: se quita del `record`; si llega, Jackson lo ignora como campo desconocido (`spring.jackson` no falla con propiedades desconocidas: compruébalo con la prueba `addEducation_shouldIgnoreFieldOfStudy_whenSent` de T-C6).
  - Constructor compacto: `normalize` en los obligatorios y `normalizeOptional` en `endDate`.
- **Modificar** `ErrorCatalog`: claves `AddEducationRequest.institution.CodePointSize`→`INSTITUTION_TOO_LONG`, `…degree.CodePointSize`→`DEGREE_TOO_LONG`; quitar las claves de `fieldOfStudy`; mensajes `INSTITUTION_TOO_LONG` «La institución no puede superar los 150 caracteres.», `DEGREE_TOO_LONG` «El título obtenido no puede superar los 150 caracteres.». Los campos con `@DomainRule` no llevan clave. Ajustar `ErrorCatalogTest.DOMAIN_FIELD_CODES`. Agregar `ErrorCode` `EDUCATION_LIMIT_REACHED`, `EDUCATION_NOT_ALLOWED`; **eliminar** `FIELD_OF_STUDY_TOO_LONG` (y su uso en `ErrorCatalogTest` y `ProfileValueRulesTest`).
- **Pruebas** `ProfileControllerTest`: `addEducation_shouldReturnAllRequiredFields_whenAllAreMissing` (cuerpo `{}` → cuatro errores: `institution` «Ingresa la institución.», `degree` «Ingresa el título obtenido.», `level` «Elige un nivel educativo.», `startDate` «Ingresa la fecha de inicio.»); `addEducation_shouldReturnRequired_whenTextIsMissingOrBlank` (los seis valores de presencia); `addEducation_shouldReturnTooLong_whenTextExceedsLimit` (151; 149 + `"😀😀"`); `addEducation_shouldAccept_whenTextIsAtLimit` (150 y 150); `addEducation_shouldReturnLevelInvalid_whenLevelIsUnknown` (`DOCTORATE`, `undergraduate`); `addEducation_shouldReturnDateInvalidFormat_whenDateIsMalformed` (incluido `"2024"`); `addEducation_shouldRejectInProgressThatIsNotBoolean_whenValueIsText` (`"inProgress":"abc"` → 422 `REQUEST_BODY_INVALID_FORMAT`); `addEducation_shouldRecordJacksonBehaviour_whenInProgressIsNumberOrQuotedBoolean` (V1: `1`, `0`, `"true"`; fija lo que pasa y, si se acepta `1` o `0`, detente y reporta).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ErrorCatalogTest" test`.

## [x] T-C2 · `EducationDraft` y fábricas de la formación — ≤ 30 min, ≈ 130 líneas

- **Crear** `domain/model/EducationDraft.java`: `record EducationDraft(String institution, String degree, EducationLevel level, YearMonth startDate, YearMonth endDate, boolean inProgress)` con Javadoc por componente; `create` asigna `DataProvenance.MANUAL`.
- **Modificar** `Education`: constantes `MAX_INSTITUTION_LENGTH = 150`, `MAX_DEGREE_LENGTH = 150`; constructor privado; `create(EducationDraft, DateBounds)` con el `Collector` y en orden: `institution`, `degree` (requerido → control → largo), `level == null` → `EDUCATION_LEVEL_REQUIRED` «Elige un nivel educativo.», `startDate == null` → `START_DATE_REQUIRED` «Ingresa la fecha de inicio.», `startDate` futura → `START_DATE_IN_THE_FUTURE`; `endDate`: presente con `inProgress` → `END_DATE_NOT_ALLOWED`; futura → `END_DATE_IN_THE_FUTURE`; anterior al inicio → `END_DATE_BEFORE_START_DATE`. `rebuild(UUID id, EducationDraft stored, DataProvenance provenance)` sin reglas (tres parámetros, como en la experiencia). `fieldOfStudy` desaparece de `Education` (campo, getter y regla de largo), de `AddEducationCommand`, de `ProfileController.addEducation` (el `new AddEducationCommand(...)` deja de pasar `r.fieldOfStudy()`), de `ProfessionalProfileMapping` (líneas que leen y escriben `FieldOfStudy`), de `EducationEntity` (campo `area_estudio`, getter y setter) y de `ProfileResponse.EducationItem`. El control de caracteres de `institution` y `degree` usa `INSTITUTION_CHARACTERS` y `DEGREE_CHARACTERS` (T-A3). Borrar `FieldRules` si queda sin uso.
- **Pruebas** `domain/model/EducationTest.java` (nueva; mes actual `2026-10`): `create_shouldAccept_whenNotInProgressWithoutEndDate`; `create_shouldAccept_whenEndEqualsStart` (`2025-02`/`2025-02`); `create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier` (`2025-02`/`2025-01`); `create_shouldAccept_whenStartIsCurrentMonth` (`2026-10`, en curso); `create_shouldRejectStartInTheFuture_whenStartIsNextMonth`; `create_shouldRejectEndInTheFuture_whenEndIsNextMonth` (`2022-02`/`2026-11`); `create_shouldAccept_whenEndIsCurrentMonth` (`2022-02`/`2026-10`); `create_shouldRejectEndDateNotAllowed_whenInProgress`; `create_shouldReportEveryField_whenSeveralRulesFail`; `rebuild_shouldNotValidate_whenStoredTextIsLongerThanLimit`. Actualizar `ProfileValueRulesTest` y `ProfessionalProfileRepositoryAdapterIT`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=EducationTest,ProfileValueRulesTest" test`.

## [x] T-C3 · Servicio de la formación y fin de `yearOnly` — ≤ 20 min, ≈ 70 líneas

- **Modificar** `AddEducationCommand` (solo `requireNonNull(profileId)`), `FieldValues.yearMonth(String value, ErrorCode code, String field)` sin `yearOnly` (y `FieldValuesTest`: quitar la prueba de `"2019"` aceptado; agregar `yearMonth_shouldRejectYearOnly_whenValueHasNoMonth` con `"2024"`), `DomainRule.Rule.START_DATE`/`END_DATE` sin el argumento `false`, `ProfileAppService.addEducation` con `EducationDraft`, `dateBounds()` y un `educationLevel(String)` privado igual a `employmentStatus(String)` de T-B3 (ausente → `null`); borrar los parámetros `yearOnly` de `startDate(...)`/`endDate(...)`.
- **Pruebas** `ProfileAppServiceTest.addEducation_shouldThrowFieldErrors_whenCommandHasNullRequiredFields` (cinco campos; `save` nunca).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,FieldValuesTest,DomainRuleValidatorTest" test`.

## [x] T-C4 · Máximo de 5 y mínimo en perfil Activo — ≤ 25 min, ≈ 100 líneas

- **Crear** `EducationLimitReachedException` (`EDUCATION_LIMIT_REACHED`, «Ya tienes el máximo de 5 formaciones académicas.») y `EducationNotAllowedException` (`EDUCATION_NOT_ALLOWED`, «No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.»); `ErrorCatalog.RESPONSES` con `CONFLICT` («Máximo de formaciones alcanzado», «No se puede eliminar la última formación»).
- **Modificar** `ProfessionalProfile`: `MAX_EDUCATIONS = 5`; `addEducation` lanza el límite con 5; `removeEducation`:
  ```java
  /**
   * Elimina una formación de este perfil.
   *
   * <p>Un perfil Activo debe conservar al menos una formación: la comprobación y la eliminación ocurren
   * con la fila del perfil bloqueada, así que dos pestañas que eliminan las dos últimas no pueden tener
   * éxito las dos.</p>
   *
   * @param eduId identificador de la formación
   * @throws EducationNotFoundException  si el identificador no es una formación de este perfil
   * @throws EducationNotAllowedException si es la única formación de un perfil Activo
   */
  public void removeEducation(UUID eduId) {
      if (educations.stream().noneMatch(e -> e.getId().equals(eduId))) throw new EducationNotFoundException();
      if (status == ProfileStatus.COMPLETED && educations.size() == 1) throw new EducationNotAllowedException();
      educations.removeIf(e -> e.getId().equals(eduId));
      touch();
  }
  ```
- **Pruebas** `ProfessionalProfileTest`: `addEducation_shouldAcceptFifth_whenProfileHasFour`; `addEducation_shouldRejectSixth_whenProfileHasFive`; `removeEducation_shouldRemoveLast_whenProfileIsInProgress`; `removeEducation_shouldRemoveLast_whenProfileIsInReview`; `removeEducation_shouldRejectLast_whenProfileIsCompleted` (sigue con 1 y `COMPLETED`); `removeEducation_shouldRemove_whenCompletedProfileHasTwo`; `removeEducation_shouldThrowNotFound_whenIdIsForeignInCompletedProfileWithOne` (404 antes que 409). `ProfileControllerTest`: `removeEducation_shouldReturn409_whenLastEducationOfActiveProfile`, `removeEducation_shouldReturn404_whenEducationIsNotInProfile`, `addEducation_shouldReturn409_whenLimitReached`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalProfileTest,ProfileControllerTest" test`.

## [x] T-C5 · Migración V7, entidad, mapeo y respuesta — ≤ 25 min, ≈ 100 líneas

- **Crear** `V7__alinear_educacion.sql` (número fijo; si ya existe un V7, detente y reporta) con el texto exacto de la spec 7.2 y un comentario SQL de cabecera en español, que incluye `ALTER TABLE educacion DROP COLUMN area_estudio;`. **Trampa:** la entidad y la migración van en el mismo commit; con `ddl-auto=validate`, una entidad que aún declara `area_estudio` no arranca sobre la base migrada. **Modificar** `EducationEntity` (`institucion` y `titulo` `length = 150`), `ProfessionalProfileMapping` (`Education.rebuild(id, new EducationDraft(...), provenance)`), `ProfileResponse.EducationItem` (`@Schema` por componente).
- **Comandos:** `./mvnw.cmd -B test`.

- **Resultado (10-oct-2026, PR 3a):** El PR 3 se partió en 3a (T-C1, T-C2, T-C3 y T-C5, rama `CM-274-formacion-academica`) y 3b (T-C4, T-C6 y T-C7) por tamaño: el total medía ≈ 1 500 líneas (Desvío 18 de la cola). Los códigos `EDUCATION_LIMIT_REACHED` y `EDUCATION_NOT_ALLOWED` de T-C1 pasan a 3b con T-C4. Rojo previo: `EducationTest` no compilaba sin `EducationDraft`, `Education.create` ni `Education.rebuild`; el resto del código se escribió antes que sus pruebas (precedente de CM-70). Verde: `clean verify` BUILD SUCCESS, 580 unitarias + 153 IT, 0 fallos (`ejecucion/verify-CM-274-3a.log`); las IT existentes pasan contra V7. Cobertura: `Education` 56/56 y 23/24; `EducationDraft` 1/1; `FieldValues` 12/12 y 8/8; `ProfileAppService` 70/72 y 16/16; `ProfessionalProfileMapping` 98/98 y 26/26; `ErrorCatalog` 79/79 y 2/2; `AddEducationRequest` 7/7; global 96,8 % líneas y 96,4 % ramas. Sin cubrir: una rama de defensa de `requireNonNull` en el constructor privado de `Education` y dos líneas previas de `ProfileAppService` (Desvío 13). V1 (`inProgress`): Jackson convierte `1`, `0`, `"true"` y `"false"` a booleano; la prueba lo fija y la pregunta va a la bandeja (no se cambia: igual que `company: 123`). Postman: la petición de formación en curso usa `2025-02` en lugar de `2026` y la del área de estudio de 501 pasa a institución de 151; Newman completo se corre en 3b, al cerrar el contrato.

## [x] T-C6 · Integración de la formación y carreras — ≤ 30 min, ≈ 200 líneas

- **Agregar** a `WorkHistoryEndToEndIT`: `addEducation_shouldReturn201WithNullEndDate_whenInProgress` (inicio `2025-02`, en curso); `removeEducation_shouldReturn204_whenDraftHasOnlyOne`; `removeEducation_shouldKeepOtherProfileEducation_whenIdBelongsToAnotherProfile`; `addEducation_shouldIgnoreFieldOfStudy_whenSent` (201 con `"fieldOfStudy":"Sistemas"` en el cuerpo; la respuesta no tiene la propiedad `fieldOfStudy`); y en `WorkHistorySchemaIT`, `educacion_shouldNotHaveFieldOfStudyColumn_whenMigrated` (`information_schema.columns` sin `area_estudio`).
- **Agregar** a `WorkHistorySchemaIT` las filas de cada `ck_educacion_*` y los nombres `pk_educacion`, `fk_educacion_perfil`, `ix_educacion_perfil_id`; a `ColumnLengthConsistencyIT`, `institucion` y `titulo`; a `FieldMessagesConsistencyTest`, los códigos de la formación.
- **Agregar** a `ProfileWriteLockIT`:
  - `removeEducation_shouldRejectSecond_whenTwoTabsDeleteBothEducationsOfActiveProfile`: perfil `COMPLETED` con formaciones E1 y E2; hilo A en `TransactionTemplate` llama `removeEducation(E1)` y espera un latch antes de confirmar; hilo B llama `removeEducation(E2)`; soltar A → A termina bien, B lanza `EducationNotAllowedException`; la base tiene solo E2 y el perfil sigue `COMPLETED`.
  - `removeEducation_shouldReturnNotFound_whenTwoTabsDeleteSameEducation`: dos bajas de E1 con 3 formaciones → una bien, otra `EducationNotFoundException`.
- **Agregar** a `WorkHistoryEndToEndIT`: `removeEducation_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02): perfil en Borrador con 3 formaciones; 20 `DELETE` de la misma → un 204 y diecinueve 404 `EDUCATION_NOT_FOUND`; quedan 2.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=WorkHistoryEndToEndIT,WorkHistorySchemaIT,ColumnLengthConsistencyIT,ProfileWriteLockIT" verify`.

## [x] T-C7 · OpenAPI, catálogo y cierre del PR 3 — ≤ 30 min

- `ProfileController` (`addEducation`, `removeEducation`) con todos los estados (201/204, 401, 403, 404, 409 `EDUCATION_LIMIT_REACHED` y `EDUCATION_NOT_ALLOWED`, 415, 422, 409 `PROFILE_UPDATE_IN_PROGRESS`); `OpenApiDocumentIT` (`maxLength` 150 y `allowableValues` de `level`); `docs/errores.md` (filas nuevas; la de «Pendiente con destino» de la última formación pasa a la fila de `EDUCATION_NOT_ALLOWED` 409; `FIELD_OF_STUDY_TOO_LONG` pasa a «Códigos retirados» con «Ya no se emite: el campo se eliminó del contrato.»).
- **Cierre:** `clean verify`, cobertura de `Education`, `EducationDraft`, `ProfessionalProfile`, las dos excepciones, `ProfileAppService`, `ProfessionalProfileMapping`, `ProfileResponse`, `AddEducationRequest`, `ErrorCatalog`; global sin bajar; diff ≤ 1000.

- **Resultado (10-oct-2026, PR 3b):** T-C4 (máximo de 5 y mínimo de 1 en perfil activo, con `EDUCATION_LIMIT_REACHED` y `EDUCATION_NOT_ALLOWED`), T-C6 (12 pruebas nuevas en `WorkHistoryEndToEndIT`, 9 de esquema de `educacion` en `WorkHistorySchemaIT`, `ColumnLengthConsistencyIT`, `FieldMessagesConsistencyTest` y dos carreras en `ProfileWriteLockIT`) y T-C7 (OpenAPI con 409, ejemplos y límites, `OpenApiDocumentIT`, `docs/errores.md`). Rojo previo: el código de T-C4 se escribió antes que sus pruebas; con el máximo en 6 y sin la comprobación del perfil activo fallan `addEducation_shouldRejectSixth_whenProfileHasFive` y `removeEducation_shouldRejectLast_whenProfileIsCompleted` (cambios revertidos). Verde: `clean verify` BUILD SUCCESS, 593 unitarias + 181 IT, 0 fallos (`ejecucion/verify-CM-274-3b.log`). Newman contra la imagen empaquetada con base limpia y el rol de `a0000007-…-000000000006` en `INACTIVE` (como pide la spec de CM-70): 111 peticiones, 0 fallidas, 361 aserciones (`ejecucion/newman-CM-274-3b.log`). Adversarial sin 5xx (`ejecucion/adversarial-CM-274-3b.log`): carácter nulo, 151 caracteres, año suelto, JSON roto, `text/plain` (415), `inProgress`/institución con objeto o arreglo, nivel nulo con fin futuro, fin con en curso, sin identidad (401), sexta formación (409), id mal escrito (422) e inexistente (404). Cobertura: `Education` 56/56 y 23/24; `ProfessionalProfile` 129/131 y 55/56; ambas excepciones 2/2; `ProfileAppService` 71/72 y 16/16; `ErrorCatalog` 82/82; global 96,9 % líneas y 96,7 % ramas. Sin cubrir: ramas de defensa de `requireNonNull` en constructores privados. La «Verificación previa en staging» de V7 (consulta 4) la ejecuta Paula antes de desplegar.

---

# PR 4 — Postman de la experiencia (`CM-274-postman-experiencia`)

## [x] T-D1 · Utilidades de la colección y carpeta HU-2.4 — ≤ 30 min

- **Modificar** `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` (v2.1, UTF-8, sin BOM):
  - Script de prueba a nivel de colección que define en `pm.globals`/`eval` las funciones `expectProblem(status, code, detail)` (estado, `Content-Type` contiene `application/problem+json` y `charset=UTF-8`, `code`, `detail`, `requestId` no vacío, cuerpo sin `Exception`, `at co.edu`, `SQL`, `org.`) y `expectFieldError(field, code, message)` (existe un elemento de `errors` con esos tres valores).
  - Carpeta `HU-2.4 — Experiencia laboral y formación académica` con subcarpetas `POST work-experiences` y `DELETE work-experiences/{expId}`. `pre-request` de la carpeta: `pm.variables.set('hu24_uid', 'uid-pm-' + pm.variables.replaceIn('{{$guid}}'))`, mes actual `new Date().toISOString().slice(0, 7)` y mes siguiente, ambos en UTC.
  - Primera petición de cada subcarpeta: crea el perfil del Usuario de la carpeta y guarda `hu24_profile`.
- **Terminado:** la colección se abre en Postman sin errores de esquema (`npx newman run … --folder "HU-2.4 — Experiencia laboral y formación académica"` sin fallos de script).

## [x] T-D2 · Peticiones de `work-experiences` — ≤ 30 min, ≈ 600 líneas

- Una petición por fila con «Postman» en la spec sección 4 para la experiencia y, por campo de la sección 6.1, una por causa: `company` ausente, `null`, `""`, `"   "`, 99, 100, 101, con emoji en 101; `position` ídem (vacío, 100, 101); `description` 499, 500, 501, `<script>`; `employmentStatus` vacío, `FREELANCE`, `current`; `startDate` y `endDate` con `2024-13`, `13/2024`, `2024`; mes actual y siguiente (variables del `pre-request`); `ENDED` sin fin; `CURRENT` con `2026-09` y con `09/2026`; fin antes del inicio; tres campos a la vez; cuerpo ilegible `{"company":` (422 `REQUEST_BODY_INVALID_FORMAT`); `Content-Type: text/plain` (415); campos no esperados (201); sin `X-User-Id` (401); perfil de otro Usuario (403); perfil inexistente (404); quinta experiencia (409); `DELETE` de una experiencia de otro perfil (404) y de una inexistente (404); `DELETE` dos veces (204 y 404); control `\u0000` (P4).
- Cada petición: `expectProblem`/`expectFieldError` o, en 201/204, estado, `Content-Type` y los campos devueltos.
- **Comandos:** `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json --folder "HU-2.4 — Experiencia laboral y formación académica"` contra `docker compose up -d --build` con base limpia; pegar la salida.

- **Resultado (10-oct-2026, PR 4a: T-D1 y la subcarpeta `POST work-experiences` de T-D2):** con las dos subcarpetas la colección crecía 1 039 líneas (94 peticiones); se partió por capacidad (Desvío 21): este PR trae T-D1 y las 72 peticiones de `POST work-experiences` (829 líneas); `DELETE work-experiences/{expId}` queda para el PR 4b y T-D2 se cierra allí. Utilidades (`typeOk`, `problem`, `fields`, `created`, `noContent`, `experience`, `clean`) en el `pre-request` de la colección, cargadas con `eval(pm.globals.get('hu24Helpers'))`; la identidad `uid-pm-{{$guid}}` se crea en la primera petición de la subcarpeta (el `pre-request` de la carpeta se ejecuta antes de cada petición y la cambiaría a mitad del flujo), y el `pre-request` de la carpeta calcula los meses en UTC y los textos de largo exacto. Cada 201 limpia el perfil con `pm.sendRequest` para no llegar al máximo de 4. Newman contra la imagen empaquetada con base limpia (`ejecucion/newman-CM-274-4a.log`): 72 peticiones de la colección + 42 de limpieza, 0 fallidas, 558 aserciones. Segunda corrida sobre la misma base: 0 fallidas. Las pruebas pueden fallar: con una copia que cambia un detalle (máximo de 5), un mensaje (`Ingresa el puesto.`) y el conteo de la limpieza fallan 5 aserciones. `docker compose logs app`: 0 líneas ` ERROR ` y 0 con `Acme`, `<script>`, `Hola` ni `Perfil de prueba` (SEG-04). Cruce de la columna «Postman» de la sección 4 con los nombres de la colección: todas las filas de `POST work-experiences` tienen su petición con el nombre de la columna; las que faltan son de `DELETE work-experiences` (PR 4b: `404 experiencia de otro perfil`, `404 experiencia inexistente`, `403 DELETE experiencia ajena`, `204 única experiencia de un perfil Activo`), de la formación (PR 5) y del PR 6 (15 años, nacimiento y 503).

- **Resultado (10-oct-2026, PR 4b: subcarpeta `DELETE work-experiences/{expId}`, cierre de T-D2):** 22 peticiones nuevas (210 líneas): experiencia de otro perfil (404 `WORK_EXPERIENCE_NOT_FOUND`), inexistente (404), perfil ajeno (403 `PROFILE_NOT_ALLOWED`) con comprobación de que el otro perfil conserva su experiencia, `expId` y `id` que no son UUID (422), perfil inexistente (404), sin identidad (401), 204 y el mismo borrado repetido (404), y el flujo del perfil Activo (nombre y resumen, formación, habilidad, rol objetivo, finalización) donde la única experiencia se borra con 204 y el perfil sigue `COMPLETED` (CA-2.4.38). Newman de la carpeta contra la imagen empaquetada con base limpia (`ejecucion/newman-CM-274-4b.log`): 136 peticiones, 0 fallidas, 651 aserciones; segunda corrida sobre la misma base: igual. **Colección completa** con base limpia y el rol `a0000007-…06` en `INACTIVE` (`ejecucion/newman-CM-274-4b-completa.log`): 247 peticiones, 0 fallidas, 1 012 aserciones, es decir, el script de utilidades a nivel de colección no altera las carpetas anteriores. Con una copia que cambia el `code` esperado del 404 fallan tres aserciones. `docker compose logs app`: 0 líneas ` ERROR ` y 0 con `Acme`, `<script>`, `Uno S.A.` ni `Perfil de prueba HU`. Cruce de la columna «Postman» de la sección 4: de las filas de experiencia solo falta `503 experiencia sin réplica (CA-2.4.36)`, que es del PR 6.

# PR 5 — Postman de la formación y cierre (`CM-274-postman-formacion`)

## [x] T-E1 · Peticiones de `educations` — ≤ 30 min, ≈ 550 líneas

- Igual que T-D2 para la sección 6.2: los cuatro obligatorios a la vez; `institution` y `degree` 149/150/151; `level` `DOCTORATE`, `undergraduate`, vacío; `startDate` `"2024"`; en curso con fin; mes actual y siguiente; fin antes del inicio; `inProgress: "abc"`; sexta formación (409); última formación de un perfil Activo (409, armando el perfil completo y finalizándolo); una de dos en Activo (204); Borrador con una (204); de otro perfil (404); inexistente (404); 403 en `POST` y `DELETE`.

- **Resultado (10-oct-2026, PR 5a: subcarpeta `POST educations` de T-E1):** 59 peticiones (610 líneas, con la limpieza de las utilidades generalizada a formaciones): los cuatro obligatorios a la vez (CA-2.4.6), `institution` y `degree` de 149, 150 y 151 (también con emoji y carácter nulo), `level` ausente, vacío, `DOCTORATE` y `undergraduate`, `startDate` ausente, `2024-13`, `2024`, `13/2024` y mes siguiente, `endDate` con otro formato, mes siguiente, en curso con fin y fin antes del inicio, `inProgress` `"abc"` y objeto, cuerpo ilegible y vacío, 415, 401 (ausente y de 129 caracteres), 403 con comprobación del otro perfil, 404, UUID inválido, campos no esperados ignorados (`fieldOfStudy` no vuelve), y el máximo: quinta formación 201 y sexta 409 `EDUCATION_LIMIT_REACHED`. Newman de la carpeta contra la imagen empaquetada (`ejecucion/newman-CM-274-5a.log`): 231 peticiones (las anteriores más las nuevas y su limpieza), 0 fallidas, 1 113 aserciones; segunda corrida sobre la misma base: igual. Con una copia que cambia el mensaje del nivel, el conteo de la limpieza y el máximo esperado fallan 5 aserciones. `docker compose logs app`: 0 líneas ` ERROR ` y 0 con datos de las peticiones. `DELETE educations/{eduId}`, DES-02, el README y la corrida completa van en el PR 5b.

- **Resultado (10-oct-2026, PR 5b: `DELETE educations/{eduId}`, resto de T-E1):** 23 peticiones (252 líneas con la carpeta DES-02): formación de otro perfil (404 `EDUCATION_NOT_FOUND`), inexistente (404), perfil ajeno (403) con comprobación de que el otro perfil conserva la suya, `eduId` y `id` que no son UUID (422), perfil inexistente (404), sin identidad (401), única formación en Borrador (204) y el mismo borrado repetido (404), y el flujo del perfil Activo con dos formaciones: quitar una (204, CA-2.4.46) y quitar la última (409 `EDUCATION_NOT_ALLOWED` con el texto del CA-2.4.37, el perfil sigue `COMPLETED` con esa formación). Con una copia que cambia el `code` esperado del 404 y del 409 fallan 3 aserciones.

## [x] T-E2 · Entorno local — ≤ 10 min

- **Comprobar** que existe `docs/perfil-local.postman_environment.json` (lo crea CM-279 P2) con `base_url` `http://localhost:8082`; si falta, créalo con esa sola variable (sin secretos) y repórtalo. **Modificar** `README.md`: sección «Pruebas de API con Newman» con los comandos de la spec sección 14 y de T-E3 (si CM-279 P2 ya la creó, agrega solo lo que falte).

- **Resultado (10-oct-2026, PR 5b):** `docs/perfil-local.postman_environment.json` ya existía (lo creó la capa de eventos de cuenta) con `base_url` `http://localhost:8082` y sin secretos; no se tocó. `README.md` gana la sección «Pruebas de API con Newman» con la corrida completa, la carpeta suelta y la medición DES-02. Se comprobó con una corrida real que la colección completa necesita el rol `a0000007-…06` del catálogo en `INACTIVE` (sin él fallan 13 aserciones de las carpetas de roles objetivo: `ejecucion/newman-CM-274-5b-sin-rol-inactivo.log`) y el README lo dice.

## [x] T-E3 · Corrida completa de Newman — ≤ 20 min

- `docker compose down -v`; `docker compose up -d --build`; esperar `GET /actuator/health` 200; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --reporters cli,junit --reporter-junit-export target/newman/perfil.xml`. Pegar la salida completa (peticiones, aserciones, fallos, tiempo máximo por petición). Si algo falla aquí y no en las pruebas automáticas, agrega primero la prueba automática que falta y luego corrige. Revisar los logs: `docker compose logs app | Select-String -Pattern ' ERROR '` → 0 líneas; `docker compose logs app | Select-String -Pattern 'Bancolombia|Universidad|<script>|2008-03-15'` → 0 líneas (SEG-04).
- **DES-02:** agrega a la colección la carpeta `DES-02 — HU-2.4` con tres peticiones (crear perfil con `X-User-Id: uid-des-{{$guid}}` nuevo por iteración; `POST …/work-experiences` válido; `DELETE` de esa experiencia), cada una con `pm.test` de estado. Corre `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "DES-02 — HU-2.4" -n 105 --reporters cli,json --reporter-json-export target/newman/des02.json` y calcula el p95 sin las 5 primeras iteraciones:
  ```powershell
  node -e "const ex=require('./target/newman/des02.json').run.executions;const g={};ex.filter(e=>e.cursor.iteration>=5).forEach(e=>{(g[e.item.name]??=[]).push(e.response.responseTime)});for(const [k,v] of Object.entries(g)){v.sort((a,b)=>a-b);console.log(k,'n='+v.length,'p95='+v[Math.ceil(v.length*0.95)-1]+' ms')}"
  ```
  Se espera `n=100` por petición y p95 ≤ 2000 ms. Pega la salida en la fila DES-02 de la spec (sección 15, «Resultado real»).

- **Resultado (10-oct-2026, PR 5b):** corrida completa con `docker compose down -v`, `up -d --build`, `GET /actuator/health` 200 y el rol `a0000007-…06` en `INACTIVE` (`ejecucion/newman-CM-274-5b.log`): 368 peticiones (colección y limpieza), 0 fallidas, 1 582 aserciones, tiempo medio 31 ms, máximo 1 980 ms. El máximo es `GET /v3/api-docs`, que genera el OpenAPI en su primera llamada (32 kB); ninguna petición de HU-2.4 pasó de 1 s. Segunda corrida de la carpeta HU-2.4 sobre la misma base: 254 peticiones, 0 fallidas, 1 218 aserciones. `docker compose logs app`: 0 líneas ` ERROR ` y 0 coincidencias con `Bancolombia|Universidad|<script>|2008-03-15` (SEG-04). **DES-02** (`ejecucion/newman-CM-274-5b-des02.log`, 105 iteraciones, se descartan 5): `n=100` por petición; crear perfil p95 = 35 ms, `POST …/work-experiences` p95 = 49 ms, `DELETE …/work-experiences/{expId}` p95 = 40 ms (límite 2 000 ms). Los resultados de DES-02 y SEG-04 quedan en la sección 15 de la spec. Staging queda PENDIENTE de DevOps.

---

# PR 6 — Fecha de nacimiento (`CM-274-fecha-nacimiento`) — espera a que el bloque P1 de CM-279 esté en `develop`

Se ejecuta cuando `develop` tenga el bloque P1 de CM-279 (spec `specs/CM-279-replica-fecha-nacimiento/` del worktree `perfil-CM-279-replica`): tabla `fecha_nacimiento_usuario` con clave `firebase_uid`, puerto `BirthDateReplica.findBirthDate(FirebaseUid)` con su adaptador, `BirthDateUnavailableException` (503 `BIRTH_DATE_UNAVAILABLE`, ya en `ErrorCatalog`) y el doble `InMemoryBirthDateReplica`. Este PR **no crea** ninguna de esas piezas.

## [x] T-F1 · Comprobar las piezas de CM-279 — ≤ 10 min

- **Rama:** `CM-274-fecha-nacimiento` desde `origin/develop` actualizado, con el PR 5 de esta tarea y el bloque P1 de CM-279 ya fusionados.

- `git grep -n "interface BirthDateReplica\|class BirthDateUnavailableException\|class InMemoryBirthDateReplica\|fecha_nacimiento_usuario" origin/develop` debe encontrar las cuatro. Si falta alguna, o la firma de `findBirthDate` no es `Optional<LocalDate> findBirthDate(FirebaseUid)`, detente y reporta.

- **Resultado (10-oct-2026):** las cuatro piezas están en la cadena (CM-279 P1a y P1b, filas 2a y 2b de la cola): `BirthDateReplica` con `Optional<LocalDate> findBirthDate(FirebaseUid)`, `BirthDateUnavailableException`, `InMemoryBirthDateReplica` y la tabla `fecha_nacimiento_usuario` (`V5`). La rama sale de la punta del PR 5b.

## [x] T-F2 · Reglas de los 15 años y del nacimiento — ≤ 30 min

- `WorkExperience.create`: con `bounds.birthMonth() != null`, `fifteen = birthMonth.plusYears(15)`; inicio anterior → `START_DATE_BEFORE_MINIMUM_AGE` «La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.»; sin inicio y fin anterior → `END_DATE_BEFORE_MINIMUM_AGE` «La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.». `Education.create`: inicio anterior a `birthMonth` → `START_DATE_BEFORE_BIRTH` «La fecha no puede ser anterior a tu fecha de nacimiento.».
- **Pruebas** (nacimiento `2008-03-15` → `birthMonth` `2008-03`): `WorkExperienceTest.create_shouldAccept_whenStartIsMonthOfFifteenthBirthday` (`2023-03`), `create_shouldRejectStart_whenBeforeFifteenthBirthday` (`2023-02`), `create_shouldAcceptEnd_whenNoStartAndEndIsMonthOfFifteenthBirthday`, `create_shouldRejectEnd_whenNoStartAndEndIsBeforeFifteenthBirthday`, `create_shouldUseFebruary_whenBornOnLeapDay` (nacimiento `2008-02-29`, inicio `2023-02` aceptado); `EducationTest.create_shouldAccept_whenStartIsBirthMonth` (`2008-03`), `create_shouldRejectStart_whenBeforeBirthMonth` (`2008-02`).

- **Resultado (10-oct-2026):** las comparaciones viven en `DateBounds.isBeforeMinimumAge` y `isBeforeBirth` (extender lo existente: el dominio ya recibía `DateBounds`) con `DateBoundsTest` (5 pruebas: sin nacimiento, mes de los 15 años, 29 de febrero, nacimiento entre años y mes actual obligatorio). `WorkExperience` y `Education` aplican las reglas en el orden de la sección 6: futuro antes que edad mínima, y el fin solo se compara con la edad mínima si no hay inicio. Rojo previo (`clean test` con el método de `DateBounds` pero sin las reglas): fallaban `WorkExperienceTest.create_shouldRejectStart_whenBeforeFifteenthBirthday`, `create_shouldRejectEnd_whenNoStartAndEndIsBeforeFifteenthBirthday`, `create_shouldReportOnlyStart_whenStartAndEndAreBeforeFifteenthBirthday` y `EducationTest.create_shouldRejectStart_whenBeforeBirthMonth`; verde con las reglas. Códigos nuevos `START_DATE_BEFORE_MINIMUM_AGE`, `END_DATE_BEFORE_MINIMUM_AGE` y `START_DATE_BEFORE_BIRTH` en `ErrorCode`, `docs/errores.md` y `ErrorCatalogTest`.

## [x] T-F3 · Servicio: leer la réplica solo con fechas — ≤ 25 min

- `ProfileAppService` recibe `BirthDateReplica`; `dateBounds(owner, hasDates)`: si el registro no trae ninguna fecha, `new DateBounds(YearMonth.now(clock), null)` sin leer la réplica; si trae, `replica.findBirthDate(owner).map(YearMonth::from).orElseThrow(BirthDateUnavailableException::new)`.
- **Pruebas** `ProfileAppServiceTest`: `addWorkExperience_shouldNotReadReplica_whenRecordHasNoDates` (`verifyNoInteractions(replica)`, 201); `addWorkExperience_shouldThrowBirthDateUnavailable_whenReplicaIsEmptyAndRecordHasDates` (`save` nunca); `addEducation_shouldThrowBirthDateUnavailable_whenReplicaIsEmpty`. `ProfileControllerTest.addWorkExperience_shouldReturn503_whenBirthDateIsUnavailable` (503, `code`, `detail` literal). Comprobar con un `ListAppender` de Logback que la fecha de nacimiento no aparece en ningún log.

- **Resultado (10-oct-2026):** `ProfileAppService` recibe `BirthDateReplica` y lo usa en `createWithBirthMonth` (un solo método para experiencia y formación). **Diferencia con la tarjeta (Desvío 22):** la tarjeta lanzaba el 503 antes de las reglas del registro; la spec manda otra cosa (REQ-EF-17: reglas del registro 422 → 503 → máximo 409), así que el servicio corre las reglas con el mes de nacimiento que haya y solo responde 503 si el registro es válido y la réplica no tiene la fecha: así un registro inválido recibe todos sus errores (también los de edad cuando la fecha existe) y no un 503 que el cliente no puede corregir. Rojo previo: con el constructor ya recibiendo la réplica y sin usarla fallaban 8 de las 11 pruebas nuevas de `ProfileAppServiceTest` (`addWorkExperience_shouldThrowBirthDateUnavailable_whenReplicaIsEmptyAndRecordHasDates`, `…whenOnlyEndDateIsPresent`, `addEducation_shouldThrowBirthDateUnavailable_whenReplicaIsEmpty`, `addWorkExperience_shouldApplyMinimumAge_whenBirthDateIsReplicated`, `…shouldReportEveryField_whenBirthRuleAndAnotherRuleFail`, `addEducation_shouldRejectStartBeforeBirth_whenBirthDateIsReplicated`, `addWorkExperience_shouldReadReplicaOnce_whenRecordHasDates`, `addWorkExperience_shouldNeverLogBirthDate_whenReplicaIsRead`); verde: 72 pruebas del servicio. `addWorkExperience_shouldNotReadReplica_whenRecordHasNoDates` verifica con un espía que no se lee la réplica (REQ-EF-64) y `ProfileControllerTest.addWorkExperience_shouldReturn503_whenBirthDateIsUnavailable` fija el 503.

## [x] T-F4 · E2E, Postman y cierre — ≤ 30 min

- `WorkHistoryEndToEndIT` con una fila en `fecha_nacimiento_usuario` (`insert into fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) values (?, date '2008-03-15')` por JDBC; la tabla de CM-279 tiene solo esas dos columnas, ambas `NOT NULL`) para CA-2.4.20 a 2.4.23, 2.4.31, 2.4.32 y sin fila para CA-2.4.36, 2.4.52 y 2.4.53. Postman: peticiones `(PR 6)` de la spec sección 4; antes de la corrida, `docker compose exec db psql -U cameia_perfil -d cameia_perfil -c "insert into fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) values ('uid-pm-nacimiento', date '2008-03-15')"` y esa identidad en las peticiones con réplica. `docs/errores.md`, OpenAPI (503 `BIRTH_DATE_UNAVAILABLE`), `clean verify`, cobertura, Newman completo.

- **Resultado (10-oct-2026, PR 6a: código, pruebas de integración y OpenAPI):** (a) **Efecto en lo existente:** desde este PR toda experiencia o formación con fechas de un Usuario sin fila en la réplica responde 503; fallaban 26 pruebas de integración (`WorkHistoryEndToEndIT`, `WorkHistoryNovemberUtcIT`, `ProfileWriteLockIT`, `TargetRoleConcurrencyIT`, `TargetRolesEndToEndIT`: `ejecucion/verify-CM-274-6-segunda.log`) y se corrigieron dejando la fecha de nacimiento de cada Usuario de prueba con `support/ReplicatedBirthDates` (adulto de 1990). (b) **E2E nuevas** en `WorkHistoryEndToEndIT` (12): CA-2.4.20 a 2.4.23, 2.4.31, 2.4.32, nacida el 29 de febrero, 503 de experiencia y de formación sin réplica (tipo de contenido, `code`, texto y sin datos internos), 201 sin fechas con réplica vacía (CA-2.4.53), 422 antes que 503 y reintento con éxito cuando la fecha llega. (c) **OpenAPI** del 503 y de los códigos nuevos en los dos endpoints, con ejemplo; `OpenApiDocumentIT` lo comprueba. (d) **Verificación:** `clean verify` BUILD SUCCESS, 622 unitarias + 193 de integración, 0 fallos (`ejecucion/verify-CM-274-6.log`). Cobertura de lo tocado: `DateBounds` 5/5 y 8/8, `Education` 58/58 y 26/26, `WorkExperience` 73/75 y 46/48 (defensas previas del dominio que el borde ya cubre con `@DomainRule`), `ProfileAppService` 81/82 y 28/28 (`loadProfile`, previa), `ProfileController` 34/34; global 97,0 % de líneas y 97,1 % de ramas. Las peticiones de Postman, Newman, las peticiones adversariales, DES-02 y los logs van en el PR 6b.

- **Resultado (10-oct-2026, PR 6b: Postman, Newman, peticiones adversariales, DES-02 y logs):** (a) **Postman:** cada Usuario de HU-2.4 y de DES-02 publica `cuenta.creada` por la API de administración de RabbitMQ antes de crear su perfil (`H.replicate`, con espera fija de 600 ms y comprobación de que el mensaje se enrutó), la carpeta `Preparación` del inicio hace lo mismo con las dos identidades fijas que agregan fechas, y las peticiones `(PR 6)` de la sección 4 están: 201/422 a los 15 años (CA-2.4.20 a 2.4.23), formación en el mes de nacimiento y antes (CA-2.4.31 y 2.4.32), 503 de experiencia y de formación sin réplica (CA-2.4.36 y 2.4.52) y 201 sin fechas con réplica vacía (CA-2.4.53). (b) **Newman:** con la imagen empaquetada, base limpia y el rol `a0000007-…06` en `INACTIVE` (`ejecucion/newman-CM-274-6.log`): 395 peticiones, 0 fallidas, 1 668 aserciones; segunda corrida de la carpeta sobre la misma base: 277 peticiones, 0 fallidas, 1 298 aserciones. Con una copia que cambia el código esperado, el mensaje y la fecha de nacimiento publicada fallan 23 aserciones. Peticiones adversariales reales (`ejecucion/adversarial-CM-274-6.log`): 17 casos, ningún 5xx salvo el 503 esperado. (c) **DES-02** tras el PR 6 (`ejecucion/newman-CM-274-6-des02.log`, 105 iteraciones, se descartan 5, sin las ejecuciones duplicadas que el informe JSON genera para la petición con `pre-request` asíncrono): crear perfil p95 = 50 ms, `POST …/work-experiences` p95 = 45 ms (ya con la lectura de la réplica), `DELETE` p95 = 35 ms; `n=100`. (d) **Logs** de toda la sesión: 9 líneas ` ERROR `, todas `BIRTH_DATE_UNAVAILABLE` provocados a propósito; 0 con la fecha de nacimiento ni con textos de las peticiones; 0 trazas.

---

# Autoverificación de CM-274 (lista de la sección 22 de la spec, 10-oct-2026)

Comando de las filas que se pueden comprobar por archivo: `python ejecucion/herramientas/autoverificacion_cm274.py` (salida en `ejecucion/autoverificacion-CM-274.log`), con `clean verify` corrido en la punta de la CM (`CM-274-postman-fecha-nacimiento`).

| # | Resultado real |
|---|---|
| V-01 | **Cumple.** 70 nombres de la columna «Prueba»: la primera corrida encontró 7 que no existían como método (`ProfileControllerTest.addWorkExperience_shouldReturnEndDateRequired_whenEndedWithoutEndDate`, `addWorkExperience_shouldAccept_whenTextIsAtLimit` (CA 2.4.17 y 2.4.19), `addEducation_shouldAccept_whenTextIsAtLimit`, `workHistory_shouldReturn403_whenProfileBelongsToAnotherUser`, y la referencia de noviembre, que estaba en la clase equivocada). Se crearon las cuatro pruebas web (con valores literales; la del 403 recorre las cuatro rutas) y se corrigió la clase de `addWorkExperience_shouldAcceptNovember_whenUtcIsAlreadyNovember` (`WorkHistoryNovemberUtcIT`). Ahora faltan 0. |
| V-02 | **Cumple.** 73 nombres de la columna «Postman» en la carpeta `HU-2.4`; faltan 0 (la fila CA 2.4.51 es la nota «no se reproduce en Newman», cubierta por `ProfileWriteLockIT` y por `409 última formación de un perfil Activo`). |
| V-03 | **Cumple.** 33 textos entre « » de las secciones 6 y 8.1: los 33 están literalmente en `src/main`; Newman los compara con `expectFieldError` y `problem` en cada petición. |
| V-04 | **Cumple.** `clean verify` BUILD SUCCESS: 631 unitarias + 193 de integración, 0 fallos, 0 omitidas (`ejecucion/verify-CM-274-6b.log`). |
| V-05 | **Cumple.** `ArquitecturaTest` dentro de V-04; la prueba temporal que demuestra que la regla nueva muerde está en el resultado de T-A9 (PR 1b). |
| V-06 | **Cumple con una excepción documentada.** 24 de 25 clases de la sección 13 con ≥ 90 % de líneas y de ramas. `DomainRuleValidator`: 17/18 líneas y 5/6 ramas, la cláusula de defensa de la tarjeta T-A3 que no se alcanza (Desvío 13). La corrida encontró `ProfileResponse` en 7/8 ramas (la expectativa salarial presente, de la historia CM-19, que ninguna prueba unitaria ejercía) y se cubrió con `ProfileResponseTest`. Global del repo: 97,0 % de líneas y 97,4 % de ramas, sin bajar. |
| V-07 | **Cumple.** Peticiones reales sin 500: `adversarial-CM-274-2a.log`, `adversarial-CM-274-3b.log` y `adversarial-CM-274-6.log` (17 casos de la fecha de nacimiento), más el cuerpo de 1 MiB de `WorkHistoryEndToEndIT`, el `text/plain`, `inProgress` ilegible y U+0000 de la colección de Newman. |
| V-08 | **Cumple.** `typeOk('application/problem+json')` con `charset=UTF-8`, `code`, `detail`, `requestId` y la ausencia de `Exception`, `at co.edu`, `SQL` y `org.` en cada 4xx de Newman; en 422, un elemento por campo con `field`, `code` y `message`. |
| V-09 a V-12 | **Cumplen.** Dentro de V-04: `WorkHistorySchemaIT` (V6 y V7 sobre filas de V5, `ck_`, nombres), `ColumnLengthConsistencyIT` y `OpenApiDocumentIT` (100/100/500/150/150), `ProfileWriteLockIT` (espera, 409 entre 1,5 s y 3,5 s, máximo de 4 y última formación en Activo) y `…shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (un 204 y diecinueve 404). |
| V-13 | **Cumple.** Colección completa con base limpia y el rol `a0000007-…06` en `INACTIVE`, 395 peticiones, 0 fallidas, 1 668 aserciones (`ejecucion/newman-CM-274-6.log`). El máximo de la corrida es `GET /v3/api-docs` en frío (2,7 s: genera el OpenAPI en su primera llamada); ninguna petición de HU-2.4 pasó de 1 s. |
| V-14 | **Cumple.** DES-02 tras el PR 6, `n=100`: p95 de 50 ms (crear perfil), 45 ms (`POST` con la lectura de la réplica) y 35 ms (`DELETE`), límite 2 000 ms. Staging queda PENDIENTE de DevOps. |
| V-15 | **Cumple.** 9 líneas ` ERROR `, todas el 503 `BIRTH_DATE_UNAVAILABLE` provocado a propósito, sin traza; 0 coincidencias con los datos de la sección 15. |
| V-16 | **Cumple.** `ErrorCodeDocumentationTest` en V-04; `FIELD_OF_STUDY_TOO_LONG` y `PROFILE_CREATION_TIMEOUT` están en «Códigos retirados». |
| V-17 | **Cumple para lo nuevo; hallazgo con destino para lo previo.** Ninguna línea agregada por la cadena lleva `CM-NNN` (`git diff origin/develop -- src` filtrado: 0). El `git grep` encuentra 29 coincidencias en comentarios de la Sprint 1 (`CM-16` a `CM-23` y `CM-82`, en 21 archivos); por la regla R1 solo se renombra lo ajeno si la spec lo recoge: **destino CM-283 (alinear el estándar)**, que limpia los comentarios con identificador de tarea. |
| V-18 | **Cumple.** `git diff origin/develop -- pom.xml` vacío. |
| V-19 | **Cumple.** PR 0: 1 504 líneas (solo documentos, excepción aprobada); 1a: 943; 1b: 806; 2a: 903; 2b: 803; 3a: 781; 3b: 784; 4a: 833; 4b: 214; 5a: 620; 5b: 290; 6a: 773; 6b: 356. Todos los que llevan código, ≤ 903. |
| V-20 | **PENDIENTE (Paula, Prompt H).** `/code-review high` y `/security-review` se lanzan dentro de un repo y sobre cambios pendientes: CM-274 toca identidad, datos personales y migraciones (`V6`, `V7`), así que los hace Paula en un chat aparte con el worktree de revisión que deja la cola. |
