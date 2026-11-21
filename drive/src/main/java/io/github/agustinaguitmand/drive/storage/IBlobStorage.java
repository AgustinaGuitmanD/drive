package io.github.agustinaguitmand.drive.storage;

import org.springframework.core.io.Resource;
import java.io.InputStream;

/**
 * Guarda y recupera el contenido (los bytes) de los archivos.
 * La metadata vive en la base; acá solo hay bytes identificados por una clave.
 * Hoy se implementa en disco local; en producción podría ser S3.
 */
public interface IBlobStorage {

    // Guarda los bytes y devuelve la clave con la que después se los encuentra.
    String guardar(InputStream contenido);

    // Devuelve los bytes guardados con esa clave, listos para mandar al navegador.
    Resource abrir(String storageKey);

    // Elimina los bytes guardados con esa clave.
    void borrar(String storageKey);
}