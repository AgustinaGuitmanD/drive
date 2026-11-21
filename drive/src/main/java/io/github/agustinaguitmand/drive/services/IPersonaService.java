package io.github.agustinaguitmand.drive.services;

import io.github.agustinaguitmand.drive.models.entities.Persona;

import java.util.UUID;

public interface IPersonaService {
    Persona registrar(String nombre, String apellido, String correo);
    void verificarQueExista(UUID personaId);
}