package io.github.agustinaguitmand.drive.services.impl;
import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.ArchivoNoEncontradoException;
import io.github.agustinaguitmand.drive.exceptions.ConflictoDeVersionException;
import io.github.agustinaguitmand.drive.exceptions.NombreDuplicadoException;
import io.github.agustinaguitmand.drive.models.helpers.NombresDeArchivo;
import io.github.agustinaguitmand.drive.services.IPersonaService;
import io.github.agustinaguitmand.drive.storage.IBlobStorage;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.repositories.IArchivoRepository;
import io.github.agustinaguitmand.drive.services.IArchivoService;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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
    private final IArchivoRepository archivoRepository;
    private final IBlobStorage blobStorage;
    private final Clock clock;
    private final IPersonaService personaService;
    private static final int MAXIMO_DE_COPIAS = 500;

    public ArchivoService(IArchivoRepository archivoRepository,
                          IBlobStorage blobStorage,
                          IPersonaService personaService, Clock clock) {
        this.archivoRepository = archivoRepository;
        this.blobStorage = blobStorage;
        this.personaService = personaService;
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
        personaService.verificarQueExista(usuarioId);
        validarQueTengaContenido(archivo);
        String nombre = nombreLibre(usuarioId, archivo.getOriginalFilename());
        String contentType = archivo.getContentType();
        TipoArchivo tipo = TipoArchivo.desdeContentType(contentType);

        // 2. Guardar los bytes
        String storageKey = blobStorage.guardar(leerContenido(archivo));

        // 3. Guardar la metadata; si falla, no deja bytes huérfanos
        try {
            Archivo nuevo = new Archivo(usuarioId, nombre, tipo, contentType,
                    archivo.getSize(), storageKey, Instant.now(clock));
            return archivoRepository.save(nuevo);
        } catch (DataIntegrityViolationException e) {
            borrarContenidoSinFallar(storageKey);
            // Otra subida simultánea tomó el mismo nombre entre la verificación y el guardado.
            throw new NombreDuplicadoException(nombre);
        } catch (RuntimeException e) {
            borrarContenidoSinFallar(storageKey);
            throw e;
        }
    }


    @Override
    public List<Archivo> listarArchivosUsuario(UUID usuarioId) {
        return archivoRepository.findByDuenioIdOrderByFechaModificacionDesc(usuarioId);
    }

    @Override
    public ContenidoArchivo obtenerContenido(UUID usuarioId, UUID archivoId) {
        Archivo archivo = buscarArchivoDelUsuario(usuarioId, archivoId);
        return new ContenidoArchivo(
                archivo.getNombre(),
                MediaType.parseMediaType(archivo.getContentType()),
                blobStorage.abrir(archivo.getStorageKey()));
    }

    @Override
    @Transactional
    public Archivo renombrar(UUID usuarioId, UUID archivoId, String nuevoNombre, long version) {
        Archivo archivo = buscarArchivoDelUsuario(usuarioId, archivoId);
        verificarVersion(archivo, version);
        if (!archivo.getNombre().equals(nuevoNombre)) {
            verificarNombreDisponible(usuarioId, nuevoNombre);
        }
        archivo.renombrar(nuevoNombre, Instant.now(clock));
        try {
            return archivoRepository.saveAndFlush(archivo);
        } catch (DataIntegrityViolationException e) {
            // Otro archivo usó ese nombre entre la verificación y el guardado.
            throw new NombreDuplicadoException(nuevoNombre);
        }
    }

    /**
     * Primero se borra la metadata y después el contenido.
     * Si bien si falla el borrado en disco, queda un archivo huérfano, creo que
     * al revés sería peor: la metadata quedaría apuntando a un contenido que ya no existe.
     * Sin @Transactional a propósito: así la base confirma el borrado antes de tocar el disco.
     */
    @Override
    public void borrar(UUID usuarioId, UUID archivoId) {
        Archivo archivo = buscarArchivoDelUsuario(usuarioId, archivoId);
        archivoRepository.delete(archivo);
        borrarContenidoSinFallar(archivo.getStorageKey());
    }

    @Override
    public Archivo obtenerMetadata(UUID usuarioId, UUID archivoId) {
        return buscarArchivoDelUsuario(usuarioId, archivoId);
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
        if (archivoRepository.existsByDuenioIdAndNombre(usuarioId, nombre)) {
            throw new NombreDuplicadoException(nombre);
        }
    }


    // Si el nombre está libre lo usa tal cual; si no, prueba "nombre (1)", "nombre (2)"...
    private String nombreLibre(UUID usuarioId, String nombre) {
        if (!archivoRepository.existsByDuenioIdAndNombre(usuarioId, nombre)) {
            return nombre;
        }
        for (int numero = 1; numero <= MAXIMO_DE_COPIAS; numero++) {
            String candidato = NombresDeArchivo.conNumero(nombre, numero);
            if (!archivoRepository.existsByDuenioIdAndNombre(usuarioId, candidato)) {
                return candidato;
            }
        }
        throw new NombreDuplicadoException(nombre);
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

    // Busca siempre por id Y dueño: si el archivo es de otro usuario, es como si no existiera.
    private Archivo buscarArchivoDelUsuario(UUID usuarioId, UUID archivoId) {
        return archivoRepository.findByIdAndDuenioId(archivoId, usuarioId)
                .orElseThrow(() -> new ArchivoNoEncontradoException(archivoId));
    }

    // El cliente manda la versión que vio. Si no coincide, alguien modificó
    // el archivo en el medio y no pisamos su cambio.
    private void verificarVersion(Archivo archivo, long versionEsperada) {
        if (archivo.getVersion() != versionEsperada) {
            throw new ConflictoDeVersionException(archivo.getId(), versionEsperada, archivo.getVersion());
        }
    }


}
