package io.github.agustinaguitmand.drive.models.helpers;

import io.github.agustinaguitmand.drive.exceptions.DatoInvalidoException;

/**
 * Validaciones genéricas que comparten las entidades.
 * Las reglas propias de cada entidad quedan en su clase.
 */
public final class Validaciones {

    private Validaciones() { }

    public static String textoObligatorio(String valor, String campo, int largoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new DatoInvalidoException(campo + " no puede estar vacío");
        }
        if (valor.length() > largoMaximo) {
            throw new DatoInvalidoException(campo + " supera los " + largoMaximo + " caracteres");
        }
        return valor;
    }
}