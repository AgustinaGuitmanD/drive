package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.TipoNoPermitidoException;

import java.util.Arrays;
import java.util.Set;

/**
 * Tipos de archivo que acepta el sistema, cada uno con los formatos permitidos.
 * Es una lista blanca: lo que no está acá se rechaza. No se aceptan HTML ni SVG
 * porque pueden contener scripts y se sirven inline en el navegador.
 */
public enum TipoArchivo {
    IMAGEN(Set.of("image/png", "image/jpeg", "image/gif", "image/webp")),
    DOCUMENTO(Set.of("application/pdf", "text/plain"));

    private final Set<String> contentTypesPermitidos;

    TipoArchivo(Set<String> contentTypesPermitidos) {
        this.contentTypesPermitidos = contentTypesPermitidos;
    }

    public static TipoArchivo desdeContentType(String contentType) {
        // Set.of no admite contains(null): se descarta antes de buscar.
        if (contentType == null) {
            throw new TipoNoPermitidoException(contentType);
        }
        for (TipoArchivo tipo : values()) {                          // recorre IMAGEN y DOCUMENTO
            if (tipo.contentTypesPermitidos.contains(contentType)) { // ¿está en su lista?
                return tipo;                                         // sí: es de este tipo
            }
        }
        throw new TipoNoPermitidoException(contentType);             // no estaba en ninguna lista
    }
}