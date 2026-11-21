package io.github.agustinaguitmand.drive.exceptions;

import java.util.UUID;

public class ArchivoNoEncontradoException extends RuntimeException {
    public ArchivoNoEncontradoException(UUID id) {
      super("El archivo con id " + id + " no se encuentra.");
    }
}
