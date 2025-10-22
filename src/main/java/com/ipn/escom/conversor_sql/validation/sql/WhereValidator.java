package com.ipn.escom.conversor_sql.validation.sql;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.SqlValidator;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.SourceCollector.ResolvedColumn;
import com.ipn.escom.conversor_sql.validation.sql.TypeSystem.TypeCategory;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;

public class WhereValidator {
	public static void validateWhereTypes(Expression whereExpr, List<Source> sources, SchemaIndex schemaIndex,
			ValidationResult validationResult, Set<String> selectColumnAliases) {
		if (whereExpr == null)
			return;

		// Comparaciones binarias ( =, <>, <, <=, >, >= )
		if (whereExpr instanceof EqualsTo e) {
			checkBinaryComparison(e.getLeftExpression(), "=", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}
		if (whereExpr instanceof NotEqualsTo e) {
			checkBinaryComparison(e.getLeftExpression(), "<>", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}
		if (whereExpr instanceof GreaterThan e) {
			checkBinaryComparison(e.getLeftExpression(), ">", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}
		if (whereExpr instanceof GreaterThanEquals e) {
			checkBinaryComparison(e.getLeftExpression(), ">=", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}
		if (whereExpr instanceof MinorThan e) {
			checkBinaryComparison(e.getLeftExpression(), "<", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}
		if (whereExpr instanceof MinorThanEquals e) {
			checkBinaryComparison(e.getLeftExpression(), "<=", e.getRightExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			return;
		}

		if (whereExpr instanceof LikeExpression) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "El operador LIKE no está permitido.");
			return;
		}

		// BETWEEN
		if (whereExpr instanceof Between between) {
			if (between.getLeftExpression() instanceof NullValue
					|| between.getBetweenExpressionStart() instanceof NullValue
					|| between.getBetweenExpressionEnd() instanceof NullValue) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"No se permite usar NULL en BETWEEN.");
				return;
			}

			TypeCategory exprCat = TypeSystem.expressionCategory(between.getLeftExpression(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			TypeCategory startCat = TypeSystem.expressionCategory(between.getBetweenExpressionStart(), sources, schemaIndex,
					validationResult, selectColumnAliases);
			TypeCategory endCat = TypeSystem.expressionCategory(between.getBetweenExpressionEnd(), sources, schemaIndex,
					validationResult, selectColumnAliases);

			if (!(exprCat == TypeCategory.NUMERIC || exprCat == TypeCategory.DATE) || exprCat != startCat
					|| exprCat != endCat) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"Tipos incompatibles en BETWEEN: se requiere NUMERIC o DATE de forma consistente.");
			}

			return;
		}

		// IN (lista de literales simples o SubSelect con restricciones)
		if (whereExpr instanceof InExpression in) {
			if (ExpressionUtils.containsNullSyntax(in.getLeftExpression())) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"No se permite usar NULL en la expresión de la izquierda de IN.");
				return;
			}

			TypeCategory left = TypeSystem.expressionCategory(in.getLeftExpression(), sources, schemaIndex, validationResult,
					selectColumnAliases);

			if (in.getRightItemsList() instanceof ExpressionList list && list.getExpressions() != null) {
				for (Expression item : list.getExpressions()) {
					if (item instanceof NullValue || item instanceof IsNullExpression) {
						validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
								"No se permite usar NULL en listas de IN.");
						return;
					}
				}
			}

			if (in.getRightItemsList() instanceof ExpressionList list) {
				for (Expression item : list.getExpressions()) {
					TypeCategory itemCat = TypeSystem.expressionCategory(item, sources, schemaIndex, validationResult,
							selectColumnAliases);
					if (!TypeSystem.areComparable(left, itemCat, "=")) {
						validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
								"Tipos incompatibles en IN: " + left + " vs " + itemCat);
						break;
					}
				}
				return;
			} else if (in.getRightItemsList() instanceof SubSelect sub) {
				// NUEVO: IN (SELECT …) con restricciones
				validateInSubselect(left, sub, sources, schemaIndex, validationResult);
				return;
			}
		}

		// IS NULL / IS NOT NULL: prohibido en AR
		if (whereExpr instanceof IsNullExpression) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"El uso de NULL o IS [NOT] NULL no está permitido.");
			return;
		}

		// (expr) → validar recursivamente
		if (whereExpr instanceof net.sf.jsqlparser.expression.Parenthesis p) {
			validateWhereTypes(p.getExpression(), sources, schemaIndex, validationResult, selectColumnAliases);
			return;
		}

		// AND / OR / NOT → validar recursivamente
		if (whereExpr instanceof AndExpression andExpr) {
			validateWhereTypes(andExpr.getLeftExpression(), sources, schemaIndex, validationResult,
					selectColumnAliases);
			validateWhereTypes(andExpr.getRightExpression(), sources, schemaIndex, validationResult,
					selectColumnAliases);
			return;
		}
		if (whereExpr instanceof OrExpression orExpr) {
			validateWhereTypes(orExpr.getLeftExpression(), sources, schemaIndex, validationResult, selectColumnAliases);
			validateWhereTypes(orExpr.getRightExpression(), sources, schemaIndex, validationResult,
					selectColumnAliases);
			return;
		}
		if (whereExpr instanceof NotExpression notExpr) {
			validateWhereTypes(notExpr.getExpression(), sources, schemaIndex, validationResult, selectColumnAliases);
		}
	}

	public static void checkBinaryComparison(Expression leftExpr, String operator, Expression rightExpr,
			List<Source> sources, SchemaIndex schemaIndex, ValidationResult validationResult,
			Set<String> selectColumnAliases) {
		// Comparaciones con NULL no permitidas
		if (ExpressionUtils.containsNullSyntax(leftExpr) || ExpressionUtils.containsNullSyntax(rightExpr)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Comparaciones con NULL no están permitidas.");
			return;
		}

		TypeCategory left = TypeSystem.expressionCategory(leftExpr, sources, schemaIndex, validationResult, selectColumnAliases);
		TypeCategory right = TypeSystem.expressionCategory(rightExpr, sources, schemaIndex, validationResult, selectColumnAliases);

		if (!TypeSystem.areComparable(left, right, operator)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Tipos incompatibles en comparación (" + operator + "): " + left + " vs " + right);
		}
	}

	/** IN (SELECT …) con reglas estrictas y verificación de tipos. */
	public static void validateInSubselect(TypeCategory leftSideType, SubSelect sub, List<Source> outerSources,
			SchemaIndex schemaIndex, ValidationResult validationResult) {
		SelectBody body = sub.getSelectBody();
		if (!(body instanceof PlainSelect ps)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"IN (subconsulta) debe ser un SELECT simple (PlainSelect).");
			return;
		}

		// Sin GROUP BY/HAVING/ORDER BY
		if (ps.getGroupBy() != null || ps.getHaving() != null
				|| (ps.getOrderByElements() != null && !ps.getOrderByElements().isEmpty())) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"IN (SELECT …) no puede usar GROUP BY, HAVING ni ORDER BY.");
		}

		// Fuentes del sub SELECT y validación de tablas
		List<Source> subSources = SourceCollector.collectFromSources(ps, validationResult);
		for (Source s : subSources) {
			if (!schemaIndex.definedTablesLower().contains(s.tableReal())) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"La tabla '" + s.originalToken() + "' no está definida en el esquema.");
			}
		}

		// Proyección: exactamente 1 ítem, sin * ni tabla.*, y debe ser columna simple
		List<SelectItem> items = ps.getSelectItems();
		if (items == null || items.size() != 1) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"IN (SELECT …) debe proyectar exactamente una columna.");
			return;
		}
		if (ProjectionValidator.containsAllColumns(items)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"IN (SELECT …) no permite SELECT * ni tabla.*; usa una columna explícita.");
			return;
		}
		SelectItem only = items.get(0);
		if (!(only instanceof SelectExpressionItem sei)
				|| !(sei.getExpression() instanceof net.sf.jsqlparser.schema.Column subCol)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"IN (SELECT …) debe proyectar una columna simple (sin expresiones).");
			return;
		}

		// WHERE de la subconsulta: no debe contener otra subconsulta (un nivel total)
		if (ps.getWhere() != null && ps.getWhere().toString().toLowerCase(Locale.ROOT).contains("select")) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Solo se permite un nivel de subconsulta en WHERE; la subconsulta del IN no puede anidar otra subconsulta.");
		}

		// Validaciones usuales en subconsulta: SELECT list simple, cláusulas no
		// permitidas, tipos en WHERE/ON, joins válidos
		ProjectionValidator.validateSelectList(items, subSources, schemaIndex, validationResult);
		Set<String> subAliases = ProjectionValidator.collectAndValidateColumnAliases(items, subSources,
				validationResult);
		SqlValidator.disallowUnsupportedClauses(ps, validationResult);
		JoinValidator.validateAllowedJoinTypes(ps.getJoins(), validationResult);
		JoinValidator.validateNaturalJoinCompatibility(ps, subSources, schemaIndex, validationResult);
		validateWhereTypes(ps.getWhere(), subSources, schemaIndex, validationResult, subAliases);
		JoinValidator.validateJoinOnTypes(ps.getJoins(), subSources, schemaIndex, validationResult, subAliases);

		// Tipo de la única columna proyectada en la subconsulta
		ResolvedColumn rc = SourceCollector.resolveColumn(subCol, subSources, schemaIndex);
		if (rc.error() != null) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, rc.error());
			return;
		}
		TypeCategory rightType = TypeSystem.toCategory(rc.type());

		// Compatibilidad con el lado izquierdo del IN
		if (!TypeSystem.areComparable(leftSideType, rightType, "=")) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Tipos incompatibles entre la columna izquierda del IN y la columna proyectada por la subconsulta: "
							+ leftSideType + " vs " + rightType + ".");
		}
	}

	public static void validateWhereNestingDepth(Expression whereExpression, ValidationResult validationResult) {
		if (whereExpression == null)
			return;
		String whereTextLower = whereExpression.toString().toLowerCase(Locale.ROOT);
		if (whereTextLower.contains("select")) {
			long parenthesisCount = whereTextLower.chars().filter(ch -> ch == '(').count();
			if (parenthesisCount > 1) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"Solo se permite un nivel de anidación en la cláusula WHERE.");
			}
		}
	}
}
