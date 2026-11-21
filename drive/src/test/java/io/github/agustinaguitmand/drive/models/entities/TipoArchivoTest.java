package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.TipoNoPermitidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TipoArchivoTest {

    @Test
    void unaImagenPngEsDeTipoImagen() {
        assertEquals(TipoArchivo.IMAGEN, TipoArchivo.desdeContentType("image/png"));
    }

    @Test
    void unPdfEsDeTipoDocumento() {
        assertEquals(TipoArchivo.DOCUMENTO, TipoArchivo.desdeContentType("application/pdf"));
    }

    // HTML y SVG pueden traer scripts: se rechazan aunque parezcan documento o imagen.
    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/x-msdownload", ""})
    @NullSource
    void rechazaLosTiposQueNoEstanEnLaListaBlanca(String contentType) {
        assertThrows(TipoNoPermitidoException.class,
                () -> TipoArchivo.desdeContentType(contentType));
    }
}