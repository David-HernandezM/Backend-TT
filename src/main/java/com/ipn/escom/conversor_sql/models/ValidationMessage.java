package com.ipn.escom.conversor_sql.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensaje de validación que se devuelve al cliente.
 * Ejemplos de 'tipo': "error", "advertencia", "exito".
 * El detalle se expresa con {@link TipoDetallado}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationMessage {

    /**
     * Severidad del mensaje (string por compatibilidad):
     * Debe ser uno de: error | advertencia | exito
     */
    @NotBlank
    @Pattern(regexp = "error|advertencia|exito", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "El tipo debe ser 'error', 'advertencia' o 'exito'.")
    private String tipo;

    /** Clasificación detallada del mensaje. */
    @NotNull
    private TipoDetallado tipoDetallado;

    /** Texto a mostrar al usuario. */
    @NotBlank
    private String contenido;

    /* ===========================
              Helpers
       =========================== */

    public static ValidationMessage ok(String contenido) {
        return ValidationMessage.builder()
                .tipo("exito")
                .tipoDetallado(TipoDetallado.VALIDACION_EXITOSA)
                .contenido(contenido)
                .build();
    }

    public static ValidationMessage errorLogico(String contenido) {
        return ValidationMessage.builder()
                .tipo("error")
                .tipoDetallado(TipoDetallado.ERROR_LOGICO)
                .contenido(contenido)
                .build();
    }

    public static ValidationMessage errorSintaxis(String contenido) {
        return ValidationMessage.builder()
                .tipo("error")
                .tipoDetallado(TipoDetallado.ERROR_SINTAXIS)
                .contenido(contenido)
                .build();
    }

    public static ValidationMessage advertencia(String contenido) {
        return ValidationMessage.builder()
                .tipo("advertencia")
                .tipoDetallado(TipoDetallado.ADVERTENCIA)
                .contenido(contenido)
                .build();
    }
}
