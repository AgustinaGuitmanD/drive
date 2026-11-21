package io.github.agustinaguitmand.drive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class DriveApplication {

	public static void main(String[] args) {
		SpringApplication.run(DriveApplication.class, args);
	}

	// Reloj real en UTC. Se inyecta en los servicios para poder fijar la hora en los tests.
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
