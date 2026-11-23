package io.github.agustinaguitmand.drive.storage.impl;
import io.github.agustinaguitmand.drive.storage.BlobGuardado;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    public BlobGuardado guardar(InputStream contenido) {
        String storageKey = UUID.randomUUID().toString();
        MessageDigest sha256 = nuevoSha256();
        // DigestInputStream calcula el hash a medida que Files.copy lee los bytes.
        try (DigestInputStream entrada = new DigestInputStream(contenido, sha256)) {
            long tamanio = Files.copy(entrada, ubicacionDe(storageKey));
            return new BlobGuardado(storageKey, HexFormat.of().formatHex(sha256.digest()), tamanio);
        } catch (IOException e) {
            borrarSinFallar(storageKey); // si la copia se cortó a la mitad, no dejar un archivo incompleto
            throw new UncheckedIOException("No se pudo guardar el contenido", e);
        }
    }

    private static MessageDigest nuevoSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM está obligada a tener SHA-256; si falta, es un error del entorno.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // Se usa cuando falló la copia: intenta borrar el archivo a medio escribir.
    // Si tampoco se puede borrar, no se lanza un error nuevo para no tapar el error original de la copia.
    private void borrarSinFallar(String storageKey) {
        try {
            Files.deleteIfExists(ubicacionDe(storageKey));
        } catch (IOException e) {
            // Se ignora a propósito: el error importante es el de la copia, que se lanza en guardar().
        }
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