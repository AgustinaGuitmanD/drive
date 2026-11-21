package io.github.agustinaguitmand.drive.services.impl;
import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.NombreDuplicadoException;
import io.github.agustinaguitmand.drive.storage.IBlobStorage;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;
import io.github.agustinaguitmand.drive.repositories.IArchivoRepository;
import io.github.agustinaguitmand.drive.services.IArchivoService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;

@Service
public class ArchivoService implements IArchivoService {
    private static final Logger log = LoggerFactory.getLogger(ArchivoService.class);

    // archivoRepository: guarda la metadata (nombre, dueño, fechas) en la base.
    // blobStorage: guarda los bytes del archivo (en disco; en producción sería S3).
    // clock: da la hora actual; se inyecta para poder fijarla en los tests.
    private final IArchivoRepository IArchivoRepository;
    private final IBlobStorage blobStorage;
    private final Clock clock;

    public ArchivoService(IArchivoRepository IArchivoRepository,
                          IBlobStorage blobStorage,
                          Clock clock) {
        this.IArchivoRepository = IArchivoRepository;
        this.blobStorage = blobStorage;
        this.clock = clock;
    }

    /**
     * Sube un archivo en tres pasos: valida, guarda los bytes en el storage
     * y guarda la metadata en la base. Si falla la base, borra los bytes.
     * Sin @Transactional a propósito: así el error de la base sale dentro del try.
     */
    @Override
    public Archivo subir(UUID usuarioId, MultipartFile archivo) {
        // 1. Validar, antes de escribir nada
        validarQueTengaContenido(archivo);
        String nombre = nombreDe(archivo);
        String contentType = archivo.getContentType();
        TipoArchivo tipo = TipoArchivo.desdeContentType(contentType);
        verificarNombreDisponible(usuarioId, nombre);

        // 2. Guardar los bytes
        String storageKey = blobStorage.guardar(leerContenido(archivo));

        // 3. Guardar la metadata; si falla, no deja bytes huérfanos
        try {
            Archivo nuevo = new Archivo(usuarioId, nombre, tipo, contentType,
                    archivo.getSize(), storageKey, Instant.now(clock));
            return IArchivoRepository.save(nuevo);
        } catch (RuntimeException e) {
            borrarContenidoSinFallar(storageKey);
            throw e;
        }
    }



    @Override
    public List<Archivo> listarArchivosUsuario(UUID usuarioId) {
        return List.of();
    }

    @Override
    public ContenidoArchivo obtenerContenido(UUID usuarioId, UUID archivoId) {
        return null;
    }

    @Override
    public Archivo renombrar(UUID usuarioId, UUID archivoId, String nuevoNombre, long version) {
        return null;
    }

    @Override
    public void borrar(UUID usuarioId, UUID archivoId) {

    }

    @Override
    public Archivo obtenerMetadata(UUID usuarioId, UUID archivoId) {
        return null;
    }

    private void validarQueTengaContenido(MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new ArchivoInvalidoException("El archivo está vacío");
        }
    }

    private String nombreDe(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || nombre.isBlank()) {
            throw new ArchivoInvalidoException("El archivo no tiene nombre");
        }
        return nombre;
    }

    private void verificarNombreDisponible(UUID usuarioId, String nombre) {
        if (IArchivoRepository.existsByDuenioIdAndNombre(usuarioId, nombre)) {
            throw new NombreDuplicadoException(nombre);
        }
    }

    private InputStream leerContenido(MultipartFile archivo) {
        try {
            return archivo.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", e);
        }
    }

    // Si no se puede borrar, se loguea y se sigue: no tapa el error original.
    private void borrarContenidoSinFallar(String storageKey) {
        try {
            blobStorage.borrar(storageKey);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar el contenido {}; queda huérfano en disco", storageKey, e);
        }
    }


}
