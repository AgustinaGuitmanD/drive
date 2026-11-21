package io.github.agustinaguitmand.drive.controller;

import io.github.agustinaguitmand.drive.exceptions.CorreoDuplicadoException;
import io.github.agustinaguitmand.drive.exceptions.UsuarioNoRegistradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorDeErrores {
    @ExceptionHandler(CorreoDuplicadoException.class)
    public ProblemDetail correoDuplicado(CorreoDuplicadoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UsuarioNoRegistradoException.class)
    public ProblemDetail usuarioNoRegistrado(UsuarioNoRegistradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
