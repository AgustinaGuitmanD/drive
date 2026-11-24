package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.ArchivoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.DatoInvalidoException;
import io.github.agustinaguitmand.drive.exceptions.RevisionNoEncontradaException;
import io.github.agustinaguitmand.drive.exceptions.TipoNoPermitidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
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
                        "image/png", 0, "clave", AHORA, "checksum-1"));
    }

    @Test
    void unArchivoNuevoNaceConSuRevision1() {
        Archivo archivo = archivoLlamado("foto.png");

        assertEquals(1, archivo.getRevisionActual());
        assertEquals("clave-1", archivo.revision(1).getStorageKey());
        assertEquals(1, archivo.ultimasRevisiones(10).size());
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

    @Test
    void agregarUnaRevisionLaConvierteEnLaActual() {
        Archivo archivo = archivoLlamado("foto.png");

        agregarRevision(archivo, 2);

        assertEquals(2, archivo.getRevisionActual());
        assertEquals("clave-2", archivo.getStorageKey());
        assertEquals("checksum-2", archivo.getChecksum());
        assertEquals(200, archivo.getTamanio());
        assertEquals(AHORA.plusSeconds(120), archivo.getFechaModificacion());
        assertEquals(AHORA, archivo.getFechaCreacion());
    }

    @Test
    void laRevisionAnteriorConservaSuContenido() {
        Archivo archivo = archivoLlamado("foto.png");

        agregarRevision(archivo, 2);

        RevisionArchivo primera = archivo.revision(1);
        assertEquals("clave-1", primera.getStorageKey());
        assertEquals("checksum-1", primera.getChecksum());
    }

    @Test
    void ultimasRevisionesDevuelveLasMasNuevasPrimeroHastaElLimite() {
        Archivo archivo = archivoLlamado("foto.png");
        agregarRevision(archivo, 2);
        agregarRevision(archivo, 3);

        List<Integer> numeros = archivo.ultimasRevisiones(2).stream()
                .map(RevisionArchivo::getNumero)
                .toList();

        assertEquals(List.of(3, 2), numeros);
    }

    @Test
    void pedirUnaRevisionQueNoExisteLanzaExcepcion() {
        Archivo archivo = archivoLlamado("foto.png");

        assertThrows(RevisionNoEncontradaException.class, () -> archivo.revision(9));
    }

    @Test
    void unaImagenNoPuedeRecibirUnaRevisionDeOtroFormatoDeImagen() {
        Archivo foto = new Archivo(UUID.randomUUID(), "foto.png", TipoArchivo.IMAGEN,
                "image/png", 100, "clave-1", AHORA, "checksum-1");

        assertThrows(ArchivoInvalidoException.class, () -> foto.agregarRevision(
                "clave-2", "checksum-2", 200, "image/jpeg", UN_MINUTO_DESPUES));
    }

    @Test
    void unPdfNoPuedeRecibirUnaRevisionDeTextoPlano() {
        Archivo cv = new Archivo(UUID.randomUUID(), "cv.pdf", TipoArchivo.DOCUMENTO,
                "application/pdf", 100, "clave-1", AHORA, "checksum-1");

        assertThrows(ArchivoInvalidoException.class, () -> cv.agregarRevision(
                "clave-2", "checksum-2", 200, "text/plain", UN_MINUTO_DESPUES));
    }

    @Test
    void unaRevisionDeUnFormatoNoPermitidoSeRechaza() {
        Archivo archivo = archivoLlamado("foto.png");

        assertThrows(TipoNoPermitidoException.class, () -> archivo.agregarRevision(
                "clave-2", "checksum-2", 200, "text/html", UN_MINUTO_DESPUES));
    }

    @Test
    void unaRevisionVaciaSeRechaza() {
        Archivo archivo = archivoLlamado("foto.png");

        assertThrows(ArchivoInvalidoException.class, () -> archivo.agregarRevision(
                "clave-2", "checksum-2", 0, "image/png", UN_MINUTO_DESPUES));
    }

    @Test
    void unaRevisionRechazadaNoModificaElArchivo() {
        Archivo archivo = archivoLlamado("foto.png");

        assertThrows(ArchivoInvalidoException.class, () -> archivo.agregarRevision(
                "clave-2", "checksum-2", 200, "image/jpeg", UN_MINUTO_DESPUES));

        assertEquals(1, archivo.getRevisionActual());
        assertEquals("clave-1", archivo.getStorageKey());
        assertEquals(AHORA, archivo.getFechaModificacion());
    }
    // Arma un archivo válido cambiando solo el nombre, para no repetir el constructor en cada test.
    private static Archivo archivoLlamado(String nombre) {
        return new Archivo(UUID.randomUUID(), nombre, TipoArchivo.IMAGEN,
                "image/png", 100, "clave-1", AHORA, "checksum-1");
    }

    private static void agregarRevision(Archivo archivo, int numero) {
        archivo.agregarRevision("clave-" + numero, "checksum-" + numero, 200,
                "image/png", AHORA.plusSeconds(60L * numero));
    }
}