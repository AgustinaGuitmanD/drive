package io.github.agustinaguitmand.drive.exceptions;

import java.util.UUID;


public class ConflictoDeVersionException extends RuntimeException {
    public ConflictoDeVersionException(UUID id, Long versionEsperada, Long versionActual) {
        super("Conflicto de versión en el archivo: " + id + " se esperaba la versión " + versionEsperada
               + " pero la actual es " + versionActual);
    }
}

