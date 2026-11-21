package io.github.agustinaguitmand.drive.controller.dto;

import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;

import java.time.Instant;
import java.util.UUID;

public record ArchivoResponse(UUID id, String nombre, TipoArchivo tipo, String contentType,
                              long tamanio, Instant fechaCreacion, Instant fechaModificacion) {

    public static ArchivoResponse from(Archivo archivo) {
        return new ArchivoResponse(archivo.getId(), archivo.getNombre(), archivo.getTipo(),
                archivo.getContentType(), archivo.getTamanio(),
                archivo.getFechaCreacion(), archivo.getFechaModificacion());
    }
}
