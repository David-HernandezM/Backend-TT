package com.ipn.escom.conversor_sql.validation.sql;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.utils.Texts;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.SourceCollector.ResolvedColumn;

import net.sf.jsqlparser.expression.Expression;

public class TypeSystem {
	/*
	 * ============================ Tipos y compatibilidad
	 * ============================
	 */

	public enum TypeCategory {
		NUMERIC, TEXT, DATE, BOOLEAN, UNKNOWN
	}

	public static TypeCategory toCategory(String dbTypeLower) {
		if (dbTypeLower == null)
			return TypeCategory.UNKNOWN;
		// numéricos
		if (dbTypeLower.contains("int") || dbTypeLower.contains("number") || dbTypeLower.contains("decimal")
				|| dbTypeLower.contains("float") || dbTypeLower.contains("double"))
			return TypeCategory.NUMERIC;
		// texto
		if (dbTypeLower.contains("char") || dbTypeLower.contains("clob") || dbTypeLower.contains("text")
				|| dbTypeLower.contains("string") || dbTypeLower.contains("varchar"))
			return TypeCategory.TEXT;
		// fecha/tiempo
		if (dbTypeLower.contains("date") || dbTypeLower.contains("timestamp"))
			return TypeCategory.DATE;
		// boolean
		if (dbTypeLower.contains("bool"))
			return TypeCategory.BOOLEAN;
		return TypeCategory.UNKNOWN;
	}

	public static TypeCategory literalCategory(Expression expr) {
		String s = expr.toString().trim();
		// texto entre comillas
		if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))
			return TypeCategory.TEXT;
		// DATE 'YYYY-MM-DD' (estilo Oracle)
		if (s.toUpperCase(Locale.ROOT).startsWith("DATE "))
			return TypeCategory.DATE;
		// número
		if (s.matches("^-?\\d+(\\.\\d+)?$"))
			return TypeCategory.NUMERIC;
		// true/false
		if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))
			return TypeCategory.BOOLEAN;

		return TypeCategory.UNKNOWN;
	}

	public static TypeCategory expressionCategory(Expression expr, List<Source> sources, SchemaIndex schemaIndex,
			ValidationResult validationResult, Set<String> selectColumnAliases) {
		if (expr instanceof net.sf.jsqlparser.schema.Column colExpr) {
			// Prohibir uso de alias de columna en WHERE/ON (si no está calificada y coincide con un alias del SELECT)
			if (colExpr.getTable() == null && selectColumnAliases != null) {
				String colLower = Texts.toLowerTrimmed(colExpr.getColumnName());
				if (colLower != null && selectColumnAliases.contains(colLower)) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"No se permite referenciar alias de columna ('" + colExpr.getColumnName()
									+ "') en WHERE/ON.");
				}
			}

			ResolvedColumn rc = SourceCollector.resolveColumn(colExpr, sources, schemaIndex);
			if (rc.error() != null) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, rc.error());
				return TypeCategory.UNKNOWN;
			}
			return toCategory(rc.type());
		}
		return literalCategory(expr);
	}
	
	public static boolean areComparable(TypeCategory left, TypeCategory right, String operator) {
        if (left == TypeCategory.UNKNOWN || right == TypeCategory.UNKNOWN) return true; // permisivo en fase 1
        operator = operator.toUpperCase(Locale.ROOT);

        // Igualdad
        if (operator.equals("=") || operator.equals("<>")) {
            return left == right;
        }
        // Orden
        if (operator.equals(">") || operator.equals(">=") || operator.equals("<") || operator.equals("<=")) {
            return (left == TypeCategory.NUMERIC && right == TypeCategory.NUMERIC)
                    || (left == TypeCategory.DATE && right == TypeCategory.DATE);
        }
        // LIKE
        if (operator.equals("LIKE")) {
            return left == TypeCategory.TEXT && right == TypeCategory.TEXT;
        }
        return true;
    }
}
