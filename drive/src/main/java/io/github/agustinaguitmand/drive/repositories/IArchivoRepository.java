package io.github.agustinaguitmand.drive.repositories;

import io.github.agustinaguitmand.drive.models.entities.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IArchivoRepository extends JpaRepository<Archivo, UUID> {
    Optional<Archivo> findByIdAndDuenioId(UUID id, UUID duenioId);
    boolean existsByDuenioIdAndNombre(UUID duenioId, String nombre);
    List<Archivo> findByDuenioIdOrderByFechaModificacionDesc(UUID duenioId);
}
