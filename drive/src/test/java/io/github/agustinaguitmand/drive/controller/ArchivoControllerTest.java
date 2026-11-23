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

@WebMvcTest(ArchivoController.class)
class ArchivoControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    IArchivoService archivoService;

    private static final String USUARIO = "eaa05ed3-234a-4a58-b434-45a2e254cb48";

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
}
