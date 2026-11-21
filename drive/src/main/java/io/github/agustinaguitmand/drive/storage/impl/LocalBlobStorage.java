package io.github.agustinaguitmand.drive.storage.impl;
import io.github.agustinaguitmand.drive.storage.IBlobStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Implementación de BlobStorage que guarda cada contenido como un archivo
 * dentro de una carpeta del disco. El nombre de cada archivo es su storageKey.
 */
@Component
public class LocalBlobStorage implements IBlobStorage {

    private final Path directorio;

    public LocalBlobStorage(@Value("${drive.storage.dir:storage}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath();
        crearDirectorioSiNoExiste();
    }

    @Override
    public String guardar(InputStream contenido) {
        // nombre con el que se guarda el archivo en el disco, distinto del nombre que ve el usuario
        String storageKey = UUID.randomUUID().toString();
        try (contenido) {

            Files.copy(contenido, ubicacionDe(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el contenido", e);
        }
        return storageKey;
    }

    @Override
    public Resource abrir(String storageKey) {
        Path ubicacion = ubicacionDe(storageKey);
        if (!Files.exists(ubicacion)) {
            throw new IllegalStateException("Falta el contenido en disco: " + storageKey);
        }
        return new FileSystemResource(ubicacion);
    }

    @Override
    public void borrar(String storageKey) {
        try {
            Files.deleteIfExists(ubicacionDe(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el contenido", e);
        }
    }

    private Path ubicacionDe(String storageKey) {
        return directorio.resolve(storageKey);
    }

    private void crearDirectorioSiNoExiste() {
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio " + directorio, e);
        }
    }
}