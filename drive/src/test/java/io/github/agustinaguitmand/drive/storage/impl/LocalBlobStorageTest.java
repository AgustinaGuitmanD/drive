package io.github.agustinaguitmand.drive.storage.impl;

import io.github.agustinaguitmand.drive.storage.BlobGuardado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalBlobStorageTest {

    @TempDir
    Path carpeta;

    LocalBlobStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalBlobStorage(carpeta.toString());
    }

    @Test
    void guardarCalculaElSha256YElTamanioDeLoEscrito() {
        BlobGuardado blob = storage.guardar(contenido("hola"));

        // El valor esperado se calculó por fuera (usando sha256sum), así el test verifica
        // que el hash se calcula sobre los bytes que se escribieron, y no sobre otra cosa.
        assertEquals("b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79", blob.sha256());
        assertEquals(4, blob.tamanio());
    }

    @Test
    void loQueSeGuardaSeAbreConLosMismosBytes() throws IOException {
        BlobGuardado blob = storage.guardar(contenido("hola"));

        byte[] leido = storage.abrir(blob.storageKey()).getContentAsByteArray();

        assertArrayEquals("hola".getBytes(), leido);
    }

    @Test
    void dosContenidosIgualesTienenElMismoChecksumPeroSeGuardanPorSeparado() {
        BlobGuardado primero = storage.guardar(contenido("hola"));
        BlobGuardado segundo = storage.guardar(contenido("hola"));

        // Mismo checksum
        assertEquals(primero.sha256(), segundo.sha256());
        // cada subida ocupa su propio lugar en disco
        assertNotEquals(primero.storageKey(), segundo.storageKey());
    }

    @Test
    void borrarEliminaElContenido() {
        BlobGuardado blob = storage.guardar(contenido("hola"));

        storage.borrar(blob.storageKey());

        assertThrows(IllegalStateException.class, () -> storage.abrir(blob.storageKey()));
    }

    @Test
    void borrarUnContenidoQueNoExisteNoFalla() {
        assertDoesNotThrow(() -> storage.borrar("clave-que-no-existe"));
    }


    private static InputStream contenido(String texto) {
        return new ByteArrayInputStream(texto.getBytes());
    }
}