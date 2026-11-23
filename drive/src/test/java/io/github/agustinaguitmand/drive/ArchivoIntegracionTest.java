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

import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlDeTests.class)
@Testcontainers(disabledWithoutDocker = true)
class ArchivoIntegracionTest {

    @Autowired
    MockMvc mvc;

    // Los archivos de esta clase se guardan en una carpeta temporal que se borra al terminar.
    @TempDir
    static Path carpetaTemporal;

    @DynamicPropertySource
    static void storageTemporal(DynamicPropertyRegistry registro) {
        registro.add("drive.storage.dir", () -> carpetaTemporal.toString());
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
        // Se renombra otra vez con la versión que ya quedó vieja.
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
}