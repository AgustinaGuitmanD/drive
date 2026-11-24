package io.github.agustinaguitmand.drive.controller.dto;

import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;

import java.time.Instant;
import java.util.UUID;
// "revisionActual" es el número del contenido actual (1, 2, 3...).
// "version" es la que el cliente tiene que mandar al renombrar o subir una revisión.
public record ArchivoResponse(UUID id, String nombre, TipoArchivo tipo, String contentType,
                              long tamanio, Instant fechaCreacion, Instant fechaModificacion,
                              long version, String checksum, int revisionActual) {

    public static ArchivoResponse from(Archivo archivo) {
        return new ArchivoResponse(
                archivo.getId(),
                archivo.getNombre(),
                archivo.getTipo(),
                archivo.getContentType(),
                archivo.getTamanio(),
                archivo.getFechaCreacion(),
                archivo.getFechaModificacion(),
                archivo.getVersion(),
                archivo.getChecksum(),
                archivo.getRevisionActual());
    }
}
