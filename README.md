# Drive

Prototipo de Google Drive basado en el capítulo 15 de *System Design Interview* (Alex Xu):
una API REST para subir, listar, descargar, renombrar y borrar archivos, con historial de
revisiones del contenido.

Hecho con Java 17, Spring Boot 4.2, JPA/Hibernate y MySQL 8.4.

Las decisiones de diseño, los trade-offs, la seguridad y el uso de IA están en
**[drive/DESIGN.md](drive/DESIGN.md)**.

## Requisitos

- **JDK 17** o posterior.
- **Docker**, para la base de datos y para los tests de integración.

No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw`).

## Cómo correrlo

Todos los comandos se ejecutan desde la subcarpeta drive/ del repositorio, donde están el pom.xml y el docker-compose.yml. 
Como el repo también se llama drive, al clonarlo queda drive/drive/

```bash
git clone https://github.com/AgustinaGuitmanD/drive.git
cd drive/drive
```

```bash
docker compose up -d        # levanta MySQL en el puerto 3307
./mvnw spring-boot:run      # API en http://localhost:8080
```

Las tablas se crean al arrancar. Los archivos subidos se guardan en `drive/storage/`.

En Windows, usá `.\mvnw` en lugar de `./mvnw`.

Desde ahí:

```bash
docker compose up -d        # levanta MySQL en el puerto 3307
./mvnw spring-boot:run      # API en http://localhost:8080
```

Las tablas se crean al arrancar. Los archivos subidos se guardan en `drive/storage/`.

En Windows, usá `.\mvnw` en lugar de `./mvnw`.

## Tests

Desde la misma carpeta (`drive/drive/`):

```bash
./mvnw clean test
```

- **Los unitarios** (dominio, servicio, storage y controller) no necesitan nada más.
- **Los de integración** levantan un MySQL propio con Testcontainers, así que necesitan Docker
  abierto. No hace falta levantar el `docker-compose`: no usan esa base. Si Docker no está disponible, se saltean y el
  resto corre igual. La primera vez tardan más, porque descargan la imagen de MySQL.

## Probarlo

Importá la colección `drive/postman/drive.postman_collection.json` en Postman (**Import**) y
ejecutá los pedidos en orden. Cada uno guarda en variables de la colección los ids que usan
los siguientes (`usuarioId`, `archivoId` y `version`), así que no hace falta copiar nada a mano.
En los pedidos que suben archivos, elegí el archivo en **Body → form-data → archivo**.

Cada pedido a `/api/archivos` lleva el header `X-User-Id` con el id de una persona registrada.
Es una simplificación del prototipo en lugar de autenticación real (ver el DESIGN.md). Para
renombrar o subir una revisión, `version` tiene que coincidir con la actual del archivo; si
alguien lo modificó en el medio, la respuesta es **409**.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/personas` | Registra una persona |
| `POST` | `/api/archivos` | Sube un archivo (`multipart/form-data`, campo `archivo`) |
| `GET` | `/api/archivos` | Lista los archivos del usuario |
| `GET` | `/api/archivos/{id}` | Metadata del archivo |
| `GET` | `/api/archivos/{id}/contenido` | Contenido actual |
| `PATCH` | `/api/archivos/{id}` | Renombra |
| `DELETE` | `/api/archivos/{id}` | Borra el archivo y todas sus revisiones |
| `POST` | `/api/archivos/{id}/revisiones` | Sube contenido nuevo (`archivo` + `version`) |
| `GET` | `/api/archivos/{id}/revisiones` | Historial de revisiones (`?limite=`, de 1 a 100) |
| `GET` | `/api/archivos/{id}/revisiones/{numero}/contenido` | Contenido de una revisión |

Los errores se devuelven en formato `ProblemDetail` (RFC 7807). La tabla de códigos está en el
DESIGN.md.

## Estructura

```
drive/
├── DESIGN.md
├── docker-compose.yml
├── pom.xml
├── postman/            colección de Postman con todos los pedidos
└── src/main/java/io/github/agustinaguitmand/drive/
    ├── controller/     endpoints, DTO y manejo de errores
    ├── services/       lógica de negocio
    ├── models/         entidades (Archivo, RevisionArchivo, Persona) y helpers
    ├── repositories/   acceso a MySQL (Spring Data JPA)
    ├── storage/        guardado de los bytes (en disco; reemplazable por S3)
    └── exceptions/     excepciones propias
```
