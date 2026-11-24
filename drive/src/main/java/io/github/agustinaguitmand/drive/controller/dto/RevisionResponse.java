package io.github.agustinaguitmand.drive.controller.dto;

import io.github.agustinaguitmand.drive.models.entities.RevisionArchivo;

import java.time.Instant;

public record RevisionResponse(int numero, long tamanio, String contentType,
                               String checksum, Instant fecha) {

    // Solo usa datos propios de la revisión: se arma fuera de la transacción,
    // así que no puede acceder al archivo (se carga de forma diferida).
    public static RevisionResponse from(RevisionArchivo revision) {
        return new RevisionResponse(revision.getNumero(), revision.getTamanio(),
                revision.getContentType(), revision.getChecksum(), revision.getFecha());
    }
}