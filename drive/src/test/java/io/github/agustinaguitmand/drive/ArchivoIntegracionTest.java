package io.github.agustinaguitmand.drive;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlDeTests.class)
@Testcontainers(disabledWithoutDocker = true)
class ArchivoIntegracionTest {

    @Autowired
    MockMvc mvc;

    @TempDir
    static Path carpeta;


    @DynamicPropertySource
    static void storageTemporal(DynamicPropertyRegistry registro) {
        registro.add("drive.storage.dir", () -> carpeta.toString());
    }

    @Test
    void unArchivoSubidoSeDescargaConLosMismosBytes() throws Exception {
        String usuario = registrarUsuario();
        byte[] contenido = "hola drive".getBytes();

        String archivoId = subir(usuario, "notas.txt", "text/plain", contenido);

        mvc.perform(get("/api/archivos/{id}/contenido", archivoId).header("X-User-Id", usuario))
                .andExpect(status().isOk())
                .andExpect(content().bytes(contenido));
    }

    @Test
    void renombrarConUnaVersionViejaDa409() throws Exception {
        String usuario = registrarUsuario();
        String id = subir(usuario, "foto.png", "image/png", new byte[]{1, 2, 3});
        long version = versionDe(usuario, id);

        renombrar(usuario, id, "vacaciones.png", version).andExpect(status().isOk());
        renombrar(usuario, id, "playa.png", version).andExpect(status().isConflict());
    }

    @Test
    void otroUsuarioNoPuedeVerElArchivo() throws Exception {
        String duenio = registrarUsuario();
        String otro = registrarUsuario();
        String id = subir(duenio, "foto.png", "image/png", new byte[]{1});

        mvc.perform(get("/api/archivos/{id}", id).header("X-User-Id", otro))
                .andExpect(status().isNotFound());
    }

    @Test
    void unArchivoBorradoYaNoSeEncuentra() throws Exception {
        String usuario = registrarUsuario();
        String id = subir(usuario, "foto.png", "image/png", new byte[]{1});

        mvc.perform(delete("/api/archivos/{id}", id).header("X-User-Id", usuario))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/archivos/{id}", id).header("X-User-Id", usuario))
                .andExpect(status().isNotFound());
    }

    @Test
    void subirDosVecesElMismoNombreGuardaLaSegundaNumerada() throws Exception {
        String usuario = registrarUsuario();
        subir(usuario, "foto.png", "image/png", new byte[]{1});

        String id = subir(usuario, "foto.png", "image/png", new byte[]{2});

        mvc.perform(get("/api/archivos/{id}", id).header("X-User-Id", usuario))
                .andExpect(jsonPath("$.nombre").value("foto (1).png"));
    }

    @Test
    void unaRevisionNuevaReemplazaElContenidoYLaAnteriorSigueDisponible() throws Exception {
        String usuario = registrarUsuario();
        byte[] fotoOriginal = {1, 2, 3};
        byte[] fotoNueva = {4, 5, 6, 7};
        String id = subir(usuario, "foto.png", "image/png", fotoOriginal);

        subirRevision(usuario, id, fotoNueva, versionDe(usuario, id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionActual").value(2));

        mvc.perform(get("/api/archivos/{id}/contenido", id).header("X-User-Id", usuario))
                .andExpect(content().bytes(fotoNueva));
        mvc.perform(get("/api/archivos/{id}/revisiones/1/contenido", id).header("X-User-Id", usuario))
                .andExpect(content().bytes(fotoOriginal));
    }

    @Test
    void elHistorialListaLasRevisionesDeLaMasNuevaALaMasVieja() throws Exception {
        String usuario = registrarUsuario();
        String id = subir(usuario, "foto.png", "image/png", new byte[]{1, 2, 3});
        subirRevision(usuario, id, new byte[]{4, 5, 6, 7}, versionDe(usuario, id))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/archivos/{id}/revisiones", id).header("X-User-Id", usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[0].tamanio").value(4))
                .andExpect(jsonPath("$[1].numero").value(1))
                .andExpect(jsonPath("$[1].tamanio").value(3));
    }

    @Test
    void siDosPersonasSubenUnaRevisionConLaMismaVersionLaPrimeraGana() throws Exception {
        // Las dos leyeron el archivo en la misma versión.
        String usuario = registrarUsuario();
        String id = subir(usuario, "foto.png", "image/png", new byte[]{1, 2, 3});
        long versionQueLeyeronAmbas = versionDe(usuario, id);

        // La primera sube su revisión: se acepta y la versión avanza.
        subirRevision(usuario, id, new byte[]{4, 5, 6}, versionQueLeyeronAmbas)
                .andExpect(status().isCreated());

        // La segunda sube con la versión que había leído, que ya quedó vieja: conflicto.
        subirRevision(usuario, id, new byte[]{7, 8, 9}, versionQueLeyeronAmbas)
                .andExpect(status().isConflict());
    }

    @Test
    void borrarUnArchivoEliminaDelDiscoLosBytesDeTodasSusRevisiones() throws Exception {
        // Cuántos archivos hay en disco antes:
        long archivosEnDiscoAntes = cantidadDeArchivosEnDisco();

        // Un archivo nuevo y una revisión: dos archivos nuevos en disco.
        String usuario = registrarUsuario();
        String id = subir(usuario, "foto.png", "image/png", new byte[]{1, 2, 3});
        subirRevision(usuario, id, new byte[]{4, 5, 6, 7}, versionDe(usuario, id))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/archivos/{id}", id).header("X-User-Id", usuario))
                .andExpect(status().isNoContent());

        assertEquals(archivosEnDiscoAntes, cantidadDeArchivosEnDisco());
    }

    // --- Métodos auxiliares ---

    // Cada test registra un usuario nuevo, así los tests no comparten datos en la base.
    private String registrarUsuario() throws Exception {
        String correo = UUID.randomUUID() + "@test.com";
        String respuesta = mvc.perform(MockMvcRequestBuilders.post("/api/personas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Ana", "apellido": "Test", "correo": "%s"}
                                """.formatted(correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private String subir(String usuario, String nombre, String tipo, byte[] contenido) throws Exception {
        String respuesta = mvc.perform(multipart("/api/archivos")
                        .file(new MockMultipartFile("archivo", nombre, tipo, contenido))
                        .header("X-User-Id", usuario))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private ResultActions renombrar(String usuario, String id, String nombre, long version) throws Exception {
        return mvc.perform(patch("/api/archivos/{id}", id)
                .header("X-User-Id", usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre": "%s", "version": %d}
                        """.formatted(nombre, version)));
    }

    private long versionDe(String usuario, String id) throws Exception {
        String respuesta = mvc.perform(get("/api/archivos/{id}", id).header("X-User-Id", usuario))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.version")).longValue();
    }

    private ResultActions subirRevision(String usuario, String archivoId, byte[] contenido,
                                        long version) throws Exception {
        return mvc.perform(multipart("/api/archivos/{id}/revisiones", archivoId)
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", contenido))
                .param("version", String.valueOf(version))
                .header("X-User-Id", usuario));
    }
    private static long cantidadDeArchivosEnDisco() throws IOException {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos.count();
        }
    }
}