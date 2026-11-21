package io.github.agustinaguitmand.drive.controller.dto;

import io.github.agustinaguitmand.drive.models.entities.Persona;

import java.util.UUID;

public record PersonaResponse(UUID id, String nombre, String apellido, String correo) {
    public static PersonaResponse from(Persona p) {
        return new PersonaResponse(p.getId(), p.getNombre(), p.getApellido(), p.getCorreo());
    }
}