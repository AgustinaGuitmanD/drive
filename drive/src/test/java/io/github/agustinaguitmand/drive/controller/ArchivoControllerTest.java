package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.exceptions.ArchivoNoEncontradoException;
import io.github.agustinaguitmand.drive.exceptions.NombreDuplicadoException;
import io.github.agustinaguitmand.drive.exceptions.TipoNoPermitidoException;
import io.github.agustinaguitmand.drive.services.IArchivoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.agustinaguitmand.drive.exceptions.ConflictoDeVersionException;
import io.github.agustinaguitmand.drive.exceptions.RevisionNoEncontradaException;
import io.github.agustinaguitmand.drive.models.ContenidoArchivo;
import io.github.agustinaguitmand.drive.models.entities.Archivo;
import io.github.agustinaguitmand.drive.models.entities.TipoArchivo;
import org.springframework.core.io.ByteArrayResource;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
@WebMvcTest(ArchivoController.class)
class ArchivoControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    IArchivoService archivoService;

    private static final String USUARIO = "eaa05ed3-234a-4a58-b434-45a2e254cb48";
    private static final UUID ARCHIVO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void getAUnArchivoQueNoExisteDa404() throws Exception {
        UUID id = UUID.randomUUID();
        when(archivoService.obtenerMetadata(any(), eq(id)))
                .thenThrow(new ArchivoNoEncontradoException(id));

        mvc.perform(get("/api/archivos/{id}", id).header("X-User-Id", USUARIO))
                .andExpect(status().isNotFound());
    }

    @Test
    void renombrarConUnNombreRepetidoDa409() throws Exception {
        UUID id = UUID.randomUUID();
        when(archivoService.renombrar(any(), eq(id), eq("cv.pdf"), anyLong()))
                .thenThrow(new NombreDuplicadoException("cv.pdf"));

        mvc.perform(patch("/api/archivos/{id}", id)
                        .header("X-User-Id", USUARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"cv.pdf\", \"version\": 0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void subirUnTipoNoPermitidoDa415() throws Exception {
        MockMultipartFile html = new MockMultipartFile(
                "archivo", "pagina.html", "text/html", new byte[]{1});
        when(archivoService.subir(any(), any()))
                .thenThrow(new TipoNoPermitidoException("text/html"));

        mvc.perform(multipart("/api/archivos").file(html).header("X-User-Id", USUARIO))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void sinElHeaderDeUsuarioDa400() throws Exception {
        mvc.perform(get("/api/archivos"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unIdQueNoEsUuidDa400() throws Exception {
        mvc.perform(get("/api/archivos/hola").header("X-User-Id", USUARIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unErrorInesperadoDa500SinExponerDetalles() throws Exception {
        when(archivoService.listarArchivosUsuario(any()))
                .thenThrow(new RuntimeException("detalle interno"));

        mvc.perform(get("/api/archivos").header("X-User-Id", USUARIO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado"));
    }

    @Test
    void subirUnaRevisionDa201ConLaUbicacionDeSuContenido() throws Exception {
        Archivo foto = new Archivo(UUID.fromString(USUARIO), "foto.png", TipoArchivo.IMAGEN,
                "image/png", 3, "clave-1", AHORA, "checksum-1");
        foto.agregarRevision("clave-2", "checksum-2", 4, "image/png", AHORA);
        when(archivoService.subirRevision(any(), eq(ARCHIVO), any(), eq(0L))).thenReturn(foto);

        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        mvc.perform(multipart("/api/archivos/{id}/revisiones", ARCHIVO)
                        .file(nuevaFoto)
                        .param("version", "0")
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/archivos/" + ARCHIVO + "/revisiones/2/contenido"))
                .andExpect(jsonPath("$.revisionActual").value(2));
    }

    @Test
    void subirUnaRevisionConUnaVersionViejaDa409() throws Exception {
        when(archivoService.subirRevision(any(), eq(ARCHIVO), any(), eq(0L)))
                .thenThrow(new ConflictoDeVersionException(ARCHIVO, 0L, 1L));

        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        mvc.perform(multipart("/api/archivos/{id}/revisiones", ARCHIVO)
                        .file(nuevaFoto)
                        .param("version", "0")
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isConflict());
    }

    @Test
    void subirUnaRevisionSinVersionDa400() throws Exception {
        MockMultipartFile nuevaFoto = new MockMultipartFile(
                "archivo", "foto.png", "image/png", new byte[]{4, 5, 6, 7});

        mvc.perform(multipart("/api/archivos/{id}/revisiones", ARCHIVO)
                        .file(nuevaFoto)
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarRevisionesDevuelveSusDatosSinExponerDondeEstanGuardadas() throws Exception {
        Archivo foto = new Archivo(UUID.fromString(USUARIO), "foto.png", TipoArchivo.IMAGEN,
                "image/png", 3, "clave-1", AHORA, "checksum-1");
        foto.agregarRevision("clave-2", "checksum-2", 4, "image/png", AHORA);
        when(archivoService.listarRevisiones(any(), eq(ARCHIVO), anyInt()))
                .thenReturn(foto.ultimasRevisiones(10));

        mvc.perform(get("/api/archivos/{id}/revisiones", ARCHIVO).header("X-User-Id", USUARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[0].checksum").value("checksum-2"))
                .andExpect(jsonPath("$[1].numero").value(1))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
    }

    @Test
    void sinIndicarLimiteSePidenComoMaximo20Revisiones() throws Exception {
        mvc.perform(get("/api/archivos/{id}/revisiones", ARCHIVO).header("X-User-Id", USUARIO))
                .andExpect(status().isOk());

        verify(archivoService).listarRevisiones(any(), eq(ARCHIVO), eq(20));
    }

    @Test
    void unLimiteDeCantidadDeRevisionesIgualACeroDa400() throws Exception {
        mvc.perform(get("/api/archivos/{id}/revisiones", ARCHIVO)
                        .param("limite", "0")
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unLimiteDeCantidadDeRevisionesMayorA100Da400() throws Exception {
        mvc.perform(get("/api/archivos/{id}/revisiones", ARCHIVO)
                        .param("limite", "101")
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descargarUnaRevisionDevuelveSusBytesConSuChecksumComoEtag() throws Exception {
        byte[] bytesDeLaRevision1 = {1, 2, 3};
        when(archivoService.obtenerContenidoDeRevision(any(), eq(ARCHIVO), eq(1)))
                .thenReturn(new ContenidoArchivo("foto.png", MediaType.IMAGE_PNG,
                        new ByteArrayResource(bytesDeLaRevision1), "checksum-1"));

        mvc.perform(get("/api/archivos/{id}/revisiones/1/contenido", ARCHIVO)
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytesDeLaRevision1))
                .andExpect(header().string("ETag", "\"checksum-1\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void descargarUnaRevisionQueNoExisteDa404() throws Exception {
        when(archivoService.obtenerContenidoDeRevision(any(), eq(ARCHIVO), eq(9)))
                .thenThrow(new RevisionNoEncontradaException(ARCHIVO, 9));

        mvc.perform(get("/api/archivos/{id}/revisiones/9/contenido", ARCHIVO)
                        .header("X-User-Id", USUARIO))
                .andExpect(status().isNotFound());
    }

}
