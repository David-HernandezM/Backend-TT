package com.ipn.escom.conversor_sql.validation.sql;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.utils.Texts;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.SourceCollector.ResolvedColumn;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

public class ProjectionValidator {
	/**
     * Valida la proyección (lista de SELECT):
     * - Permite SELECT * y tabla.*
     * - Prohíbe funciones agregadas (SUM/COUNT/AVG/MIN/MAX) en esta fase
     * - Cada columna simple debe resolverse sin ambigüedad y existir
     */
    public static void validateSelectList(List<SelectItem> selectItems,
                                           List<Source> availableSources,
                                           SchemaIndex schemaIndex,
                                           ValidationResult validationResult) {
        if (selectItems == null || selectItems.isEmpty()) {
            validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                    "El SELECT debe proyectar al menos una expresión.");
            return;
        }

        for (SelectItem selectItem : selectItems) {
            // Permitimos SELECT * y tabla.*
            if (selectItem instanceof AllColumns) {
                continue;
            }
            if (selectItem instanceof AllTableColumns) {
                continue;
            }

            // Debe ser una expresión seleccionada
            if (selectItem instanceof SelectExpressionItem selectExpressionItem) {
                Expression selectExpression = selectExpressionItem.getExpression();
                
                if (selectItem instanceof SelectExpressionItem sei) {
                    Expression e = sei.getExpression();
                    if (ExpressionUtils.containsNullSyntax(e)) {
                        validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                                "No se permite proyectar NULL ni usar IS [NOT] NULL en el SELECT.");
                        continue;
                    }
                }

                // Agregaciones no permitidas
                if (selectExpression instanceof Function sqlFunction && isAggregate(sqlFunction)) {
                    validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                            "No se permiten funciones agregadas como SUM, COUNT, AVG, MIN o MAX en la consulta.");
                    continue;
                }

                // Columna simple
                if (selectExpression instanceof net.sf.jsqlparser.schema.Column columnExpression) {
                    ResolvedColumn resolvedColumn = SourceCollector.resolveColumn(columnExpression, availableSources, schemaIndex);
                    if (resolvedColumn.error() != null) {
                        validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, resolvedColumn.error());
                    } else if (resolvedColumn.ambiguous()) {
                        validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                                "Columna ambigua en SELECT: '" + columnExpression.getColumnName() + "'.");
                    }
                } else {
                    // Expresiones/funciones no permitidas en esta fase (salvo las excepciones arriba)
                    validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                            "Solo se permiten columnas simples o '*' en la lista de SELECT.");
                }
            } else {
                validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                        "Elemento de SELECT no soportado.");
            }
        }
    }
    
    /** true si la proyección usa * o tabla.* (para bloquear en set operations) */
    public static boolean containsAllColumns(List<SelectItem> selectItems) {
        if (selectItems == null) return false;
        for (SelectItem si : selectItems) {
            if ((si instanceof AllColumns) || (si instanceof AllTableColumns)) return true;
        }
        return false;
    }
    
    /**
     * Recorre la lista de SELECT para:
     *  - Extraer alias de columnas.
     *  - Validar duplicados de alias en el mismo SELECT.
     *  - Validar colisión con alias de tablas presentes.
     *
     * @return conjunto de alias de columna definidos en este SELECT.
     */
    public static Set<String> collectAndValidateColumnAliases(
            List<SelectItem> selectItems,
            List<Source> availableSources,
            ValidationResult validationResult) {

        Set<String> columnAliases = new HashSet<>();
        if (selectItems == null) return columnAliases;

        Set<String> tableAliases = new HashSet<>();
        for (Source s : availableSources) {
            if (s.aliasLower() != null) tableAliases.add(s.aliasLower());
        }

        for (SelectItem selectItem : selectItems) {
            if (selectItem instanceof SelectExpressionItem sei) {
                if (sei.getAlias() != null && sei.getAlias().getName() != null) {
                    String aliasLower = Texts.toLowerTrimmed(sei.getAlias().getName());
                    if (aliasLower != null) {
                        // Duplicado de alias de columna
                        if (!columnAliases.add(aliasLower)) {
                            validationResult.agregarMensaje(
                                    "error",
                                    TipoDetallado.ERROR_LOGICO,
                                    "Alias de columna duplicado en SELECT: '" + sei.getAlias().getName() + "'."
                            );
                        }
                        // Colisión con alias de tabla
                        if (tableAliases.contains(aliasLower)) {
                            validationResult.agregarMensaje(
                                    "error",
                                    TipoDetallado.ERROR_LOGICO,
                                    "El alias de columna '" + sei.getAlias().getName()
                                            + "' colisiona con un alias de tabla. Usa un nombre diferente."
                            );
                        }
                    }
                }
            }
        }
        return columnAliases;
    }
    
    public static boolean isAggregate(Function sqlFunction) {
		if (sqlFunction.getName() == null)
			return false;
		String functionNameLower = sqlFunction.getName().toLowerCase(Locale.ROOT);
		return functionNameLower.equals("sum") || functionNameLower.equals("count") || functionNameLower.equals("avg")
				|| functionNameLower.equals("min") || functionNameLower.equals("max");
	}
}
