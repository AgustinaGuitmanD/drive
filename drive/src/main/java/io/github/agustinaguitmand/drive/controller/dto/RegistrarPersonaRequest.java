package io.github.agustinaguitmand.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistrarPersonaRequest(@NotBlank String nombre,
                                      @NotBlank String apellido,
                                      @NotBlank String correo) { }