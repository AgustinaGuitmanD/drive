package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.controller.dto.PersonaResponse;
import io.github.agustinaguitmand.drive.controller.dto.RegistrarPersonaRequest;
import io.github.agustinaguitmand.drive.models.entities.Persona;
import io.github.agustinaguitmand.drive.services.IPersonaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {
    private final IPersonaService personaService;

    public PersonaController(IPersonaService personaService) {
        this.personaService = personaService;
    }

    @PostMapping
    public ResponseEntity<PersonaResponse> registrar(@Valid @RequestBody RegistrarPersonaRequest request) {
        Persona persona = personaService.registrar(request.nombre(), request.apellido(), request.correo());
        URI location = URI.create("/api/personas/" + persona.getId());
        return ResponseEntity.created(location).body(PersonaResponse.from(persona));
    }
}