package io.github.agustinaguitmand.drive.exceptions;

public class ArchivoInvalidoException extends RuntimeException {
    public ArchivoInvalidoException(String mensaje) {
        super(mensaje);
    }
}