package co.edu.unicauca.cameia.perfil.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

/**
 * Deja en la tabla de la réplica la fecha de nacimiento de los Usuarios de prueba, como lo haría el evento de cuenta
 * creada. Sin esa fila, agregar una experiencia o una formación con fechas responde 503.
 */
public final class ReplicatedBirthDates {

    /** Nacimiento de una persona adulta: ninguna fecha de las pruebas queda antes de sus 15 años ni de su nacimiento. */
    public static final LocalDate ADULT = LocalDate.of(1990, 1, 1);

    private ReplicatedBirthDates() {
    }

    /**
     * Guarda la fecha de nacimiento del Usuario; si ya había una, la deja como estaba.
     *
     * @param jdbc      acceso a la base de la prueba
     * @param uid       identidad del Usuario
     * @param birthDate fecha de nacimiento que queda replicada
     */
    public static void insert(JdbcTemplate jdbc, String uid, LocalDate birthDate) {
        jdbc.update("INSERT INTO fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento) VALUES (?, ?) "
                + "ON CONFLICT (firebase_uid) DO NOTHING", uid, birthDate);
    }

    /**
     * Guarda la fecha de nacimiento de una persona adulta para el Usuario.
     *
     * @param jdbc acceso a la base de la prueba
     * @param uid  identidad del Usuario
     */
    public static void insertAdult(JdbcTemplate jdbc, String uid) {
        insert(jdbc, uid, ADULT);
    }

    /**
     * Borra las fechas replicadas de los Usuarios de prueba cuya identidad empieza con el prefijo.
     *
     * @param jdbc   acceso a la base de la prueba
     * @param prefix inicio de la identidad de los Usuarios de la prueba, por ejemplo {@code uid-work-}
     */
    public static void deleteByPrefix(JdbcTemplate jdbc, String prefix) {
        jdbc.update("DELETE FROM fecha_nacimiento_usuario WHERE firebase_uid LIKE ?", prefix + "%");
    }
}
