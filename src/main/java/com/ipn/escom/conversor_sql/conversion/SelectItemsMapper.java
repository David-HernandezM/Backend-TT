package com.ipn.escom.conversor_sql.conversion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ipn.escom.conversor_sql.core.CoreAttr;
import com.ipn.escom.conversor_sql.core.CoreCross;
import com.ipn.escom.conversor_sql.core.CoreIntersect;
import com.ipn.escom.conversor_sql.core.CoreJoin;
import com.ipn.escom.conversor_sql.core.CoreMinus;
import com.ipn.escom.conversor_sql.core.CoreNodeInterface;
import com.ipn.escom.conversor_sql.core.CoreProject;
import com.ipn.escom.conversor_sql.core.CoreSelect;
import com.ipn.escom.conversor_sql.core.CoreSemiJoin;
import com.ipn.escom.conversor_sql.core.CoreTable;
import com.ipn.escom.conversor_sql.core.CoreRename;
import com.ipn.escom.conversor_sql.core.CoreUnion;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

public final class SelectItemsMapper {
	  private SelectItemsMapper() {}

	  public static List<CoreAttr> expand(List<SelectItem> items, CoreNodeInterface scope, SchemaIndex schema) {
	    if (items == null || items.isEmpty()) {
	      throw new IllegalArgumentException("SELECT sin columnas no es válido.");
	    }

	    // 1) Recolectar las relaciones visibles en el scope (alias->nombreReal o nombre)
	    Map<String,String> visibles = collectVisibleRelations(scope);

	    // 2) Procesar items
	    List<CoreAttr> out = new ArrayList<>();
	    for (SelectItem it : items) {
	      if (it instanceof AllColumns) {
	        // SELECT *
	        out.addAll(expandAll(visibles, schema));
	      } else if (it instanceof SelectExpressionItem sei) {
	        String aliasCol = (sei.getAlias() != null) ? sei.getAlias().getName() : null;

	        if (sei.getExpression() instanceof Column col) {
	          String tbl = (col.getTable() != null && col.getTable().getName() != null)
	              ? col.getTable().getName()
	              : null;
	          String name = col.getColumnName();

	          // Si la columna viene calificada con tabla/alias, úsala;
	          // si no, intenta resolverla en el único origen visible
	          if (tbl == null) {
	            tbl = resolveSingleRelationOrNull(visibles);
	          }

	          // Validación mínima (puedes reforzarla en CoreValidator)
	          if (tbl == null && visibles.size() > 1) {
	            throw new IllegalArgumentException("Columna ambigua sin calificar: '" + name + "'");
	          }
	          String realTable = (tbl != null) ? visibles.getOrDefault(tbl, tbl) : resolveSingleRelationOrThrow(visibles);

	          // Opcional: verificar existencia de columna en el schema
	          if (!schema.hasColumnIgnoreCase(realTable, name)) {
	            throw new IllegalArgumentException("Columna no encontrada: '" + name + "' en tabla '" + realTable + "'");
	          }

	          // El 'rel' del CoreAttr debe ser el IDENTIFICADOR visible en la salida (alias si existe)
	          String relVisible = (tbl != null) ? tbl : resolveSingleRelationOrThrow(visibles);

	          out.add(new CoreAttr(relVisible, name, aliasCol));
	        } else {
	          // Solo soportamos columnas en el MVP (sin expresiones)
	          throw new IllegalArgumentException("Solo se admiten columnas simples en SELECT (sin expresiones) en el MVP.");
	        }
	      } else if (it instanceof AllTableColumns atc) {
	        // SELECT t.*  -> expandir solo para esa tabla/alias
	        String tbl = atc.getTable().getName();
	        if (tbl == null) throw new IllegalArgumentException("AllTableColumns sin nombre de tabla.");
	        String realTable = visibles.getOrDefault(tbl, tbl);
	        out.addAll(expandTable(tbl, realTable, schema));
	      } else {
	        throw new IllegalArgumentException("SelectItem no soportado: " + it);
	      }
	    }
	    return out;
	  }

	  // --- helpers ---

	  private static Map<String,String> collectVisibleRelations(CoreNodeInterface n) {
	    Map<String,String> map = new LinkedHashMap<>();
	    collect(n, map);
	    return map;
	  }

	  private static void collect(CoreNodeInterface n, Map<String,String> out) {
	    if (n instanceof CoreTable t) {
	      // key = alias si existe; value = nombre real en schema
	      String key = (t.alias() != null) ? t.alias() : t.name();
	      out.putIfAbsent(key, t.name());
	    } else if (n instanceof CoreRename r) {
	      collect(r.input(), out);
	    } else if (n instanceof CoreProject p) {
	      collect(p.input(), out);
	    } else if (n instanceof CoreSelect s) {
	      collect(s.input(), out);
	    } else if (n instanceof CoreJoin j) {
	      collect(j.left(), out);
	      collect(j.right(), out);
	    } else if (n instanceof CoreCross c) {
	      collect(c.left(), out);
	      collect(c.right(), out);
	    } else if (n instanceof CoreUnion u) {
	      // Para set-ops, el header debe ser compatible; aquí no sumamos relaciones
	      collect(u.left(), out);
	    } else if (n instanceof CoreIntersect i) {
	      collect(i.left(), out);
	    } else if (n instanceof CoreMinus m) {
	      collect(m.left(), out);
	    } else if (n instanceof CoreSemiJoin sj) {
	      collect(sj.left(), out);
	    }
	  }

	  private static List<CoreAttr> expandAll(Map<String,String> visibles, SchemaIndex schema) {
	    return visibles.entrySet().stream()
	        .flatMap(e -> expandTable(e.getKey(), e.getValue(), schema).stream())
	        .collect(Collectors.toList());
	  }

	  private static List<CoreAttr> expandTable(String visibleRel, String realTable, SchemaIndex schema) {
	    List<String> cols = schema.getColumnsIgnoreCase(realTable);
	    if (cols == null || cols.isEmpty()) {
	      throw new IllegalArgumentException("La tabla '" + realTable + "' no tiene columnas en el schema.");
	    }
	    List<CoreAttr> out = new ArrayList<>(cols.size());
	    for (String c : cols) {
	      out.add(new CoreAttr(visibleRel, c, null));
	    }
	    return out;
	  }

	  private static String resolveSingleRelationOrNull(Map<String,String> visibles) {
	    return (visibles.size() == 1) ? visibles.keySet().iterator().next() : null;
	  }

	  private static String resolveSingleRelationOrThrow(Map<String,String> visibles) {
	    if (visibles.size() == 1) return visibles.keySet().iterator().next();
	    throw new IllegalArgumentException("Columna sin calificar y existen múltiples relaciones visibles.");
	  }
	}