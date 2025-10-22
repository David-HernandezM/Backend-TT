package com.ipn.escom.conversor_sql.validation.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record SchemaIndex(
        Set<String> definedTablesLower,
        Map<String, Map<String, String>> columnTypesByTable,
        Map<String, Set<String>> columnsByTable
) { 
	  // --- Helpers ignore-case para consumo del normalizador / mappers ---

	  public boolean hasTableIgnoreCase(String tableName) {
	    String key = normalize(tableName);
	    return key != null && definedTablesLower != null && definedTablesLower.contains(key);
	  }

	  /** Devuelve la lista de columnas (en minúsculas) de la tabla, o lista vacía si no existe. */
	  public List<String> getColumnsIgnoreCase(String tableName) {
	    String key = normalize(tableName);
	    if (key == null || columnsByTable == null) return Collections.emptyList();
	    Set<String> cols = columnsByTable.get(key);
	    return (cols == null) ? Collections.emptyList() : new ArrayList<>(cols);
	  }

	  public boolean hasColumnIgnoreCase(String tableName, String columnName) {
	    String t = normalize(tableName);
	    String c = normalize(columnName);
	    if (t == null || c == null || columnsByTable == null) return false;
	    Set<String> cols = columnsByTable.get(t);
	    return cols != null && cols.contains(c);
	  }

	  /** Tipo de columna (minúsculas) si existe; empty si no. */
	  public Optional<String> getColumnTypeLower(String tableName, String columnName) {
	    String t = normalize(tableName);
	    String c = normalize(columnName);
	    if (t == null || c == null || columnTypesByTable == null) return Optional.empty();
	    Map<String,String> types = columnTypesByTable.get(t);
	    if (types == null) return Optional.empty();
	    String type = types.get(c);
	    return Optional.ofNullable(type);
	  }

	  /** Nombre de tabla normalizado (minúsculas, trim); null si input vacío. */
	  public static String normalize(String s) {
	    if (s == null) return null;
	    String t = s.trim();
	    return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
	  }
}
