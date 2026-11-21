package io.github.agustinaguitmand.drive.services.impl;

import io.github.agustinaguitmand.drive.exceptions.ArchivoNoEncontradoException;
import io.github.agustinaguitmand.drive.exceptions.UsuarioNoRegistradoException;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;
import io.github.agustinaguitmand.drive.repositories.IArchivoRepository;
import io.github.agustinaguitmand.drive.services.IPersonaService;
import io.github.agustinaguitmand.drive.storage.IBlobStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchivoServiceTest {
    @Mock IArchivoRepository archivoRepository;
    @Mock IBlobStorage blobStorage;
    @Mock IPersonaService personaService;

    // Se fija el reloj para evitar fallos intermitentes por diferencias de milisegundos al
    // validar timestamps de creación/modificación.
    Clock clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);
    ArchivoService service;

    @BeforeEach
    void setUp() {
        service = new ArchivoService(archivoRepository, blobStorage, personaService, clock);
    }

    @Test
    void noSePuedeSubirUnArchivoSiElUsuarioNoEstaRegistrado() {
        UUID desconocido = UUID.randomUUID();
        doThrow(new UsuarioNoRegistradoException(desconocido))
                .when(personaService).verificarQueExista(desconocido);
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3});

        assertThrows(UsuarioNoRegistradoException.class, () -> service.subir(desconocido, foto));

        // No se escribió nada (ni en disco ni en la base)
        verifyNoInteractions(blobStorage);
        verify(archivoRepository, never()).save(any());
    }


    @Test
    void alSubirElArchivoQuedaGuardadoConElIdDelUsuarioQueLoSubio() {
        UUID usuario = UUID.randomUUID();
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3});
        when(blobStorage.guardar(any())).thenReturn("clave-1");
        when(archivoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.subir(usuario, foto);

        ArgumentCaptor<Archivo> guardado = ArgumentCaptor.forClass(Archivo.class);
        verify(archivoRepository).save(guardado.capture());
        assertEquals(usuario, guardado.getValue().getDuenioId());
    }

    @Test
    void unUsuarioNoPuedeVerElContenidoDeUnArchivoDeOtro() {
        UUID otroUsuario = UUID.randomUUID();
        UUID archivoId = UUID.randomUUID();
        when(archivoRepository.findByIdAndDuenioId(archivoId, otroUsuario))
             .thenReturn(Optional.empty());

        assertThrows(ArchivoNoEncontradoException.class,
                () -> service.obtenerContenido(otroUsuario, archivoId));

        // No se llegó a leer ningún byte del storage.
        verifyNoInteractions(blobStorage);
    }

    @Test
    void unUsuarioPuedeVerElContenidoDeSuArchivo() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = new Archivo(usuario, "foto.png", TipoArchivo.IMAGEN,
                "image/png", 3, "clave-1", Instant.now(clock));
        Resource bytes = new ByteArrayResource(new byte[]{1, 2, 3});
        when(archivoRepository.findByIdAndDuenioId(archivo.getId(), usuario))
                .thenReturn(Optional.of(archivo));
        when(blobStorage.abrir("clave-1")).thenReturn(bytes);

        ContenidoArchivo contenido = service.obtenerContenido(usuario, archivo.getId());

        assertEquals("foto.png", contenido.nombre());
        assertEquals(MediaType.IMAGE_PNG, contenido.tipo());
        assertSame(bytes, contenido.recurso());
    }


    @Test
    void siSeSubeUnArchivoConUnNombreQueYaExisteSeGuardaNumerado() {
        UUID usuario = UUID.randomUUID();
        // El usuario ya tiene un "foto.png" guardado.
        when(archivoRepository.existsByDuenioIdAndNombre(usuario, "foto.png")).thenReturn(true);
        when(blobStorage.guardar(any())).thenReturn("clave-2");
        when(archivoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile otraFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6});

        Archivo subido = service.subir(usuario, otraFoto);

        assertEquals("foto (1).png", subido.getNombre());
    }
}