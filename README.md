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

Todos los comandos se ejecutan desde la carpeta `drive/`:

```bash
cd drive
docker compose up -d        # levanta MySQL en el puerto 3307
./mvnw spring-boot:run      # API en http://localhost:8080
```

Las tablas se crean al arrancar. Los archivos subidos se guardan en `drive/storage/`.

En Windows, usá `.\mvnw` en lugar de `./mvnw`.

## Tests

```bash
cd drive
./mvnw clean test
```

- **Los unitarios** (dominio, servicio, storage y controller) no necesitan nada más.
- **Los de integración** levantan un MySQL propio con Testcontainers, así que necesitan Docker
  abierto. No usan la base del `docker-compose`. Si Docker no está disponible, se saltean y el
  resto corre igual. La primera vez tardan más, porque descargan la imagen de MySQL.

## Probarlo

Cada pedido a `/api/archivos` lleva el header `X-User-Id` con el id de una persona registrada.
Es una simplificación del prototipo en lugar de autenticación real (ver el DESIGN.md).

**1. Registrar una persona** y copiar el `id` de la respuesta:

```bash
curl -X POST http://localhost:8080/api/personas \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Ana", "apellido": "Pérez", "correo": "ana@mail.com"}'
```

**2. Subir un archivo** (PNG, JPEG, GIF, WebP, PDF o texto plano; hasta 20 MB):

```bash
curl -X POST http://localhost:8080/api/archivos \
  -H "X-User-Id: <id-de-la-persona>" \
  -F "archivo=@foto.png"
```

**3. Listar, descargar y renombrar:**

```bash
curl http://localhost:8080/api/archivos -H "X-User-Id: <id>"
curl http://localhost:8080/api/archivos/<archivo-id>/contenido -H "X-User-Id: <id>" -o descarga.png

curl -X PATCH http://localhost:8080/api/archivos/<archivo-id> \
  -H "X-User-Id: <id>" -H "Content-Type: application/json" \
  -d '{"nombre": "vacaciones.png", "version": 0}'
```

Para renombrar o subir una revisión, `version` tiene que ser la que devolvió la última
respuesta del archivo. Si no coincide, alguien lo modificó en el medio y la respuesta es
**409**.

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
└── src/main/java/io/github/agustinaguitmand/drive/
    ├── controller/     endpoints, DTO y manejo de errores
    ├── services/       lógica de negocio
    ├── models/         entidades (Archivo, RevisionArchivo, Persona) y helpers
    ├── repositories/   acceso a MySQL (Spring Data JPA)
    ├── storage/        guardado de los bytes (en disco; reemplazable por S3)
    └── exceptions/     excepciones propias
```
