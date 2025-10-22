package com.ipn.escom.conversor_sql.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.models.ValidationMessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResult {

    /**
     * True cuando no se ha agregado ningún mensaje de tipo "error".
     * Se actualiza automáticamente al agregar mensajes o al reemplazar la lista completa.
     */
    @Builder.Default
    private boolean valido = true;

    /**
     * Mensajes acumulados de validación.
     */
    @Builder.Default
    private List<ValidationMessage> mensajes = new ArrayList<>();

    /* ===========================
                 API
       =========================== */

    /** Agrega un mensaje genérico y ajusta 'valido' si es error. */
    public void agregarMensaje(String tipo, TipoDetallado tipoDetallado, String contenido) {
        String normalized = (tipo == null) ? "error" : tipo.toLowerCase();
        if ("error".equals(normalized)) {
            this.valido = false;
        }
        if (this.mensajes == null) {
            this.mensajes = new ArrayList<>();
        }
        this.mensajes.add(new ValidationMessage(normalized, tipoDetallado, contenido));
    }

    /** Helpers semánticos (azúcar sintáctico). */
    public void addErrorLogico(String contenido) {
        agregarMensaje("error", TipoDetallado.ERROR_LOGICO, contenido);
    }

    public void addErrorSintaxis(String contenido) {
        agregarMensaje("error", TipoDetallado.ERROR_SINTAXIS, contenido);
    }

    public void addAdvertencia(String contenido) {
        agregarMensaje("advertencia", TipoDetallado.ADVERTENCIA, contenido);
    }

    public void addExito(String contenido) {
        agregarMensaje("exito", TipoDetallado.VALIDACION_EXITOSA, contenido);
    }

    /** ¿Hay al menos un error? */
    public boolean hasErrors() {
        if (mensajes == null) return false;
        return mensajes.stream().anyMatch(m -> "error".equalsIgnoreCase(m.getTipo()));
    }

    /** Cantidad de errores. */
    public long errorCount() {
        if (mensajes == null) return 0;
        return mensajes.stream().filter(m -> "error".equalsIgnoreCase(m.getTipo())).count();
    }

    /** Une mensajes de otro resultado y recalcula 'valido'. */
    public void merge(ValidationResult other) {
        if (other == null) return;
        if (other.getMensajes() != null && !other.getMensajes().isEmpty()) {
            if (this.mensajes == null) this.mensajes = new ArrayList<>();
            this.mensajes.addAll(other.getMensajes());
        }
        // Si el otro ya era inválido o trae errores, este también queda inválido
        this.valido = this.valido && other.isValido();
        if (hasErrors()) this.valido = false;
    }

    /* ===========================
             Setters seguros
       =========================== */

    /**
     * Reemplaza la lista y recalcula 'valido' en función de los tipos presentes.
     * Si la lista es null, queda vacía y 'valido' se mantiene true.
     */
    public void setMensajes(List<ValidationMessage> nuevosMensajes) {
        if (nuevosMensajes == null) {
            this.mensajes = new ArrayList<>();
            this.valido = true;
            return;
        }
        this.mensajes = new ArrayList<>(nuevosMensajes);
        this.valido = this.mensajes.stream().noneMatch(m -> "error".equalsIgnoreCase(m.getTipo()));
    }

    /**
     * Devuelve una vista inmodificable de los mensajes para evitar mutaciones accidentales.
     */
    public List<ValidationMessage> getMensajes() {
        if (mensajes == null) return Collections.emptyList();
        return Collections.unmodifiableList(mensajes);
    }
}
