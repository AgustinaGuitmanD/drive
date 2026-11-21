package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

import static io.github.agustinaguitmand.drive.models.helpers.Validaciones.textoObligatorio;

@Entity
@Getter
public class Archivo {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID duenioId;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoArchivo tipo;       // Imagen, video, audio, doc

    @Column(nullable = false, length = 100)
    private String contentType;

    private long tamanio;

    @Column(nullable = false, unique = true, updatable = false)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(nullable = false)
    private Instant fechaModificacion;

    @Version
    private long version;

    private static final int LARGO_MAXIMO_NOMBRE = 255;

    protected Archivo() { }

    public Archivo(UUID duenioId, String nombre, TipoArchivo tipo,
                   String contentType, long tamanio, String storageKey, Instant ahora) {
        if (tamanio <= 0) {
            throw new ArchivoInvalidoException("El archivo está vacío");
        }
        this.nombre = textoObligatorio(nombre, "El nombre", 255);
        this.id = UUID.randomUUID();
        this.duenioId = duenioId;
        this.tipo = tipo;
        this.contentType = contentType;
        this.tamanio = tamanio;
        this.storageKey = storageKey;
        this.fechaCreacion = ahora;
        this.fechaModificacion = ahora;
    }

    public void renombrar(String nuevoNombre, Instant ahora) {

        this.nombre = textoObligatorio(nombre, "El nombre", 255);
        this.fechaModificacion = ahora;
    }


}