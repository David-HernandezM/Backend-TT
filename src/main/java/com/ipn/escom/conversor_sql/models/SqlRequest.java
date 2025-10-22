package com.ipn.escom.conversor_sql.models;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de entrada para validar una sentencia SQL contra el esquema relacional enviado.
 * <p>
 * - Debe incluir al menos una tabla (máximo 7).
 * - La sentencia SQL no puede ser vacía.
 * <p>
 * Validaciones avanzadas (PK, FK, tipos, sintaxis soportada) se realizan en {@link com.ipn.escom.conversor_sql.validation.SqlValidator}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlRequest {

    /**
     * Lista de tablas que definen el esquema.
     * Se valida en cascada: cada tabla debe cumplir sus propias reglas.
     */
    @NotEmpty(message = "Debe proporcionar al menos una tabla en 'tables'.")
    @Valid
    private List<RelationalTable> tables;

    /**
     * Sentencia SQL a validar.
     */
    @NotBlank(message = "El campo 'sqlQuery' no puede estar vacío.")
    private String sqlQuery;
}
