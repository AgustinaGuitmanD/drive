package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.RevisionNoEncontradaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.agustinaguitmand.drive.models.helpers.Validaciones.textoObligatorio;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"duenio_id", "nombre"}))
public class Archivo {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID duenioId;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoArchivo tipo;       // Imagen, doc

    @Column(nullable = false, length = 100)
    private String contentType;

    private long tamanio;

    @Column(nullable = false, unique = true, updatable = false)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(nullable = false)
    private Instant fechaModificacion;

    // Control de concurrencia: aumenta con CUALQUIER cambio (renombrar o subir contenido).
    // El cliente manda la versión que vio; si no coincide, alguien modificó el archivo
    // en el medio y se responde 409 en lugar de pisar ese cambio.
    @Version
    private long version;

    // Número de la revisión actual del contenido. A diferencia de "version",
    // solo aumenta cuando se sube contenido nuevo: renombrar no crea una revisión (como el historial de docs).
    @Column(nullable = false)
    private int revisionActual;

    @OneToMany(mappedBy = "archivo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero DESC")
    @Getter(AccessLevel.NONE)
    private List<RevisionArchivo> revisiones = new ArrayList<>();

    // Huella SHA-256 del contenido, en hexadecimal (64 caracteres).
    @Column(nullable = false, length = 64, updatable = false)
    private String checksum;

    private static final int LARGO_MAXIMO_NOMBRE = 255;

    protected Archivo() { }

    public Archivo(UUID duenioId, String nombre, TipoArchivo tipo, String contentType,
                   long tamanio, String storageKey, Instant ahora, String checksum) {
        if (tamanio <= 0) {
            throw new ArchivoInvalidoException("El archivo está vacío");
        }
        this.nombre = textoObligatorio(nombre, "El nombre", LARGO_MAXIMO_NOMBRE);
        this.id = UUID.randomUUID();
        this.duenioId = duenioId;
        this.tipo = tipo;
        this.contentType = contentType;
        this.tamanio = tamanio;
        this.storageKey = storageKey;
        this.fechaCreacion = ahora;
        this.fechaModificacion = ahora;
        this.checksum = checksum;
    }

    public void renombrar(String nuevoNombre, Instant ahora) {
        this.nombre = textoObligatorio(nuevoNombre, "El nombre", LARGO_MAXIMO_NOMBRE);
        this.fechaModificacion = ahora;
    }

    public RevisionArchivo agregarRevision(String storageKey, String checksum, long tamanio,
                                           String contentType, Instant ahora) {
        if (TipoArchivo.desdeContentType(contentType) != this.tipo) {
            throw new ArchivoInvalidoException("La revisión debe ser del mismo tipo que el archivo");
        }
        revisionActual++;
        RevisionArchivo revision = new RevisionArchivo(this, revisionActual, storageKey,
                checksum, tamanio, contentType, ahora);
        revisiones.add(0, revision);
        this.fechaModificacion = ahora;
        return revision;
    }

    // Una revisión tiene que ser del mismo tipo que el archivo.
    public void verificarTipoCompatible(String contentType) {
        if (TipoArchivo.desdeContentType(contentType) != this.tipo) {
            throw new ArchivoInvalidoException("La revisión debe ser del mismo tipo que el archivo");
        }
    }

    public List<RevisionArchivo> ultimasRevisiones(int cantidad) {
        return revisiones.stream().limit(cantidad).toList();
    }

    // Busca una revisión por su número. Si no existe (o ya se descartó), no hay nada que devolver.
    public RevisionArchivo revision(int numero) {
        return revisiones.stream()
                .filter(revision -> revision.getNumero() == numero)
                .findFirst()
                .orElseThrow(() -> new RevisionNoEncontradaException(id, numero));
    }
}