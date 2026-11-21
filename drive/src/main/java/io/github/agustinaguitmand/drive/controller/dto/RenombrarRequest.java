package io.github.agustinaguitmand.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del PATCH /api/archivos/{id}.
 * La versión es la que el cliente leyó; si no coincide con la actual, otro lo modificó antes.
 */
public record RenombrarRequest(@NotBlank @Size(max = 255) String nombre,
                               @NotNull Long version) { }
