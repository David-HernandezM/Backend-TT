package com.ipn.escom.conversor_sql.validation;

import java.util.List;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.SqlRequest;
import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.schema.SchemaBuilder;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.JoinValidator;
import com.ipn.escom.conversor_sql.validation.sql.ProjectionValidator;
import com.ipn.escom.conversor_sql.validation.sql.SetOpsValidator;
import com.ipn.escom.conversor_sql.validation.sql.Source;
import com.ipn.escom.conversor_sql.validation.sql.SourceCollector;
import com.ipn.escom.conversor_sql.validation.sql.SqlParser;
import com.ipn.escom.conversor_sql.validation.sql.WhereValidator;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;

/**
 * Valida (fase 1) una sentencia SELECT contra un esquema dado. - Reglas de
 * esquema (PK única, FK debe referenciar PK y coincidir tipos) - Subconjunto
 * SQL permitido (sin GROUP BY/HAVING/ORDER BY) - Resolución de alias de tablas
 * y columnas; ambigüedad de columnas - Validación de tipos en WHERE y JOIN ON;
 * NATURAL JOIN con verificación de columnas comunes - IN (SubSelect) con
 * restricciones; Set operations estrictas (sin UNION ALL, aridad y tipos por
 * posición) - Permite SELECT * y tabla.* (excepto en set operations)
 */
public class SqlValidator {
	/*
	 * ============================ API ============================
	 */

	public static ValidationResult validar(SqlRequest sqlRequest) {
		ValidationResult validationResult = new ValidationResult();

		// 1) Validación básica del request
		if (!validateRequestBasics(sqlRequest, validationResult))
			return validationResult;

		// 2) Índice del esquema enviado por el usuario
		SchemaIndex schemaIndex = SchemaBuilder.buildSchemaIndex(sqlRequest.getTables(), validationResult);
		if (!validationResult.isValido())
			return validationResult;

		// 3) Parseo de la sentencia SQL
		Statement parsedStatement = SqlParser.parseSql(sqlRequest.getSqlQuery(), validationResult);
		if (!validationResult.isValido())
			return validationResult;

		// 4) Validación semántica limitada a SELECT
		validateSelectStatement(parsedStatement, schemaIndex, validationResult);
		return validationResult;
	}

	/*
	 * ============================ Validación de SELECT
	 * ============================
	 */

	private static void validateSelectStatement(Statement parsedStatement, SchemaIndex schemaIndex,
			ValidationResult validationResult) {
		if (!(parsedStatement instanceof Select selectStatement)) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "Solo se permiten sentencias SELECT.");
			return;
		}

		SelectBody selectBody = selectStatement.getSelectBody();
		if (selectBody instanceof PlainSelect plainSelect) {
			validatePlainSelect(plainSelect, schemaIndex, validationResult);
		} else if (selectBody instanceof SetOperationList setOperationList) {
			SetOpsValidator.validateSetOperationList(setOperationList, schemaIndex, validationResult);
		} else {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"La estructura SELECT no es compatible con el sistema.");
		}
	}

	private static void validatePlainSelect(PlainSelect plainSelect, SchemaIndex schemaIndex,
			ValidationResult validationResult) {
		// 1) Recolectar fuentes con alias (FROM + JOIN), detectando alias duplicados
		List<Source> availableSources = SourceCollector.collectFromSources(plainSelect, validationResult);
		if (!validationResult.isValido())
			return;

		// 2) Validar que todas las tablas existan en el esquema
		for (Source source : availableSources) {
			if (!schemaIndex.definedTablesLower().contains(source.tableReal())) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"La tabla '" + source.originalToken() + "' no está definida en el esquema.");
			}
		}
		if (!validationResult.isValido())
			return;

		// 3) Validar la lista de SELECT (permitimos * y tabla.*)
		ProjectionValidator.validateSelectList(plainSelect.getSelectItems(), availableSources, schemaIndex,
				validationResult);
		if (!validationResult.isValido())
			return;

		// 3.1) Recolectar y validar alias de columnas (duplicados / colisiones con alias de tablas)
		Set<String> selectColumnAliases = ProjectionValidator
				.collectAndValidateColumnAliases(plainSelect.getSelectItems(), availableSources, validationResult);
		if (!validationResult.isValido())
			return;

		// 4) Cláusulas no permitidas
		disallowUnsupportedClauses(plainSelect, validationResult);
		if (!validationResult.isValido())
			return;

		// 5) Joins permitidos y NATURAL JOIN checks
		JoinValidator.validateAllowedJoinTypes(plainSelect.getJoins(), validationResult);
		if (!validationResult.isValido())
			return;

		JoinValidator.validateNaturalJoinCompatibility(plainSelect, availableSources, schemaIndex, validationResult);
		if (!validationResult.isValido())
			return;

		// 6) WHERE: limitar anidación y validar tipos (con alias de columna prohibidos)
		WhereValidator.validateWhereNestingDepth(plainSelect.getWhere(), validationResult);
		if (!validationResult.isValido())
			return;

		// 7) WHERE: validar tipos de las expresiones 
		WhereValidator.validateWhereTypes(plainSelect.getWhere(), availableSources, schemaIndex, validationResult,
				selectColumnAliases);
		if (!validationResult.isValido())
			return;

		// 8) JOIN ... ON: validar tipos en condiciones ON (y prohibir alias de columna)
		JoinValidator.validateJoinOnTypes(plainSelect.getJoins(), availableSources, schemaIndex, validationResult,
				selectColumnAliases);
	}

	/*
	 * ============================ Reglas adicionales ============================
	 */

	public static void disallowUnsupportedClauses(PlainSelect plainSelect, ValidationResult validationResult) {
		if (plainSelect.getGroupBy() != null) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"No se permite el uso de GROUP BY en la consulta.");
		}
		if (plainSelect.getHaving() != null) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"No se permite el uso de HAVING en la consulta.");
		}
		if (plainSelect.getOrderByElements() != null && !plainSelect.getOrderByElements().isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"No se permite el uso de ORDER BY en la consulta.");
		}
	}

	/*
	 * ============================ Utilidades ============================
	 */

	private static boolean validateRequestBasics(SqlRequest sqlRequest, ValidationResult validationResult) {
		if (sqlRequest.getSqlQuery() == null || sqlRequest.getSqlQuery().trim().isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"El campo 'sqlQuery' no puede estar vacío.");
		}
		if (sqlRequest.getTables() == null || sqlRequest.getTables().isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"Debe haber al menos una tabla definida.");
		}
		return validationResult.isValido();
	}
}
