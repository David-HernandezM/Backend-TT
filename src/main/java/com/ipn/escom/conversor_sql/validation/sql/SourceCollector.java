package com.ipn.escom.conversor_sql.validation.sql;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.utils.Texts;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

public class SourceCollector {
	/**
	 * Extrae todas las fuentes (tabla real + alias) de FROM y JOIN, en orden, y
	 * valida alias de tabla duplicados.
	 */
	public static List<Source> collectFromSources(PlainSelect plainSelect, ValidationResult validationResult) {
		List<Source> sources = new ArrayList<>();
		Set<String> seenAliases = new HashSet<>();

		// FROM
		FromItem fromItem = plainSelect.getFromItem();
		extractSource(fromItem, sources, seenAliases, validationResult);

		// JOINs
		if (plainSelect.getJoins() != null) {
			for (Join joinClause : plainSelect.getJoins()) {
				extractSource(joinClause.getRightItem(), sources, seenAliases, validationResult);
			}
		}
		return sources;
	}

	public static void extractSource(FromItem fromItem, List<Source> sources, Set<String> seenAliases,
			ValidationResult validationResult) {
		if (fromItem == null)
			return;

		if (fromItem instanceof Table tableFrom) {
			String tableNameLower = Texts.toLowerTrimmed(tableFrom.getName());
			String aliasLower = (tableFrom.getAlias() != null && tableFrom.getAlias().getName() != null)
					? Texts.toLowerTrimmed(tableFrom.getAlias().getName())
					: null;
			String originalToken = (tableFrom.getAlias() != null && tableFrom.getAlias().getName() != null)
					? tableFrom.getAlias().getName()
					: tableFrom.getName();

			// Alias duplicado de tabla
			if (aliasLower != null) {
				if (!seenAliases.add(aliasLower)) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"Alias de tabla duplicado: '" + tableFrom.getAlias().getName() + "'.");
				}
			}

			sources.add(new Source(tableNameLower, aliasLower, originalToken));
		} else {
			// Subselects u otros FROM items no soportados en fase 1
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Solo se permiten tablas en FROM/JOIN (subconsultas en FROM no soportadas en esta fase).");
		}
	}
	
	/** Resultado de resolver una columna contra las fuentes y el esquema. */
    public record ResolvedColumn(String tableReal, String column, String type,
                                  boolean ambiguous, String error) { }

    /**
     * Resuelve una columna (calificada o no) a (tabla real, columna, tipo),
     * detectando alias inválidos y ambigüedad.
     */
    public static ResolvedColumn resolveColumn(net.sf.jsqlparser.schema.Column columnExpression,
                                                List<Source> availableSources,
                                                SchemaIndex schemaIndex) {
        String qualifierLower = (columnExpression.getTable() != null)
                ? Texts.toLowerTrimmed(columnExpression.getTable().getName())
                : null;
        String columnNameLower = Texts.toLowerTrimmed(columnExpression.getColumnName());

        if (columnNameLower == null) {
            return new ResolvedColumn(null, null, null, false, "Columna sin nombre.");
        }

        // Columna calificada: alias/tabla.columna
        if (qualifierLower != null) {
            Source matchedSource = null;
            for (Source source : availableSources) {
                if (qualifierLower.equals(source.aliasLower()) || qualifierLower.equals(source.tableReal())) {
                    matchedSource = source;
                    break;
                }
            }
            if (matchedSource == null) {
                return new ResolvedColumn(null, null, null, false,
                        "Alias o tabla '" + qualifierLower + "' no está presente en FROM/JOIN.");
            }
            // Validar columna en la tabla encontrada
            if (!schemaIndex.columnsByTable()
                    .getOrDefault(matchedSource.tableReal(), Set.of())
                    .contains(columnNameLower)) {
                return new ResolvedColumn(null, null, null, false,
                        "La columna '" + columnNameLower + "' no existe en la tabla '" + matchedSource.tableReal() + "'.");
            }
            String resolvedType = schemaIndex.columnTypesByTable()
                    .getOrDefault(matchedSource.tableReal(), Map.of())
                    .get(columnNameLower);
            return new ResolvedColumn(matchedSource.tableReal(), columnNameLower, resolvedType, false, null);
        }

        // Columna no calificada: buscar en todas las fuentes
        List<Source> ownerSources = new ArrayList<>();
        for (Source source : availableSources) {
            if (schemaIndex.columnsByTable()
                    .getOrDefault(source.tableReal(), Set.of())
                    .contains(columnNameLower)) {
                ownerSources.add(source);
            }
        }
        if (ownerSources.isEmpty()) {
            return new ResolvedColumn(null, null, null, false,
                    "La columna '" + columnNameLower + "' no existe en ninguna de las tablas del FROM/JOIN.");
        }
        if (ownerSources.size() > 1) {
            return new ResolvedColumn(null, null, null, true,
                    "La columna '" + columnNameLower + "' es ambigua: existe en múltiples fuentes. Califícala con alias/tabla.");
        }
        Source onlyOwner = ownerSources.get(0);
        String resolvedType = schemaIndex.columnTypesByTable()
                .getOrDefault(onlyOwner.tableReal(), Map.of())
                .get(columnNameLower);
        return new ResolvedColumn(onlyOwner.tableReal(), columnNameLower, resolvedType, false, null);
    }
}
