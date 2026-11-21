package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.DatoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchivoTest {

    private static final Instant AHORA = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant UN_MINUTO_DESPUES = AHORA.plusSeconds(60);

    @Test
    void alCrearloLasFechasDeCreacionYModificacionSonIguales() {
        Archivo archivo = archivoLlamado("foto.png");

        assertEquals(AHORA, archivo.getFechaCreacion());
        assertEquals(AHORA, archivo.getFechaModificacion());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void noSePuedeCrearConNombreVacio(String nombre) {
        assertThrows(DatoInvalidoException.class, () -> archivoLlamado(nombre));
    }

    @Test
    void noSePuedeCrearConNombreDeMasDe255Caracteres() {
        String nombreLargo = "a".repeat(256);

        assertThrows(DatoInvalidoException.class, () -> archivoLlamado(nombreLargo));
    }

    @Test
    void noSePuedeCrearConTamanioCero() {
        assertThrows(ArchivoInvalidoException.class, () ->
                new Archivo(UUID.randomUUID(), "foto.png", TipoArchivo.IMAGEN,
                        "image/png", 0, "clave", AHORA));
    }

    @Test
    void renombrarCambiaElNombreYLaFechaDeModificacion() {
        Archivo archivo = archivoLlamado("foto.png");

        archivo.renombrar("vacaciones.png", UN_MINUTO_DESPUES);

        assertEquals("vacaciones.png", archivo.getNombre());
        assertEquals(UN_MINUTO_DESPUES, archivo.getFechaModificacion());
        assertEquals(AHORA, archivo.getFechaCreacion());
    }

    @Test
    void unRenombreInvalidoNoModificaElArchivo() {
        Archivo archivo = archivoLlamado("foto.png");

        assertThrows(DatoInvalidoException.class, () -> archivo.renombrar("", UN_MINUTO_DESPUES));

        assertEquals("foto.png", archivo.getNombre());
        assertEquals(AHORA, archivo.getFechaModificacion());
    }

    // Arma un archivo válido cambiando solo el nombre, para no repetir el constructor en cada test.
    private static Archivo archivoLlamado(String nombre) {
        return new Archivo(UUID.randomUUID(), nombre, TipoArchivo.IMAGEN,
                "image/png", 100, "clave", AHORA);
    }
}