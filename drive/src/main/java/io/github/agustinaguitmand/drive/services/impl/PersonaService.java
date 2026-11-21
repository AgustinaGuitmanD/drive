package io.github.agustinaguitmand.drive.services.impl;

import io.github.agustinaguitmand.drive.exceptions.CorreoDuplicadoException;
import io.github.agustinaguitmand.drive.exceptions.UsuarioNoRegistradoException;
import io.github.agustinaguitmand.drive.models.entities.Persona;
import io.github.agustinaguitmand.drive.repositories.IPersonaRepository;
import io.github.agustinaguitmand.drive.services.IPersonaService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PersonaService implements IPersonaService {
    private final IPersonaRepository personaRepository;

    public PersonaService(IPersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    @Override
    public Persona registrar(String nombre, String apellido, String correo) {
        if (personaRepository.existsByCorreo(correo)) {
            throw new CorreoDuplicadoException(correo);
        }
        return personaRepository.save(new Persona(nombre, apellido, correo));
    }

    @Override
    public void verificarQueExista(UUID personaId) {
        if (!personaRepository.existsById(personaId)) {
            throw new UsuarioNoRegistradoException(personaId);
        }
    }
}