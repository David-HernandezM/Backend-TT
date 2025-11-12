package com.ipn.escom.conversor_sql.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ipn.escom.conversor_sql.core.expresiones.CoreBinArith;
import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreParen;
import com.ipn.escom.conversor_sql.core.expresiones.CoreUnaryArith;
import com.ipn.escom.conversor_sql.core.predicados.CoreAnd;
import com.ipn.escom.conversor_sql.core.predicados.CoreBetween;
import com.ipn.escom.conversor_sql.core.predicados.CoreCmp;
import com.ipn.escom.conversor_sql.core.predicados.CoreExists;
import com.ipn.escom.conversor_sql.core.predicados.CoreInList;
import com.ipn.escom.conversor_sql.core.predicados.CoreInSubselect;
import com.ipn.escom.conversor_sql.core.predicados.CoreNot;
import com.ipn.escom.conversor_sql.core.predicados.CoreNotExists;
import com.ipn.escom.conversor_sql.core.predicados.CoreOr;
import com.ipn.escom.conversor_sql.core.predicados.CorePred;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjExpr;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjItem;
import com.ipn.escom.conversor_sql.core.relacionales.CoreAlias;
import com.ipn.escom.conversor_sql.core.relacionales.CoreExcept;
import com.ipn.escom.conversor_sql.core.relacionales.CoreIntersect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreNaturalJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProduct;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProject;
import com.ipn.escom.conversor_sql.core.relacionales.CoreRel;
import com.ipn.escom.conversor_sql.core.relacionales.CoreSelect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreTable;
import com.ipn.escom.conversor_sql.core.relacionales.CoreUnion;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

public final class SchemaGuards {
	private final SchemaIndex schema;

	public SchemaGuards(SchemaIndex schema) {
		this.schema = Objects.requireNonNull(schema);
	}

	public void checkTablesExist(CoreRel r, ValidationResult vr) {
		if (r instanceof CoreTable t) {
			String key = norm(t.name());
			if (!schema.columnsByTable().containsKey(key)) {
				vr.addErrorLogico("Tabla no encontrada en esquema: " + t.name());
			}
		} else if (r instanceof CoreAlias a) {
			checkTablesExist(a.input(), vr);
		} else if (r instanceof CoreProject p) {
			checkTablesExist(p.input(), vr);
		} else if (r instanceof CoreSelect s) {
			checkTablesExist(s.input(), vr);
		} else if (r instanceof CoreProduct x) {
			checkTablesExist(x.left(), vr);
			checkTablesExist(x.right(), vr);
		} else if (r instanceof CoreJoin j) {
			checkTablesExist(j.left(), vr);
			checkTablesExist(j.right(), vr);
		} else if (r instanceof CoreNaturalJoin nj) {
			checkTablesExist(nj.left(), vr);
			checkTablesExist(nj.right(), vr);
		} else if (r instanceof CoreUnion u) {
			checkTablesExist(u.left(), vr);
			checkTablesExist(u.right(), vr);
		} else if (r instanceof CoreIntersect i) {
			checkTablesExist(i.left(), vr);
			checkTablesExist(i.right(), vr);
		} else if (r instanceof CoreExcept e) {
			checkTablesExist(e.left(), vr);
			checkTablesExist(e.right(), vr);
		}
	}

	public void checkColumnsResolvable(CoreRel r, ValidationResult vr) {
		walkWithLocalVis(r, vr);
	}

	// ---- Walk por nodo con visibilidad local ----
	private void walkWithLocalVis(CoreRel r, ValidationResult vr) {
		Map<String, Set<String>> vis = visibleColumns(r); // contexto local de este nodo

		if (r instanceof CoreProject p) {
			// valida expresiones de proyección con vis local
			for (CoreProjItem it : p.items()) {
				if (it instanceof CoreProjExpr pe)
					checkExprWithVis(pe.expr(), vis, vr);
			}
			walkWithLocalVis(p.input(), vr);

		} else if (r instanceof CoreSelect s) {
			checkPredWithVis(s.predicate(), vis, vr);
			walkWithLocalVis(s.input(), vr);

		} else if (r instanceof CoreJoin j) {
			checkPredWithVis(j.on(), vis, vr);
			walkWithLocalVis(j.left(), vr);
			walkWithLocalVis(j.right(), vr);

		} else if (r instanceof CoreProduct x) {
			walkWithLocalVis(x.left(), vr);
			walkWithLocalVis(x.right(), vr);

		} else if (r instanceof CoreNaturalJoin nj) {
			walkWithLocalVis(nj.left(), vr);
			walkWithLocalVis(nj.right(), vr);

		} else if (r instanceof CoreUnion u) {
			// cada rama con su propio vis
			walkWithLocalVis(u.left(), vr);
			walkWithLocalVis(u.right(), vr);

		} else if (r instanceof CoreIntersect i) {
			walkWithLocalVis(i.left(), vr);
			walkWithLocalVis(i.right(), vr);

		} else if (r instanceof CoreExcept e) {
			walkWithLocalVis(e.left(), vr);
			walkWithLocalVis(e.right(), vr);

		} else if (r instanceof CoreAlias a) {
			// el vis de este nodo ya mapea TODO al alias
			walkWithLocalVis(a.input(), vr);

		} else if (r instanceof CoreTable) {
			// hoja: nada más que validar

		} else {
			vr.addErrorLogico("Nodo relacional no soportado: " + r.getClass().getSimpleName());
		}
	}

	private void checkPredWithVis(CorePred p, Map<String, Set<String>> vis, ValidationResult vr) {
		if (p == null)
			return;
		if (p instanceof CoreAnd a) {
			checkPredWithVis(a.a(), vis, vr);
			checkPredWithVis(a.b(), vis, vr);
		} else if (p instanceof CoreOr o) {
			checkPredWithVis(o.a(), vis, vr);
			checkPredWithVis(o.b(), vis, vr);
		} else if (p instanceof CoreNot n) {
			checkPredWithVis(n.a(), vis, vr);
		} else if (p instanceof CoreCmp c) {
			checkExprWithVis(c.left(), vis, vr);
			checkExprWithVis(c.right(), vis, vr);
		} else if (p instanceof CoreBetween bt) {
			checkExprWithVis(bt.value(), vis, vr);
			checkExprWithVis(bt.low(), vis, vr);
			checkExprWithVis(bt.high(), vis, vr);
		} else if (p instanceof CoreInList in) {
			checkExprWithVis(in.left(), vis, vr);
			for (CoreExpr e : in.rightList())
				checkExprWithVis(e, vis, vr);
		} else if (p instanceof CoreInSubselect is) {
			checkExprWithVis(is.left(), vis, vr);
			// el subselect se valida con su propio contexto en
			// NodeInvariants/CoreInvariants
		} else if (p instanceof CoreExists || p instanceof CoreNotExists) {
			// subselect validado por su lado
		}
	}

	private void checkExprWithVis(CoreExpr e, Map<String, Set<String>> vis, ValidationResult vr) {
		if (e instanceof CoreColumnRef c) {
			if (c.relOrNull() != null) {
				if (!existsQualified(vis, c.relOrNull(), c.name())) {
					vr.addErrorLogico("Columna no encontrada: " + q(c.relOrNull(), c.name()));
				}
			} else {
				var owners = ownersOf(vis, c.name());
				if (owners.isEmpty())
					vr.addErrorLogico("Columna no encontrada: " + c.name());
				else if (owners.size() > 1)
					vr.addErrorLogico("Columna ambigua: " + c.name() + " en " + owners);
			}
		} else if (e instanceof CoreParen p) {
			checkExprWithVis(p.inner(), vis, vr);
		} else if (e instanceof CoreUnaryArith ua) {
			checkExprWithVis(ua.a(), vis, vr);
		} else if (e instanceof CoreBinArith ba) {
			checkExprWithVis(ba.left(), vis, vr);
			checkExprWithVis(ba.right(), vis, vr);
		}
		// CoreNumber/CoreString/CoreNull no requieren visibilidad
	}

	private static String q(String rel, String col) {
		return (rel != null ? rel + "." : "") + col;
	}

	private static String norm(String s) {
		return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
	}

	private Map<String, Set<String>> visibleColumns(CoreRel r) {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		if (r instanceof CoreTable t) {
			String key = t.name();
			out.put(key, new LinkedHashSet<>(schema.columnsByTable().getOrDefault(norm(key), Set.of())));
		} else if (r instanceof CoreAlias a) {
			Map<String, Set<String>> inner = visibleColumns(a.input());
			Set<String> cols = inner.values().stream().flatMap(Set::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			out.put(a.alias(), cols);
		} else if (r instanceof CoreProject p) {
			out.putAll(visibleColumns(p.input()));
		} else if (r instanceof CoreSelect s) {
			out.putAll(visibleColumns(s.input()));
		} else if (r instanceof CoreProduct x) {
			out.putAll(visibleColumns(x.left()));
			visibleColumns(x.right()).forEach(out::putIfAbsent);
		} else if (r instanceof CoreJoin j) {
			out.putAll(visibleColumns(j.left()));
			visibleColumns(j.right()).forEach(out::putIfAbsent);
		} else if (r instanceof CoreNaturalJoin nj) {
			out.putAll(visibleColumns(nj.left()));
			visibleColumns(nj.right()).forEach(out::putIfAbsent);
		} else if (r instanceof CoreUnion u) {
			out.putAll(visibleColumns(u.left()));
		} else if (r instanceof CoreIntersect i) {
			out.putAll(visibleColumns(i.left()));
		} else if (r instanceof CoreExcept e) {
			out.putAll(visibleColumns(e.left()));
		}
		return out;
	}

	private boolean existsQualified(Map<String, Set<String>> vis, String rel, String col) {
		Set<String> cols = vis.getOrDefault(rel, Set.of());
		return cols.stream().map(SchemaGuards::norm).anyMatch(nc -> nc.equals(norm(col)));
	}

	private List<String> ownersOf(Map<String, Set<String>> vis, String col) {
		String n = norm(col);
		List<String> owners = new ArrayList<>();
		for (var entry : vis.entrySet()) {
			if (entry.getValue().stream().map(SchemaGuards::norm).anyMatch(c -> c.equals(n))) {
				owners.add(entry.getKey());
			}
		}
		return owners;
	}
}
