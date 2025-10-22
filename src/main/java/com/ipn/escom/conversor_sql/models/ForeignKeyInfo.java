package com.ipn.escom.conversor_sql.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Referencia de clave foránea (se valida lógicamente en SqlValidator). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyInfo {
    private String referencedTable;
    private String referencedColumn;
}
