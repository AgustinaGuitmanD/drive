package io.github.agustinaguitmand.drive.models.helpers;

/**
 * Construye el nombre alternativo que usa el sistema cuando se sube un archivo duplicado
 *  (por ejemplo, si se sube 2 veces "foto.png" la segunda pasa a llamarse "foto (1).png").
 */
// final para que nadie pueda heredar de ella, como solo tiene métodos static,
// no guarda datos y no se crean objetos de ella.
public final class NombresDeArchivo {
    private NombresDeArchivo() { }

    public static String conNumero(String nombre, int numero) {

        int punto = nombre.lastIndexOf('.');
        // Sin extensión, o un nombre que empieza con punto (".gitignore"): el número va al final.
        if (punto <= 0) {
            return nombre + " (" + numero + ")";
        }
        return nombre.substring(0, punto) + " (" + numero + ")" + nombre.substring(punto);
    }
}
