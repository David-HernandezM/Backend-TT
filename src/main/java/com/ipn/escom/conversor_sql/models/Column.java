package com.ipn.escom.conversor_sql.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Columna del esquema (tipo simple; PK/FK opcionales). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Column {

    @NotBlank(message = "Cada columna debe tener un nombre.")
    private String name;

    @NotBlank(message = "Cada columna debe tener un tipo de dato.")
    private String type;

    private Boolean primaryKey;        // opcional
    private ForeignKeyInfo foreignKey; // opcional
}
