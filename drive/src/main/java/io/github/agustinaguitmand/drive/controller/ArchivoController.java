package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.controller.dto.ArchivoResponse;
import io.github.agustinaguitmand.drive.controller.dto.RenombrarRequest;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.services.IArchivoService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {
    private final IArchivoService archivoService;

    public ArchivoController(IArchivoService archivoService) {
        this.archivoService = archivoService;
    }

    /**
     * Sube un nuevo archivo al sistema asociado a un usuario específico.
     *
     * @param usuarioId Identificador del usuario.
     * @param archivo   Archivo binario recibido en formato multipart/form-data.
     * @return Respuesta HTTP 201 (Created) con la ubicación del archivo en la cabecera Location
     *         y los datos del archivo creado en el cuerpo.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoResponse> subirArchivo(@RequestHeader("X-User-Id") UUID usuarioId,
                                                        @RequestParam("archivo") MultipartFile archivo) {
        Archivo creado = archivoService.subir(usuarioId, archivo);
        // Genera la ruta del nuevo recurso y responde con HTTP 201 Created
        URI location = URI.create("/api/archivos/" + creado.getId());
        return ResponseEntity.created(location).body(ArchivoResponse.from(creado));
    }

    /**
     * Obtiene el listado de todos los archivos pertenecientes a un usuario.
     *
     * @param usuarioId Identificador del usuario.
     * @return Lista de DTOs con la información de cada archivo.
     */
    @GetMapping
    public List<ArchivoResponse> listarArchivosUsuario(@RequestHeader("X-User-Id") UUID usuarioId) {
        return archivoService.listarArchivosUsuario(usuarioId).stream().map(ArchivoResponse::from).toList();
    }


    /**
     * Devuelve los datos de un archivo (nombre, tipo, tamaño, fechas) como JSON, sin el contenido.
     *
     * @param usuarioId Identificador del usuario propietario.
     * @param id        Identificador único del archivo.
     * @return DTO con los datos del archivo.
     */
    @GetMapping("/{id}")
    public ArchivoResponse obtenerMetadataArchivo(@RequestHeader("X-User-Id") UUID usuarioId,
                                   @PathVariable UUID id) {
        return ArchivoResponse.from(archivoService.obtenerMetadata(usuarioId, id));
    }

    /**
     * Devuelve el archivo en sí (los bytes de la foto o el PDF), no sus datos.
     * La metadata (nombre, tipo, fechas) va por GET /api/archivos/{id} como JSON;
     * el contenido va separado en /contenido para no mezclar los dos.
     * El navegador lo muestra en pantalla en vez de descargarlo (inline),
     * y sabe si es una imagen o un PDF por el Content-Type.
     *
     * @param usuarioId Identificador del usuario.
     * @param id        Identificador único del archivo.
     * @return Respuesta HTTP 200 (OK) con el recurso binario en el cuerpo.
     */
    @GetMapping("/{id}/contenido")
    public ResponseEntity<Resource> obtenerContenido(@RequestHeader("X-User-Id") UUID usuarioId,
                                                     @PathVariable UUID id) {
        ContenidoArchivo contenido = archivoService.obtenerContenido(usuarioId, id);
        return ResponseEntity.ok()
                .contentType(contenido.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION, mostrarEnNavegador(contenido.nombre()))
                // El navegador respeta el Content-Type declarado y no intenta adivinar otro.
                // Deshabilita el MIME-sniffing del navegador para evitar que interprete archivos
                // estáticos como código ejecutable (prevención de XSS).
                .header("X-Content-Type-Options", "nosniff")
                .body(contenido.recurso());
    }


    /**
     * Actualiza parcialmente un archivo para cambiar su nombre.
     * Incluye validación de versión (control de concurrencia optimista) para evitar
     * sobrescribir cambios si el archivo fue modificado previamente.
     *
     * @param usuarioId Identificador del usuario propietario.
     * @param id        Identificador único del archivo a renombrar.
     * @param request   Cuerpo de la petición con el nuevo nombre y la versión esperada.
     * @return DTO actualizado con los nuevos datos del archivo.
     */
    @PatchMapping("/{id}")
    public ArchivoResponse renombrar(@RequestHeader("X-User-Id") UUID usuarioId,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody RenombrarRequest request) {
        return ArchivoResponse.from(archivoService.renombrar(usuarioId, id, request.nombre(), request.version()));
    }

    /**
     * Elimina un archivo perteneciente al usuario.
     *
     * @param usuarioId Identificador del usuario propietario.
     * @param id        Identificador único del archivo a eliminar.
     * @return Respuesta HTTP 204 (No Content) sin cuerpo si la eliminación fue exitosa.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(@RequestHeader("X-User-Id") UUID usuarioId,
                                       @PathVariable UUID id) {
        archivoService.borrar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Construye el valor de la cabecera Content-Disposition en modo 'inline'
     * codificado en UTF-8 para soportar correctamente tildes, ñ y caracteres especiales.
     *
     * @param nombreArchivo Nombre original del archivo.
     * @return Cadena formateada lista para usarse en la cabecera HTTP.
     */
    private String mostrarEnNavegador(String nombreArchivo) {
        return ContentDisposition.inline()
                .filename(nombreArchivo, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
