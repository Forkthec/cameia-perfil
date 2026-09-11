package co.edu.unicauca.cameia.perfil.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Fila del catálogo de roles profesionales TI (CM-23). Ver migración V3. */
@Entity
@Table(name = "rol_profesional")
public class ProfessionalRoleEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "categoria", nullable = false, length = 100)
    private String categoria;

    public ProfessionalRoleEntity() {}

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }

    public void setId(UUID id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
}
