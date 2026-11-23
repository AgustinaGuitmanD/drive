package io.github.agustinaguitmand.drive.services;

import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.RevisionArchivo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IArchivoService {
    public Archivo subir(UUID usuarioId, MultipartFile archivo);
    public List<Archivo> listarArchivosUsuario(UUID usuarioId);
    public ContenidoArchivo obtenerContenido(UUID usuarioId, UUID archivoId);
    Archivo renombrar(UUID usuarioId, UUID archivoId, String nuevoNombre, long version);
    void borrar(UUID usuarioId, UUID archivoId);
    Archivo obtenerMetadata(UUID usuarioId, UUID archivoId);
    Archivo subirRevision(UUID usuarioId, UUID archivoId, MultipartFile archivo, long versionEsperada);
    List<RevisionArchivo> listarRevisiones(UUID usuarioId, UUID archivoId, int limite);
    ContenidoArchivo obtenerContenidoDeRevision(UUID usuarioId, UUID archivoId, int numero);
}
