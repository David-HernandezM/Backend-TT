package com.ipn.escom.conversor_sql.validation.schema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.Column;
import com.ipn.escom.conversor_sql.models.ForeignKeyInfo;
import com.ipn.escom.conversor_sql.models.RelationalTable;
import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.utils.Texts;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

public class SchemaBuilder {
	private SchemaBuilder() {
	}

	public static SchemaIndex buildSchemaIndex(List<RelationalTable> relationalTables,
			ValidationResult validationResult) {
		Set<String> definedTablesLower = new HashSet<>();
		Map<String, Map<String, String>> columnTypesByTable = new HashMap<>();
		Map<String, Set<String>> columnsByTable = new HashMap<>();

		for (RelationalTable relationalTable : relationalTables) {
			validateTable(relationalTable, validationResult);
			String tableNameLower = Texts.toLowerTrimmed(relationalTable.getName());
			if (tableNameLower == null)
				continue;

			// Duplicado de tabla
			if (!definedTablesLower.add(tableNameLower)) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"Tabla duplicada en el esquema: '" + relationalTable.getName() + "'.");
				continue;
			}

			int primaryKeyCount = 0;
			Map<String, String> typeByColumn = new LinkedHashMap<>();
			Set<String> columnNamesLower = new LinkedHashSet<>();

			if (relationalTable.getColumns() != null) {
				for (Column tableColumn : relationalTable.getColumns()) {
					validateColumn(relationalTable, tableColumn, validationResult);

					String columnNameLower = Texts.toLowerTrimmed(tableColumn.getName());
					if (columnNameLower != null) {
						// Duplicado de columna dentro de la misma tabla
						if (!columnNamesLower.add(columnNameLower)) {
							validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "Columna duplicada '"
									+ tableColumn.getName() + "' en la tabla '" + relationalTable.getName() + "'.");
						}
						String typeLower = Texts.toLowerTrimmed(tableColumn.getType());
						if (typeLower != null) {
							typeByColumn.put(columnNameLower, typeLower);
						}
					}

					if (Boolean.TRUE.equals(tableColumn.getPrimaryKey()))
						primaryKeyCount++;

					if (tableColumn.getForeignKey() != null) {
						validateForeignKey(relationalTables, relationalTable, tableColumn, validationResult);
					}
				}
			}

			validatePrimaryKeyCount(relationalTable, primaryKeyCount, validationResult);
			columnTypesByTable.put(tableNameLower, typeByColumn);
			columnsByTable.put(tableNameLower, columnNamesLower);
		}

		return new SchemaIndex(definedTablesLower, columnTypesByTable, columnsByTable);
	}

	private static void validateTable(RelationalTable relationalTable, ValidationResult validationResult) {
		if (Texts.isBlank(relationalTable.getName())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Cada tabla debe tener un nombre válido.");
		}
		if (relationalTable.getColumns() == null || relationalTable.getColumns().isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La tabla '" + relationalTable.getName() + "' debe tener al menos una columna.");
		}
	}

	private static void validateColumn(RelationalTable relationalTable, Column tableColumn,
			ValidationResult validationResult) {
		if (Texts.isBlank(tableColumn.getName())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La tabla '" + relationalTable.getName() + "' tiene una columna sin nombre.");
		}
		if (Texts.isBlank(tableColumn.getType())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "La columna '" + tableColumn.getName()
					+ "' de la tabla '" + relationalTable.getName() + "' no tiene un tipo de dato.");
		}
	}

	private static void validateForeignKey(List<RelationalTable> allTables, RelationalTable ownerTable,
			Column foreignKeyColumn, ValidationResult validationResult) {
		ForeignKeyInfo fkInfo = foreignKeyColumn.getForeignKey();
		if (Texts.isBlank(fkInfo.getReferencedTable()) || Texts.isBlank(fkInfo.getReferencedColumn())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La columna '" + foreignKeyColumn.getName() + "' en la tabla '" + ownerTable.getName()
							+ "' tiene una clave foránea incompleta.");
			return;
		}

		Optional<RelationalTable> referencedTableOpt = allTables.stream()
				.filter(t -> t.getName().equalsIgnoreCase(fkInfo.getReferencedTable())).findFirst();

		if (referencedTableOpt.isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La clave foránea en '" + ownerTable.getName() + "." + foreignKeyColumn.getName()
							+ "' referencia una tabla inexistente: '" + fkInfo.getReferencedTable() + "'.");
			return;
		}

		Optional<Column> referencedColumnOpt = referencedTableOpt.get().getColumns().stream()
				.filter(c -> c.getName().equalsIgnoreCase(fkInfo.getReferencedColumn())).findFirst();

		if (referencedColumnOpt.isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La clave foránea en '" + ownerTable.getName() + "." + foreignKeyColumn.getName()
							+ "' referencia una columna inexistente: '" + fkInfo.getReferencedTable() + "."
							+ fkInfo.getReferencedColumn() + "'.");
			return;
		}

		Column referencedCol = referencedColumnOpt.get();

		// ✅ FK debe referenciar PK
		if (!Boolean.TRUE.equals(referencedCol.getPrimaryKey())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La clave foránea '" + ownerTable.getName() + "." + foreignKeyColumn.getName()
							+ "' debe referenciar una PK, pero '" + fkInfo.getReferencedTable() + "."
							+ fkInfo.getReferencedColumn() + "' no está marcada como PK.");
		}

		// Tipos coincidentes
		String sourceTypeLower = Texts.toLowerTrimmed(foreignKeyColumn.getType());
		String targetTypeLower = Texts.toLowerTrimmed(referencedCol.getType());
		if (sourceTypeLower != null && targetTypeLower != null && !sourceTypeLower.equals(targetTypeLower)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La clave foránea '" + foreignKeyColumn.getName() + "' en '" + ownerTable.getName()
							+ "' no coincide en tipo con '" + fkInfo.getReferencedTable() + "."
							+ fkInfo.getReferencedColumn() + "'.");
		}
	}

	private static void validatePrimaryKeyCount(RelationalTable relationalTable, int primaryKeyCount,
			ValidationResult validationResult) {
		if (primaryKeyCount == 0) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La tabla '" + relationalTable.getName() + "' no tiene ninguna clave primaria.");
		} else if (primaryKeyCount > 1) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "La tabla '"
					+ relationalTable.getName() + "' tiene más de una clave primaria. Solo se permite una.");
		}
	}

	public static SchemaIndex build(List<RelationalTable> relationalTables) {
		ValidationResult vr = ValidationResult.builder().build();
		SchemaIndex idx = buildSchemaIndex(relationalTables, vr);
		if (!vr.isValido()) {
			throw new IllegalArgumentException("Esquema inválido: " + vr.getMensajes());
		}
		return idx;
	}
}
