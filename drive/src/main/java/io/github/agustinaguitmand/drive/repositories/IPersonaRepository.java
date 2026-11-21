package io.github.agustinaguitmand.drive.repositories;

import io.github.agustinaguitmand.drive.models.entities.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IPersonaRepository extends JpaRepository<Persona, UUID> {
    boolean existsByCorreo(String correo);
}