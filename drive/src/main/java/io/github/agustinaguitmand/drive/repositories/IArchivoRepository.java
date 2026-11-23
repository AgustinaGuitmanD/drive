package io.github.agustinaguitmand.drive.repositories;

import io.github.agustinaguitmand.drive.models.entities.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IArchivoRepository extends JpaRepository<Archivo, UUID> {
    Optional<Archivo> findByIdAndDuenioId(UUID id, UUID duenioId);
    boolean existsByDuenioIdAndNombre(UUID duenioId, String nombre);
    List<Archivo> findByDuenioIdOrderByFechaModificacionDesc(UUID duenioId);
    // Los storageKey de todas las revisiones del archivo: uno por cada contenido guardado en disco.
    @Query("select r.storageKey from RevisionArchivo r where r.archivo.id = :archivoId")
    List<String> findStorageKeysDeRevisiones(@Param("archivoId") UUID archivoId);
}
