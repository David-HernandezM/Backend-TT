package com.ipn.escom.conversor_sql.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationResult> handleNotReadable(HttpMessageNotReadableException ex) {
        ValidationResult vr = new ValidationResult();
        vr.agregarMensaje("error", TipoDetallado.ERROR_SINTAXIS,
                "El cuerpo de la petición no es un JSON válido o no coincide con el modelo. " + ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(vr);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResult> handleValidation(MethodArgumentNotValidException ex) {
        ValidationResult vr = new ValidationResult();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                vr.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                        "Campo inválido '" + err.getField() + "': " + err.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(vr);
    }

    // Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ValidationResult> handleGeneric(Exception ex) {
        ValidationResult vr = new ValidationResult();
        vr.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "Error inesperado: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(vr);
    }
}
