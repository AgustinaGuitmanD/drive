package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.controller.dto.ArchivoResponse;
import io.github.agustinaguitmand.drive.controller.dto.RenombrarRequest;
import io.github.agustinaguitmand.drive.controller.dto.RevisionResponse;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.services.IArchivoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // Identifica al usuario. Es una simplificación del prototipo: en producción
    // el id saldría de un token autenticado, no de un header que manda el cliente.
    private static final String USUARIO = "X-User-Id";

    private final IArchivoService archivoService;

    public ArchivoController(IArchivoService archivoService) {
        this.archivoService = archivoService;
    }

    /**
     * Sube un nuevo archivo al sistema asociado a un usuario específico.
     * Responde con un HTTP 201 (Created) con la ubicación del archivo en la cabecera Location
     *  y los datos del archivo creado en el cuerpo.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoResponse> subirArchivo(@RequestHeader(USUARIO) UUID usuarioId,
                                                        @RequestParam("archivo") MultipartFile archivo) {
        Archivo creado = archivoService.subir(usuarioId, archivo);
        // Genera la ruta del nuevo recurso y responde con HTTP 201 Created
        URI location = URI.create("/api/archivos/" + creado.getId());
        return ResponseEntity.created(location).body(ArchivoResponse.from(creado));
    }

    /**
     * Obtiene el listado de todos los archivos pertenecientes a un usuario.
     * Retorna una lista de DTOs con la información de cada archivo.
     */
    @GetMapping
    public List<ArchivoResponse> listarArchivosUsuario(@RequestHeader(USUARIO) UUID usuarioId) {
        return archivoService.listarArchivosUsuario(usuarioId).stream()
                             .map(ArchivoResponse::from)
                             .toList();
    }


    /**
     * Retorna solo los datos del archivo (JSON). Los bytes van por /contenido.
     */
    @GetMapping("/{id}")
    public ArchivoResponse obtenerMetadataArchivo(@RequestHeader(USUARIO) UUID usuarioId,
                                   @PathVariable UUID id) {
        return ArchivoResponse.from(archivoService.obtenerMetadata(usuarioId, id));
    }

    /**
     * Devuelve el archivo en sí (los bytes de la foto o el PDF), no sus datos.
     * El navegador lo muestra en pantalla en vez de descargarlo (inline).
     */
    @GetMapping("/{id}/contenido")
    public ResponseEntity<Resource> obtenerContenido(@RequestHeader(USUARIO) UUID usuarioId,
                                                     @PathVariable UUID id) {
        return respuestaConContenido(archivoService.obtenerContenido(usuarioId, id));
    }

    /**
     * Actualiza parcialmente un archivo para cambiar su nombre.
     * Si alguien modificó el archivo en el medio, responde 409 en lugar de pisar ese cambio.
     * Renombrar no crea una revisión.
     */
    @PatchMapping("/{id}")
    public ArchivoResponse renombrar(@RequestHeader(USUARIO) UUID usuarioId,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody RenombrarRequest request) {
        return ArchivoResponse.from(archivoService.renombrar(usuarioId, id, request.nombre(), request.version()));
    }

    /**
     * Elimina un archivo perteneciente al usuario (todas sus versiones).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(@RequestHeader(USUARIO) UUID usuarioId,
                                       @PathVariable UUID id) {
        archivoService.borrar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    /** Crea una revisión con contenido nuevo. Igual que al renombrar, "version" evita pisar
     * un cambio ajeno: la primera modificación gana y la otra recibe 409.
     * Devuelve el archivo actualizado.
     */
    @PostMapping(value = "/{id}/revisiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoResponse> subirRevision(@RequestHeader(USUARIO) UUID usuarioId,
                                                         @PathVariable UUID id,
                                                         @RequestParam("archivo") MultipartFile archivo,
                                                         @RequestParam("version") long version) {
        Archivo actualizado = archivoService.subirRevision(usuarioId, id, archivo, version);
        URI location = URI.create("/api/archivos/" + id + "/revisiones/"
                + actualizado.getRevisionActual() + "/contenido");
        return ResponseEntity.created(location).body(ArchivoResponse.from(actualizado));
    }

    /**
    *  Historial de revisiones, de la más nueva a la más vieja.
    */
    @GetMapping("/{id}/revisiones")
    public List<RevisionResponse> listarRevisiones(@RequestHeader(USUARIO) UUID usuarioId,
                                                   @PathVariable UUID id,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite) {
        return archivoService.listarRevisiones(usuarioId, id, limite).stream()
                .map(RevisionResponse::from)
                .toList();
    }

    /**
     * Devuelve el contenido de una versión en particular
     */
    @GetMapping("/{id}/revisiones/{numero}/contenido")
    public ResponseEntity<Resource> obtenerContenidoDeRevision(@RequestHeader(USUARIO) UUID usuarioId,
                                                               @PathVariable UUID id,
                                                               @PathVariable int numero) {
        return respuestaConContenido(archivoService.obtenerContenidoDeRevision(usuarioId, id, numero));
    }

    /**
     * Construye el valor de la cabecera Content-Disposition en modo 'inline'
     * codificado en UTF-8 para soportar correctamente tildes, ñ y caracteres especiales.
     */
    private String mostrarEnNavegador(String nombreArchivo) {
        return ContentDisposition.inline()
                .filename(nombreArchivo, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private ResponseEntity<Resource> respuestaConContenido(ContenidoArchivo contenido) {
        return ResponseEntity.ok()
                .contentType(contenido.tipo())
                .header(HttpHeaders.CONTENT_DISPOSITION, mostrarEnNavegador(contenido.nombre()))
                // Que el navegador respete el Content-Type y no lo "adivine":
                // evita que un archivo subido se ejecute como HTML o script.
                .header("X-Content-Type-Options", "nosniff")
                .eTag(contenido.checksum())
                .body(contenido.recurso());
    }
}
