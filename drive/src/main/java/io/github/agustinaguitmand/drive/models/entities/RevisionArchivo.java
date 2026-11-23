package io.github.agustinaguitmand.drive.models.entities;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Un contenido que tuvo el archivo en algún momento (1, 2, 3...).
 * Es inmutable: subir contenido nuevo crea otra revisión en lugar de modificar esta,
 * así se puede descargar cualquier contenido anterior.
 */
@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"archivo_id", "numero"}))
public class RevisionArchivo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archivo_id", updatable = false)
    private Archivo archivo;

    @Column(nullable = false, updatable = false)
    private int numero;

    @Column(nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(nullable = false, updatable = false, length = 64)
    private String checksum;

    @Column(nullable = false, updatable = false)
    private long tamanio;

    @Column(nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    public RevisionArchivo(Archivo archivo, int numero, String storageKey, String checksum,
                           long tamanio, String contentType, Instant fecha) {
        this.id = UUID.randomUUID();
        this.archivo = archivo;
        this.numero = numero;
        this.storageKey = storageKey;
        this.checksum = checksum;
        this.tamanio = tamanio;
        this.contentType = contentType;
        this.fecha = fecha;
    }

    // Solo para JPA: lo usa para crear el objeto al leerlo de la base.
    protected RevisionArchivo() {
    }
}