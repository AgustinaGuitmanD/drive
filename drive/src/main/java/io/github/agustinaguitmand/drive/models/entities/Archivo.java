package io.github.agustinaguitmand.drive.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Getter
public class Archivo {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID duenioId;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoArchivo tipo;

    private String contentType;
    private long tamanio;
    private String storageKey;

    private Instant fechaCreacion;
    private Instant fechaModificacion;

    @Version
    private long version;

    protected Archivo() { }

    public Archivo(UUID duenioId, String nombre, TipoArchivo tipo,
                   String contentType, long tamanio, String storageKey, Instant ahora) {
        this.id = UUID.randomUUID();
        // validar nombre, tipo/contentType y tamaño antes de asignar
        this.duenioId = duenioId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.contentType = contentType;
        this.tamanio = tamanio;
        this.storageKey = storageKey;
        this.fechaCreacion = ahora;
        this.fechaModificacion = ahora;
    }

    public void renombrar(String nuevoNombre, Instant ahora) {
        this.nombre = nuevoNombre;
        this.fechaModificacion = ahora;
    }
}