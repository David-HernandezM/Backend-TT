package com.ipn.escom.conversor_sql.validation.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.SqlValidator;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;
import com.ipn.escom.conversor_sql.validation.sql.SourceCollector.ResolvedColumn;
import com.ipn.escom.conversor_sql.validation.sql.TypeSystem.TypeCategory;

import net.sf.jsqlparser.statement.select.ExceptOp;
import net.sf.jsqlparser.statement.select.IntersectOp;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperation;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.UnionOp;

public class SetOpsValidator {
	/* ============================
    	  Tipos de proyección
	============================ */
	public static void validateSetOperationList(SetOperationList setOperationList, SchemaIndex schemaIndex,
			ValidationResult validationResult) {
		// Reglas sobre el tipo de operación (rechazar UNION ALL)
		if (setOperationList.getOperations() != null) {
			for (SetOperation op : setOperationList.getOperations()) {
				if (op instanceof UnionOp u && u.isAll()) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"UNION ALL no está permitido. Solo UNION (sin ALL), INTERSECT y EXCEPT/MINUS.");
				} else if (!(op instanceof UnionOp) && !(op instanceof IntersectOp) && !(op instanceof ExceptOp)) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"Operación de conjunto no soportada. Solo UNION, INTERSECT y EXCEPT/MINUS.");
				}
			}
		}

		// Obtener los SELECTs
		List<SelectBody> bodies = setOperationList.getSelects();
		if (bodies == null || bodies.isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, "Operación de conjunto sin SELECTs.");
			return;
		}

		// Para comparar aridad y tipos por posición, generamos la proyección tipada de cada SELECT.
		List<List<TypeCategory>> projectionTypesBySelect = new ArrayList<>();
		Integer arity = null;

		for (SelectBody sub : bodies) {
			if (!(sub instanceof PlainSelect subPlain)) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"El tipo de subconsulta en la operación de conjunto no está soportado.");
				continue;
			}

			// Fuentes del sub SELECT
			List<Source> subSources = SourceCollector.collectFromSources(subPlain, validationResult);

			// Validar tablas del sub SELECT
			for (Source s : subSources) {
				if (!schemaIndex.definedTablesLower().contains(s.tableReal())) {
					validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
							"La tabla '" + s.originalToken() + "' no está definida en el esquema.");
				}
			}

			// Validar SELECT list con reglas base
			ProjectionValidator.validateSelectList(subPlain.getSelectItems(), subSources, schemaIndex,
					validationResult);
			// En set operations, exigimos proyección explícita y columnas simples (sin * ni tabla.*)
			if (ProjectionValidator.containsAllColumns(subPlain.getSelectItems())) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"En operaciones de conjunto no se permite SELECT * ni tabla.*; usa proyección explícita.");
			}

			// Cláusulas no permitidas en el sub SELECT (ya son globales, pero reforzamos)
			SqlValidator.disallowUnsupportedClauses(subPlain, validationResult);

			// WHERE/JOINS válidos
			Set<String> subAliases = ProjectionValidator.collectAndValidateColumnAliases(subPlain.getSelectItems(),
					subSources, validationResult);
			WhereValidator.validateWhereNestingDepth(subPlain.getWhere(), validationResult);
			WhereValidator.validateWhereTypes(subPlain.getWhere(), subSources, schemaIndex, validationResult, subAliases);
			JoinValidator.validateJoinOnTypes(subPlain.getJoins(), subSources, schemaIndex, validationResult, subAliases);

			// Obtener tipos de proyección por posición
			List<TypeCategory> projTypes = projectTypesForPlainSelect(subPlain, subSources, schemaIndex,
					validationResult);
			if (projTypes == null) {
				// Si falla, ya se registraron mensajes; seguimos para acumular
				continue;
			}
			projectionTypesBySelect.add(projTypes);

			// Validar aridad constante
			if (arity == null) {
				arity = projTypes.size();
			} else if (!arity.equals(projTypes.size())) {
				validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
						"Los SELECT dentro de la operación de conjunto no proyectan la misma cantidad de columnas.");
			}
		}

		// Comparar tipos por posición entre todos los SELECTs
		if (arity != null && projectionTypesBySelect.size() > 1) {
			List<TypeCategory> baseline = projectionTypesBySelect.get(0);
			for (int i = 1; i < projectionTypesBySelect.size(); i++) {
				List<TypeCategory> current = projectionTypesBySelect.get(i);
				if (current.size() != baseline.size())
					continue; // ya registrado arriba
				for (int pos = 0; pos < baseline.size(); pos++) {
					TypeCategory a = baseline.get(pos);
					TypeCategory b = current.get(pos);
					// Compatibilidad estricta por posición (mismo Category)
					if (a != b) {
						validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
								"Tipos incompatibles en posición " + (pos + 1)
										+ " entre SELECTs de la operación de conjunto: " + a + " vs " + b + ".");
					}
				}
			}
		}

		// ORDER BY a nivel de conjunto no permitido (regla original)
		if (setOperationList.getOrderByElements() != null && !setOperationList.getOrderByElements().isEmpty()) {
			validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
					"No se permite el uso de ORDER BY fuera de operaciones de conjunto.");
		}
	}
	
	/**
     * Devuelve la lista de tipos de la proyección (por posición) para un PlainSelect,
     * asumiendo que:
     *  - No hay AllColumns ni AllTableColumns (se valida afuera).
     *  - Cada item es una columna simple (se valida afuera).
     */
    public static List<TypeCategory> projectTypesForPlainSelect(PlainSelect ps,
                                                                 List<Source> sources,
                                                                 SchemaIndex schemaIndex,
                                                                 ValidationResult validationResult) {
        List<SelectItem> items = ps.getSelectItems();
        if (items == null || items.isEmpty()) return List.of();

        List<TypeCategory> types = new ArrayList<>();
        for (SelectItem it : items) {
            if (!(it instanceof SelectExpressionItem sei)) {
                validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                        "En operaciones de conjunto, cada SELECT debe proyectar columnas simples.");
                return null;
            }
            if (!(sei.getExpression() instanceof net.sf.jsqlparser.schema.Column col)) {
                validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO,
                        "En operaciones de conjunto, cada SELECT debe proyectar columnas simples (sin expresiones).");
                return null;
            }
            ResolvedColumn rc = SourceCollector.resolveColumn(col, sources, schemaIndex);
            if (rc.error() != null) {
                validationResult.agregarMensaje("error", TipoDetallado.ERROR_LOGICO, rc.error());
                return null;
            }
            types.add(TypeSystem.toCategory(rc.type()));
        }
        return types;
    }
}
