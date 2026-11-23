package io.github.agustinaguitmand.drive.models;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Lo que devuelve el servicio al pedir el contenido de un archivo:
 * su nombre, su tipo (imagen, PDF...), su checksum y los bytes para mandar al navegador.
 */
public record ContenidoArchivo(String nombre, MediaType tipo, Resource recurso, String checksum) { }