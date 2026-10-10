package co.edu.unicauca.cameia.perfil.infrastructure.persistence.repository;

import co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de Spring Data sobre la tabla {@code evento_saliente}.
 *
 * <p>Pieza interna de la infraestructura: el resto del servicio usa el puerto {@code OutboxRepository}.</p>
 */
interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Inserta un evento pendiente.
     *
     * @param id            identificador del evento
     * @param type          tipo del evento
     * @param version       versión del contrato
     * @param aggregateId   identificador del perfil
     * @param payload       carga JSON
     * @param correlationId correlación con la petición
     * @param createdAt     instante de creación
     */
    @Modifying
    @Query(value = """
            INSERT INTO evento_saliente
                (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, intentos)
            VALUES (:id, :type, :version, :aggregateId, CAST(:payload AS jsonb), :correlationId, :createdAt, 0)
            """, nativeQuery = true)
    void insert(@Param("id") UUID id, @Param("type") String type, @Param("version") short version,
            @Param("aggregateId") UUID aggregateId, @Param("payload") String payload,
            @Param("correlationId") String correlationId, @Param("createdAt") Instant createdAt);

    /**
     * Lee y bloquea los pendientes más antiguos; las filas que otra transacción ya bloqueó se saltan.
     *
     * @param limit cantidad máxima de filas
     * @return los pendientes por orden de creación
     */
    @Query(value = """
            SELECT * FROM evento_saliente
             WHERE fecha_publicacion IS NULL
             ORDER BY fecha_creacion, id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockPending(@Param("limit") int limit);

    /**
     * Marca el evento como publicado y borra su carga.
     *
     * @param id identificador del evento
     * @param at instante de la confirmación
     * @return 1 si seguía pendiente, 0 si no
     */
    @Modifying
    @Query(value = "UPDATE evento_saliente SET fecha_publicacion = :at, carga = NULL WHERE id = :id AND fecha_publicacion IS NULL",
            nativeQuery = true)
    int markPublished(@Param("id") UUID id, @Param("at") Instant at);

    /**
     * Suma un intento fallido a un evento pendiente.
     *
     * @param id identificador del evento
     * @return los intentos después de sumar, o {@code null} si el evento ya no está pendiente
     */
    @Query(value = "UPDATE evento_saliente SET intentos = intentos + 1 WHERE id = :id AND fecha_publicacion IS NULL RETURNING intentos",
            nativeQuery = true)
    Integer incrementAttempts(@Param("id") UUID id);

    /**
     * Cuenta los pendientes.
     *
     * @return cantidad de eventos sin publicar
     */
    @Query(value = "SELECT count(*) FROM evento_saliente WHERE fecha_publicacion IS NULL", nativeQuery = true)
    long countPending();
}
