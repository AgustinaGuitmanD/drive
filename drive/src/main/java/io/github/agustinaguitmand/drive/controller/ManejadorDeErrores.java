package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);


    // 400: el dato que mandó el cliente no es válido.
    @ExceptionHandler({DatoInvalidoException.class, ArchivoInvalidoException.class})
    public ProblemDetail datoInvalido(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 401: no sabemos quién es el usuario.
    @ExceptionHandler(UsuarioNoRegistradoException.class)
    public ProblemDetail usuarioNoRegistrado(UsuarioNoRegistradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }


    // 409: choca con el estado actual (nombre o correo repetido, versión vieja).
    @ExceptionHandler({NombreDuplicadoException.class, CorreoDuplicadoException.class,
                       ConflictoDeVersionException.class})
    public ProblemDetail conflicto(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // 409: dos pedidos al mismo tiempo. El mensaje original trae detalles internos
    // de la base (tablas, SQL), así que se responde con un texto propio.
    @ExceptionHandler({DataIntegrityViolationException.class,
                      ObjectOptimisticLockingFailureException.class})
    public ProblemDetail conflictoConcurrente(RuntimeException e) {
        log.warn("Conflicto al guardar en la base", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El recurso fue modificado por otro pedido. Volvé a intentarlo.");
    }


    // 415: el tipo de archivo no está en la lista blanca.
    @ExceptionHandler(TipoNoPermitidoException.class)
    public ProblemDetail tipoNoPermitido(TipoNoPermitidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    // 500: cualquier error que no esperábamos. Se loguea completo y al cliente
    // no se le muestra el detalle para no exponer información interna.
    @ExceptionHandler(Exception.class)
    public ProblemDetail errorInesperado(Exception e) {
        log.error("Error inesperado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado");
    }

    // 404: el archivo (o la revisión) no existe, o es de otro usuario
    // (no distinguimos, para no revelar que existe).
    @ExceptionHandler({ArchivoNoEncontradoException.class, RevisionNoEncontradaException.class})
    public ProblemDetail noEncontrado(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
