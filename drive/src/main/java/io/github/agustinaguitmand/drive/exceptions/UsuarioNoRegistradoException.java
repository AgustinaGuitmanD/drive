package io.github.agustinaguitmand.drive.exceptions;

import java.util.UUID;

public class UsuarioNoRegistradoException extends RuntimeException {
  public UsuarioNoRegistradoException(UUID id) {
    super("El usuario con id " + id + " no se encuentra registrado.");
  }
}