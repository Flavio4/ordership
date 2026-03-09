package com.rtz.ordership.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.rtz.ordership.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
                log.warn("Recurso no encontrado: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
        }

        @ExceptionHandler(DuplicateResourceException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
                log.warn("Recurso duplicado: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage()));
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
                log.warn("Acceso no autorizado: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
                String message = ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(Collectors.joining(", "));
                log.warn("Error de validación: {}", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
                String message = "El cuerpo de la solicitud es inválido";

                Throwable cause = ex.getCause();
                if (cause instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {
                        String validValues = Arrays.stream(ife.getTargetType().getEnumConstants())
                                        .map(Object::toString)
                                        .collect(Collectors.joining(", "));
                        message = "Valor inválido: '" + ife.getValue() + "'. Valores permitidos: [" + validValues + "]";
                }

                log.warn("Request body inválido: {}", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
                String message = "Parámetro inválido '" + ex.getName() + "': se esperaba tipo " +
                                (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido");
                log.warn("Tipo de argumento inválido: {}", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
                log.warn("Método HTTP no soportado: {}", ex.getMethod());
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(),
                                                "Método HTTP '" + ex.getMethod()
                                                                + "' no soportado para este endpoint"));
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
                log.warn("Ruta no encontrada: {}", ex.getResourcePath());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(),
                                                "Recurso no encontrado: " + ex.getResourcePath()));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
                log.warn("Acceso denegado: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(),
                                                "No tiene permisos para realizar esta acción"));
        }

        @ExceptionHandler(AuthorizationDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
                log.warn("Autorización denegada: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(),
                                                "No tiene permisos para realizar esta acción"));
        }

        @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
        public ResponseEntity<ErrorResponse> handleIllegalArgumentsAndStates(RuntimeException ex) {
                log.warn("Operación o estado inválido: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
                if ("PropertyReferenceException".equals(ex.getClass().getSimpleName())) {
                        log.warn("Propiedad de ordenamiento inválida: {}", ex.getMessage());
                        String msg = ex.getMessage();
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(),
                                                        "Parámetro de ordenamiento o filtro inválido: " + msg));
                }

                log.error("Error inesperado: {}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                "Error interno del servidor"));
        }
}
