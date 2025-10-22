package com.ipn.escom.conversor_sql.validation.sql;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.TypeSystem.TypeCategory;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

public class JoinValidator {
	public static void validateJoinOnTypes(List<Join> joinClauses, List<Source> sources, SchemaIndex schemaIndex,
			ValidationResult validationResult, Set<String> selectColumnAliases) {
		if (joinClauses == null)
			return;

		for (Join joinClause : joinClauses) {
			// INNER JOIN ... ON (una o varias expresiones)
			if (joinClause.isInner() && joinClause.getOnExpressions() != null) {
				for (Expression onExpr : joinClause.getOnExpressions()) {
					WhereValidator.validateWhereTypes(onExpr, sources, schemaIndex, validationResult,
							selectColumnAliases);
				}
			}
			// NATURAL JOIN se valida en validateNaturalJoinCompatibility(...)
		}
	}

	/**
	 * Verifica NATURAL JOIN: - Debe haber al menos una columna común por nombre
	 * entre las tablas adyacentes (izquierda/derecha). - Las columnas comunes deben
	 * tener tipos compatibles.
	 *
	 * Nota: asumimos la tabla izquierda como la fuente previa (FROM o el último
	 * RIGHT de join). Es una aproximación suficiente para esta fase.
	 */
	public static void validateNaturalJoinCompatibility(PlainSelect plainSelect, List<Source> sourcesInOrder,
			SchemaIndex schemaIndex, ValidationResult validationResult) {
		if (plainSelect.getJoins() == null || plainSelect.getJoins().isEmpty())
			return;

		// sourcesInOrder: [FROM, J1.right, J2.right, ...]
		List<Join> joins = plainSelect.getJoins();

		for (int i = 0; i < joins.size(); i++) {
			Join j = joins.get(i);
			if (!j.isNatural())
				continue;

			// Tomamos la izquierda como la fuente previa en 'sourcesInOrder'
			if (i >= sourcesInOrder.size() - 1)
				continue; // seguridad
			Source left = sourcesInOrder.get(i); // FROM o acumulado anterior (aprox)
			Source right = sourcesInOrder.get(i + 1); // tabla del JOIN actual

			Set<String> leftCols = schemaIndex.columnsByTable().getOrDefault(left.tableReal(), Set.of());
			Set<String> rightCols = schemaIndex.columnsByTable().getOrDefault(right.tableReal(), Set.of());

			// Intersección de columnas por nombre
			Set<String> common = new HashSet<>(leftCols);
			common.retainAll(rightCols);

			if (common.isEmpty()) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"NATURAL JOIN entre '" + left.originalToken() + "' y '" + right.originalToken()
								+ "' no tiene columnas comunes por nombre.");
				continue;
			}

			// Tipos compatibles en las columnas comunes
			for (String col : common) {
				String lt = schemaIndex.columnTypesByTable().getOrDefault(left.tableReal(), Map.of()).get(col);
				String rt = schemaIndex.columnTypesByTable().getOrDefault(right.tableReal(), Map.of()).get(col);
				TypeCategory ltc = TypeSystem.toCategory(lt);
				TypeCategory rtc = TypeSystem.toCategory(rt);
				if (ltc != rtc) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"NATURAL JOIN columnas comunes con tipos incompatibles: '" + col + "' en '"
									+ left.originalToken() + "' (" + ltc + ") vs '" + right.originalToken() + "' ("
									+ rtc + ").");
				}
			}
		}
	}

	public static void validateAllowedJoinTypes(List<Join> joinClauses, ValidationResult validationResult) {
		if (joinClauses == null)
			return;
		for (Join joinClause : joinClauses) {
			if (joinClause.isLeft() || joinClause.isRight() || joinClause.isFull()) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"Solo se permiten INNER JOIN o NATURAL JOIN. No se permiten LEFT/RIGHT/FULL JOIN.");
			}
		}
	}
}
