package io.github.agustinaguitmand.drive;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Levanta un MySQL real en Docker para los tests de integración.
 * Spring configura la conexión sola (@ServiceConnection), sin URL ni contraseñas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlDeTests {

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4");
    }
}