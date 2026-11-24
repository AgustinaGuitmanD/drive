package io.github.agustinaguitmand.drive.services.impl;

import io.github.agustinaguitmand.drive.exceptions.*;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.RevisionArchivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;
import io.github.agustinaguitmand.drive.repositories.IArchivoRepository;
import io.github.agustinaguitmand.drive.services.IPersonaService;
import io.github.agustinaguitmand.drive.storage.BlobGuardado;
import io.github.agustinaguitmand.drive.storage.IBlobStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
        when(blobStorage.guardar(any())).thenReturn(new BlobGuardado("clave-1", "checksum-1", 3));
        when(archivoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.subir(usuario, foto);

        ArgumentCaptor<Archivo> guardado = ArgumentCaptor.forClass(Archivo.class);
        verify(archivoRepository).save(guardado.capture());
        assertEquals(usuario, guardado.getValue().getDuenioId());
    }

    @Test
    void siLaBaseRechazaElNombreSeBorranLosBytesYSeInformaDuplicado() {
        UUID usuario = UUID.randomUUID();
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{1, 2, 3});
        when(blobStorage.guardar(any())).thenReturn(new BlobGuardado("clave-1", "checksum-1", 3));
        // Otra subida simultánea tomó el nombre entre la verificación y el guardado.
        when(archivoRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("nombre duplicado"));

        assertThrows(NombreDuplicadoException.class, () -> service.subir(usuario, foto));

        // No quedan bytes huérfanos.
        verify(blobStorage).borrar("clave-1");
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
                "image/png", 3, "clave-1", Instant.now(clock),
                "checksum-1");
        Resource bytes = new ByteArrayResource(new byte[]{1, 2, 3});
        when(archivoRepository.findByIdAndDuenioId(archivo.getId(), usuario))
                .thenReturn(Optional.of(archivo));
        when(blobStorage.abrir("clave-1")).thenReturn(bytes);

        ContenidoArchivo contenido = service.obtenerContenido(usuario, archivo.getId());

        assertEquals("foto.png", contenido.nombre());
        assertEquals(MediaType.IMAGE_PNG, contenido.tipo());
        assertSame(bytes, contenido.recurso());
        assertEquals("checksum-1", contenido.checksum());
    }


    @Test
    void siSeSubeUnArchivoConUnNombreQueYaExisteSeGuardaNumerado() {
        UUID usuario = UUID.randomUUID();
        // El usuario ya tiene un "foto.png" guardado.
        when(archivoRepository.existsByDuenioIdAndNombre(usuario, "foto.png")).thenReturn(true);
        when(blobStorage.guardar(any())).thenReturn(new BlobGuardado("clave-2", "checksum-2", 3));
        when(archivoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile otraFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6});

        Archivo subido = service.subir(usuario, otraFoto);

        assertEquals("foto (1).png", subido.getNombre());
    }

    @Test
    void borrarEliminaPrimeroLaMetadataYDespuesElContenido() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);

        service.borrar(usuario, archivo.getId());

        InOrder orden = inOrder(archivoRepository, blobStorage);
        orden.verify(archivoRepository).delete(archivo);
        orden.verify(blobStorage).borrar("clave-1");
    }

    @Test
    void siFallaElBorradoEnDiscoLaMetadataQuedaBorradaIgual() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        doThrow(new UncheckedIOException(new IOException("disco no disponible")))
                .when(blobStorage).borrar("clave-1");

        assertDoesNotThrow(() -> service.borrar(usuario, archivo.getId()));

        verify(archivoRepository).delete(archivo);
    }

    @Test
    void unUsuarioNoPuedeBorrarUnArchivoDeOtro() {
        UUID otroUsuario = UUID.randomUUID();
        UUID archivoId = UUID.randomUUID();
        when(archivoRepository.findByIdAndDuenioId(archivoId, otroUsuario))
                .thenReturn(Optional.empty());

        assertThrows(ArchivoNoEncontradoException.class,
                () -> service.borrar(otroUsuario, archivoId));

        verify(archivoRepository, never()).delete(any());
        verifyNoInteractions(blobStorage);
    }


    @Test
    void subirUnaRevisionLaGuardaComoLaActual() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});
        when(blobStorage.guardar(any())).thenReturn(new BlobGuardado("clave-2", "checksum-2", 4));
        when(archivoRepository.saveAndFlush(archivo)).thenReturn(archivo);

        Archivo actualizado = service.subirRevision(usuario, archivo.getId(), nuevaFoto, 0);

        assertEquals(2, actualizado.getRevisionActual());
        assertEquals("clave-2", actualizado.getStorageKey());
        assertEquals("checksum-2", actualizado.getChecksum());
        assertEquals(4, actualizado.getTamanio());
    }

    @Test
    void subirUnaRevisionConUnaVersionViejaSeRechazaSinEscribirEnDisco() {
        // El usuario leyó el archivo en su versión actual.
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        long versionQueLeyoElUsuario = archivo.getVersion();

        // Mientras tanto, otra persona lo modificó: la versión avanzó.
        // (En producción la avanza JPA al guardar; acá no hay base de datos, así que se simula.)
        ReflectionTestUtils.setField(archivo, "version", versionQueLeyoElUsuario + 1);

        // El usuario intenta subir su revisión con la versión que había leído, que ya quedó vieja.
        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        assertThrows(ConflictoDeVersionException.class,
                () -> service.subirRevision(usuario, archivo.getId(), nuevaFoto, versionQueLeyoElUsuario));

        // Se rechazó antes de guardar nada: no hay bytes que limpiar.
        verifyNoInteractions(blobStorage);
    }

    @Test
    void subirUnaRevisionDeOtroFormatoSeRechazaSinEscribirEnDisco() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        MockMultipartFile fotoJpeg = new MockMultipartFile(
                "archivo", "foto.jpg", "image/jpeg", new byte[]{4, 5, 6, 7});

        assertThrows(ArchivoInvalidoException.class,
                () -> service.subirRevision(usuario, archivo.getId(), fotoJpeg, 0));

        verifyNoInteractions(blobStorage);
    }

    @Test
    void siOtraSubidaSimultaneaGuardaPrimeroSeInformaConflictoYSeBorranLosBytesEscritos() {
        // El usuario sube una revisión con la versión correcta, así que pasa la verificación...
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        long versionActual = archivo.getVersion();
        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        // ...y sus bytes se escriben en disco.
        String claveDeLosBytesEscritos = "clave-2";
        when(blobStorage.guardar(any()))
                .thenReturn(new BlobGuardado(claveDeLosBytesEscritos, "checksum-2", 4));

        // Pero otra subida que llegó al mismo tiempo guardó primero. Al guardar, JPA detecta
        // que el archivo cambió desde que se leyó (por @Version) y lanza esta excepción.
        when(archivoRepository.saveAndFlush(archivo))
                .thenThrow(new ObjectOptimisticLockingFailureException(Archivo.class, archivo.getId()));

        assertThrows(ConflictoDeVersionException.class,
                () -> service.subirRevision(usuario, archivo.getId(), nuevaFoto, versionActual));

        // Los bytes que se alcanzaron a escribir se borran: no quedan huérfanos.
        verify(blobStorage).borrar(claveDeLosBytesEscritos);
    }


    @Test
    void unUsuarioNoPuedeSubirUnaRevisionAUnArchivoDeOtro() {
        UUID otroUsuario = UUID.randomUUID();
        UUID archivoId = UUID.randomUUID();
        when(archivoRepository.findByIdAndDuenioId(archivoId, otroUsuario))
                .thenReturn(Optional.empty());
        MockMultipartFile foto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        assertThrows(ArchivoNoEncontradoException.class,
                () -> service.subirRevision(otroUsuario, archivoId, foto, 0));

        verifyNoInteractions(blobStorage);
    }


    @Test
    void listarRevisionesDevuelveLasMasNuevasRespetandoElLimite() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        archivo.agregarRevision("clave-2", "checksum-2", 4, "image/png", Instant.now(clock));
        archivo.agregarRevision("clave-3", "checksum-3", 5, "image/png", Instant.now(clock));

        List<Integer> numeros = service.listarRevisiones(usuario, archivo.getId(), 2).stream()
                                       .map(RevisionArchivo::getNumero)
                                       .toList();

        assertEquals(List.of(3, 2), numeros);
    }


    @Test
    void obtenerUnaRevisionViejaDevuelveSuContenidoYNoElActual() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        archivo.agregarRevision("clave-2", "checksum-2", 4, "image/png", Instant.now(clock));
        Resource bytesDeLaRevision1 = new ByteArrayResource(new byte[]{1, 2, 3});
        when(blobStorage.abrir("clave-1")).thenReturn(bytesDeLaRevision1);

        ContenidoArchivo contenido = service.obtenerContenidoDeRevision(usuario, archivo.getId(), 1);

        assertSame(bytesDeLaRevision1, contenido.recurso());
        assertEquals("checksum-1", contenido.checksum());
        assertEquals("foto.png", contenido.nombre());
    }

    @Test
    void obtenerUnaRevisionQueNoExisteSeRechazaSinLeerElDisco() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);

        assertThrows(RevisionNoEncontradaException.class,
                () -> service.obtenerContenidoDeRevision(usuario, archivo.getId(), 9));

        verifyNoInteractions(blobStorage);
    }


    @Test
    void borrarEliminaLosBytesDeTodasLasRevisiones() {
        UUID usuario = UUID.randomUUID();
        Archivo archivo = archivoGuardado(usuario);
        when(archivoRepository.findStorageKeysDeRevisiones(archivo.getId()))
                .thenReturn(List.of("clave-1", "clave-2"));

        service.borrar(usuario, archivo.getId());

        verify(blobStorage).borrar("clave-1");
        verify(blobStorage).borrar("clave-2");
    }


    // Metodo auxiliar: Crea un archivo del usuario, creado una hora antes del reloj del test,
    // y programa el repositorio para que lo encuentre.
    private Archivo archivoGuardado(UUID usuario) {
        Instant unaHoraAntes = Instant.now(clock).minusSeconds(3600);
        Archivo archivo = new Archivo(usuario, "foto.png", TipoArchivo.IMAGEN,
                "image/png", 3, "clave-1", unaHoraAntes, "checksum-1");
        when(archivoRepository.findByIdAndDuenioId(archivo.getId(), usuario))
                .thenReturn(Optional.of(archivo));
        // Las claves de los bytes de sus revisiones, que borrar() necesita para limpiar el disco.
        lenient().when(archivoRepository.findStorageKeysDeRevisiones(archivo.getId()))
                .thenReturn(List.of("clave-1"));
        return archivo;
    }
}