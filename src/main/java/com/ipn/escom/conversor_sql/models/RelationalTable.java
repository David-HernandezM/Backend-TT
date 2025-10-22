package com.ipn.escom.conversor_sql.models;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla del esquema. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationalTable {

    @NotBlank(message = "Cada tabla debe tener un nombre.")
    private String name;

    @NotEmpty(message = "La tabla debe tener al menos una columna.")
    @Valid
    private List<Column> columns;
}
