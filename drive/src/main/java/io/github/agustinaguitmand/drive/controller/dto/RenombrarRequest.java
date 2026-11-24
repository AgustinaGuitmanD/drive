package io.github.agustinaguitmand.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * El JSON que manda el cliente en el PATCH /api/archivos/{id} para renombrar.
 * Las anotaciones se validan antes de llegar al servicio (por el @Valid del controller):
 * si alguna no se cumple, se responde 400 sin buscar el archivo ni hacer ningún trabajo.
 */
public record RenombrarRequest(

        // Mismo límite que Archivo, así un nombre inválido se rechaza en la entrada.
        @NotBlank @Size(max = 255) String nombre,

        // La versión que el cliente leyó. Si otro modificó el archivo mientras tanto,
        // no coincide y se responde 409 en lugar de pisar ese cambio.
        @NotNull Long version) { }