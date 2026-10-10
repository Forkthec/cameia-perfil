package co.edu.unicauca.cameia.perfil.application.service;

import co.edu.unicauca.cameia.perfil.application.command.RemoveBirthDateCommand;
import co.edu.unicauca.cameia.perfil.application.command.ReplicateBirthDateCommand;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException;
import co.edu.unicauca.cameia.perfil.domain.exception.InvalidAccountEventException.Reason;
import co.edu.unicauca.cameia.perfil.domain.model.FirebaseUid;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.InMemoryBirthDateReplica;
import co.edu.unicauca.cameia.perfil.infrastructure.persistence.InMemoryProcessedMessageInbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El consumo de eventos de cuenta: validación, Inbox, idempotencia y privacidad del log, con dobles y reloj fijo. */
@ExtendWith(OutputCaptureExtension.class)
class BirthDateReplicaAppServiceTest {

    private static final String MESSAGE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_MESSAGE_ID = "22222222-2222-4222-8222-222222222222";
    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final LocalDate BIRTH_DATE = LocalDate.of(2008, 3, 15);

    private InMemoryBirthDateReplica replica;
    private InMemoryProcessedMessageInbox inbox;
    private BirthDateReplicaAppService service;

    @BeforeEach
    void setUp() {
        replica = new InMemoryBirthDateReplica();
        inbox = new InMemoryProcessedMessageInbox();
        var clock = Clock.fixed(Instant.parse("2026-10-09T15:04:05Z"), ZoneOffset.UTC);
        service = new BirthDateReplicaAppService(replica, inbox, clock);
    }

    private static ReplicateBirthDateCommand created(String messageId, String uid, LocalDate birthDate) {
        return new ReplicateBirthDateCommand(messageId, uid, birthDate);
    }

    private void assertNothingTouched() {
        assertThat(replica.contents()).isEmpty();
        assertThat(inbox.contents()).isEmpty();
    }

    @Test
    @DisplayName("Un evento válido guarda la fecha y anota el mensaje")
    void recordAccountCreated_shouldStoreBirthDateAndRegisterMessage_whenEventIsValid() {
        service.recordAccountCreated(created(MESSAGE_ID, UID, BIRTH_DATE));

        assertThat(replica.contents()).containsEntry(UID, BIRTH_DATE).hasSize(1);
        assertThat(inbox.contents()).containsEntry(UUID.fromString(MESSAGE_ID), "cuenta.creada").hasSize(1);
    }

    @Test
    @DisplayName("Reentregar el mismo mensaje 20 veces no tiene más efecto que la primera")
    void recordAccountCreated_shouldHaveNoEffect_whenMessageWasProcessed() {
        for (int i = 0; i < 20; i++) {
            service.recordAccountCreated(created(MESSAGE_ID, UID, BIRTH_DATE));
        }

        assertThat(replica.contents()).hasSize(1);
        assertThat(inbox.contents()).hasSize(1);
    }

    @Test
    @DisplayName("Un segundo mensaje de creación conserva la primera fecha")
    void recordAccountCreated_shouldKeepFirstDate_whenOtherMessageHasOtherDate() {
        service.recordAccountCreated(created(MESSAGE_ID, UID, BIRTH_DATE));
        service.recordAccountCreated(created(OTHER_MESSAGE_ID, UID, LocalDate.of(2000, 1, 1)));

        assertThat(replica.contents()).containsEntry(UID, BIRTH_DATE);
        assertThat(inbox.contents()).hasSize(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "1-1-1-1-1", "11111111-1111-4111-8111-11111111111Z"})
    @DisplayName("Un identificador de mensaje ausente o mal formado se rechaza sin tocar nada")
    void recordAccountCreated_shouldReject_whenMessageIdIsInvalid(String messageId) {
        assertThatThrownBy(() -> service.recordAccountCreated(created(messageId, UID, BIRTH_DATE)))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.MESSAGE_ID_INVALID));
        assertNothingTouched();
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres es válida")
    void recordAccountCreated_shouldAccept_whenUserIdHas128Characters() {
        String uid = "a".repeat(FirebaseUid.MAX_LENGTH);

        service.recordAccountCreated(created(MESSAGE_ID, uid, BIRTH_DATE));

        assertThat(replica.contents()).containsKey(uid);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Una identidad ausente o en blanco se rechaza sin tocar nada")
    void recordAccountCreated_shouldReject_whenUserIdIsInvalid(String uid) {
        assertThatThrownBy(() -> service.recordAccountCreated(created(MESSAGE_ID, uid, BIRTH_DATE)))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.USER_ID_INVALID));
        assertNothingTouched();
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres se rechaza sin tocar nada")
    void recordAccountCreated_shouldReject_whenUserIdHas129Characters() {
        String uid = "a".repeat(FirebaseUid.MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.recordAccountCreated(created(MESSAGE_ID, uid, BIRTH_DATE)))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.USER_ID_INVALID));
        assertNothingTouched();
    }

    @Test
    @DisplayName("Una fecha de nacimiento ausente se rechaza sin tocar el Inbox")
    void recordAccountCreated_shouldReject_whenBirthDateIsNull() {
        assertThatThrownBy(() -> service.recordAccountCreated(created(MESSAGE_ID, UID, null)))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.BIRTH_DATE_REQUIRED));
        assertNothingTouched();
    }

    @Test
    @DisplayName("Nacer hoy en UTC es válido")
    void recordAccountCreated_shouldAccept_whenBirthDateIsToday() {
        service.recordAccountCreated(created(MESSAGE_ID, UID, LocalDate.of(2026, 10, 9)));

        assertThat(replica.contents()).containsEntry(UID, LocalDate.of(2026, 10, 9));
    }

    @Test
    @DisplayName("Una fecha de mañana se rechaza sin tocar el Inbox")
    void recordAccountCreated_shouldReject_whenBirthDateIsTomorrow() {
        assertThatThrownBy(() -> service.recordAccountCreated(created(MESSAGE_ID, UID, LocalDate.of(2026, 10, 10))))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.BIRTH_DATE_IN_THE_FUTURE));
        assertNothingTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1915-10-09", "1900-01-01", "0001-01-01", "-9999-01-01"})
    @DisplayName("Una fecha con más de 110 años cumplidos se rechaza sin tocar el Inbox")
    void recordAccountCreated_shouldReject_whenBirthDateImpliesMoreThan110Years(String birthDate) {
        assertThatThrownBy(() -> service.recordAccountCreated(created(MESSAGE_ID, UID, LocalDate.parse(birthDate))))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.BIRTH_DATE_OUT_OF_RANGE));
        assertNothingTouched();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1915-10-10", "1916-10-09"})
    @DisplayName("Una fecha con 110 años cumplidos o menos se guarda")
    void recordAccountCreated_shouldStore_whenBirthDateImpliesUpTo110Years(String birthDate) {
        service.recordAccountCreated(created(MESSAGE_ID, UID, LocalDate.parse(birthDate)));

        assertThat(replica.contents()).containsEntry(UID, LocalDate.parse(birthDate));
    }

    @Test
    @DisplayName("Nacer un 29 de febrero se guarda tal cual")
    void recordAccountCreated_shouldStoreLeapDay_whenBornOnFebruary29() {
        service.recordAccountCreated(created(MESSAGE_ID, UID, LocalDate.of(2008, 2, 29)));

        assertThat(replica.contents()).containsEntry(UID, LocalDate.of(2008, 2, 29));
    }

    @Test
    @DisplayName("Un evento de cuenta eliminada borra la fecha guardada")
    void recordAccountDeleted_shouldRemoveBirthDate_whenPresent() {
        service.recordAccountCreated(created(MESSAGE_ID, UID, BIRTH_DATE));

        service.recordAccountDeleted(new RemoveBirthDateCommand(OTHER_MESSAGE_ID, UID));

        assertThat(replica.contents()).isEmpty();
        assertThat(inbox.contents()).containsEntry(UUID.fromString(OTHER_MESSAGE_ID), "cuenta.eliminada");
    }

    @Test
    @DisplayName("Borrar una fecha inexistente no falla y anota el mensaje")
    void recordAccountDeleted_shouldNotFail_whenAbsent() {
        service.recordAccountDeleted(new RemoveBirthDateCommand(MESSAGE_ID, UID));

        assertThat(replica.contents()).isEmpty();
        assertThat(inbox.contents()).containsKey(UUID.fromString(MESSAGE_ID));
    }

    @Test
    @DisplayName("Un mensaje de eliminación ya procesado no borra nada")
    void recordAccountDeleted_shouldHaveNoEffect_whenMessageWasProcessed() {
        service.recordAccountDeleted(new RemoveBirthDateCommand(MESSAGE_ID, UID));
        replica.saveIfAbsent(new FirebaseUid(UID), BIRTH_DATE);

        service.recordAccountDeleted(new RemoveBirthDateCommand(MESSAGE_ID, UID));

        assertThat(replica.contents()).containsEntry(UID, BIRTH_DATE);
    }

    @Test
    @DisplayName("Una identidad en blanco en la eliminación se rechaza")
    void recordAccountDeleted_shouldReject_whenUserIdIsBlank() {
        assertThatThrownBy(() -> service.recordAccountDeleted(new RemoveBirthDateCommand(MESSAGE_ID, "   ")))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.USER_ID_INVALID));
        assertNothingTouched();
    }

    @Test
    @DisplayName("Un identificador de mensaje que no es UUID en la eliminación se rechaza")
    void recordAccountDeleted_shouldReject_whenMessageIdIsNotUuid() {
        assertThatThrownBy(() -> service.recordAccountDeleted(new RemoveBirthDateCommand("abc", UID)))
                .isInstanceOfSatisfying(InvalidAccountEventException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.MESSAGE_ID_INVALID));
        assertNothingTouched();
    }

    @Test
    @DisplayName("El log lleva la identidad y el mensaje, nunca la fecha de nacimiento")
    void recordAccountCreated_shouldNotLogBirthDate_whenStored(CapturedOutput output) {
        service.recordAccountCreated(created(MESSAGE_ID, UID, BIRTH_DATE));

        assertThat(output.getAll()).contains(UID).contains(MESSAGE_ID).doesNotContain("2008-03-15");
    }
}
