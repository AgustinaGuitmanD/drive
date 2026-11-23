package io.github.agustinaguitmand.drive.exceptions;

import java.util.UUID;

public class RevisionNoEncontradaException extends RuntimeException {
    public RevisionNoEncontradaException(UUID archivoId, int numero) {
        super("El archivo " + archivoId + " no tiene la revisión " + numero);
    }
}