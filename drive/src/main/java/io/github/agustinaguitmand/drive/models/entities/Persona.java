package io.github.agustinaguitmand.drive.models.entities;

import io.github.agustinaguitmand.drive.exceptions.DatoInvalidoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

import static io.github.agustinaguitmand.drive.models.helpers.Validaciones.textoObligatorio;

@Entity
@Getter
public class Persona {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(nullable = false, length = 100)
    private String apellido;
    @Column(nullable = false, unique = true, length = 254)
    private String correo;

    public Persona(String nombre, String apellido, String correo) {

        this.id = UUID.randomUUID();
        this.nombre = textoObligatorio(nombre, "El nombre", 100);
        this.apellido = textoObligatorio(apellido, "El apellido", 100);
        this.correo = correoValido(correo);
    }

    // Solo para JPA: lo usa para crear el objeto al leerlo de la base.
    protected Persona() {
    }

    private static String correoValido(String correo) {
        textoObligatorio(correo, "El correo", 254);
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new DatoInvalidoException("El correo no tiene un formato válido");
        }
        return correo;
    }
}
