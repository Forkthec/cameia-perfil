package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.ProcessedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Acceso a la tabla del Inbox; la anotación es una inserción atómica que ignora el conflicto. */
interface ProcessedMessageJpaRepository extends JpaRepository<ProcessedMessageEntity, UUID> {

    /**
     * Inserta el identificador del mensaje si no está.
     *
     * @param messageId   identificador del mensaje
     * @param eventType   tipo del evento
     * @param processedAt instante del procesamiento
     * @return 1 si se insertó, 0 si ya estaba
     */
    @Modifying
    @Query(value = """
            INSERT INTO evento_procesado (message_id, tipo, procesado_en)
            VALUES (:messageId, :eventType, :processedAt)
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") UUID messageId, @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt);
}
