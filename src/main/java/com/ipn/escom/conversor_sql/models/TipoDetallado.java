package com.ipn.escom.conversor_sql.models;

/**
 * Clasificación detallada del mensaje de validación.
 * - ERROR_LOGICO: Reglas de negocio / semántica (tablas/columnas inexistentes, ambigüedad, joins no permitidos, etc.)
 * - ERROR_SINTAXIS: Parseo SQL (errores de sintaxis).
 * - VALIDACION_EXITOSA: Todo OK.
 * - ADVERTENCIA: Observaciones no bloqueantes.
 */
public enum TipoDetallado {
    ERROR_LOGICO,
    ERROR_SINTAXIS,
    VALIDACION_EXITOSA,
    ADVERTENCIA
}
