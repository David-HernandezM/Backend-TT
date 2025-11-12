package com.ipn.escom.conversor_sql.conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNumber;
import com.ipn.escom.conversor_sql.core.predicados.CmpOp;
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
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjAll;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjAllFrom;
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
import com.ipn.escom.conversor_sql.core.subselect.CoreSubselect;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

/**
 * Normalizador SQL→Core (RA clásica): - NATURAL JOIN → JOIN θ + PROJECT
 * (colapsa duplicadas). - BETWEEN → AND de dos comparaciones. - IN (v1,...) →
 * opcionalmente deja CoreInList (lo mapeará PredMapper) o desazúcar a ORs. - IN
 * (subsel) / EXISTS(subsel) (positivos) → se levantan a JOINs contra relación
 * derivada. - Expansión de * / t.* requiere SchemaIndex (inyectado).
 *
 * Salida: CoreRel "puro" con { CoreProject, CoreSelect, CoreProduct, CoreJoin,
 * CoreUnion, CoreIntersect, CoreExcept, CoreAlias, CoreTable } y predicados en
 * { CoreCmp, CoreAnd, CoreOr, CoreNot, CoreInList } (sin subselects).
 */
public final class SqlToCoreNormalizer {

	private final SchemaIndex schema; // ya lo tienes en tu proyecto

	public SqlToCoreNormalizer(SchemaIndex schema) {
		this.schema = Objects.requireNonNull(schema, "schema");
	}

	/** Punto de entrada. */
	public CoreRel normalize(CoreRel root) {
		CoreRel r = normalizeRel(root);

		// Al terminar, no deberían quedar:
		// - CoreNaturalJoin
		// - CoreInSubselect/CoreExists/CoreNotExists dentro de predicados
		// - CoreBetween
		// - CoreProjAll/CoreProjAllFrom (si hay esquema)
		return r;
	}

	// -------------------- Relacionales --------------------

	private CoreRel normalizeRel(CoreRel n) {
		if (n instanceof CoreProject p) {
			// 1) normaliza el input
			CoreRel in = normalizeRel(p.input());
			// 2) (opcional) expande * / t.* usando schema
			List<CoreProjItem> items = expandProjectItems(p.items(), in);
			return new CoreProject(in, items);
		}
		if (n instanceof CoreSelect s) {
		    // 1) normaliza el input
		    CoreRel in = normalizeRel(s.input());

		    // 2) levanta IN/EXISTS a JOIN (puede mover tablas del WHERE al FROM)
		    LiftResult lifted = liftSubqueryPredicates(in, s.predicate());

		    // 3) normaliza el predicado residual; si es null, déjalo en null
		    CorePred resid = normalizePredOrNull(lifted.residual());

		    // 4) si no hay predicado efectivo, elimina el SELECT “vacío”
		    if (resid == null || isTrue(resid)) {
		        return lifted.input();
		    }
		    return new CoreSelect(lifted.input(), resid);
		}
		if (n instanceof CoreProduct x) {
			return new CoreProduct(normalizeRel(x.left()), normalizeRel(x.right()));
		}
		if (n instanceof CoreJoin j) {
			CoreRel L = normalizeRel(j.left());
			CoreRel R = normalizeRel(j.right());
			CorePred on = normalizePred(j.on());
			return new CoreJoin(L, R, on);
		}
		if (n instanceof CoreNaturalJoin nj) {
			// Reescritura NATURAL:
			// 1) normaliza lados
			CoreRel L = normalizeRel(nj.left());
			CoreRel R = normalizeRel(nj.right());

			return new CoreNaturalJoin(L, R);
		}
		if (n instanceof CoreUnion u) {
			return new CoreUnion(normalizeRel(u.left()), normalizeRel(u.right()));
		}
		if (n instanceof CoreIntersect i) {
			return new CoreIntersect(normalizeRel(i.left()), normalizeRel(i.right()));
		}
		if (n instanceof CoreExcept e) {
			return new CoreExcept(normalizeRel(e.left()), normalizeRel(e.right()));
		}
		if (n instanceof CoreAlias a) {
			return new CoreAlias(normalizeRel(a.input()), a.alias());
		}
		if (n instanceof CoreTable) {
			return n;
		}
		// Si llegase aquí un nodo no esperado:
		throw new IllegalArgumentException(
				"Nodo relacional no soportado en normalización: " + n.getClass().getSimpleName());
	}

	// -------------------- Predicados --------------------

	private CorePred normalizePred(CorePred p) {
		if (p == null)
			return new CoreCmp(new CoreNumber("1"), CmpOp.EQ, new CoreNumber("1")); // 1=1

		if (p instanceof CoreAnd a)
			return new CoreAnd(normalizePred(a.a()), normalizePred(a.b()));
		if (p instanceof CoreOr o)
			return new CoreOr(normalizePred(o.a()), normalizePred(o.b()));
		if (p instanceof CoreNot n)
			return new CoreNot(normalizePred(n.a()));

		if (p instanceof CoreCmp c)
			return c;

		if (p instanceof CoreBetween bt) {
			// (x BETWEEN lo AND hi) → (x >= lo) AND (x <= hi)
			CorePred ge = new CoreCmp(bt.value(), CmpOp.GTE, bt.low());
			CorePred le = new CoreCmp(bt.value(), CmpOp.LTE, bt.high());
			return new CoreAnd(ge, le);
		}

		if (p instanceof CoreInList in) {
			// Se puede dejar como InList (PredMapper ya lo soporta)
			return in;
		}

		// A estas alturas ya debimos haber levantado subconsultas
		// (liftSubqueryPredicates)
		if (p instanceof CoreInSubselect || p instanceof CoreExists || p instanceof CoreNotExists) {
			throw new IllegalStateException("Predicado con subconsulta no levantado a JOIN durante normalización.");
		}

		throw new IllegalArgumentException("Predicado no soportado: " + p.getClass().getSimpleName());
	}

	// -------------------- Levantar subconsultas (IN/EXISTS positivas)
	// --------------------

	/**
	 * Resultado de levantar subconsultas: input (posiblemente con JOINs nuevos) +
	 * predicado residual sin subselects.
	 */
	private record LiftResult(CoreRel input, CorePred residual) {
	}

	private LiftResult liftSubqueryPredicates(CoreRel input, CorePred p) {
		if (p == null)
			return new LiftResult(input, null);

		// Descompone por conectores y reescribe hojas
		if (p instanceof CoreAnd a) {
			LiftResult L = liftSubqueryPredicates(input, a.a());
			LiftResult R = liftSubqueryPredicates(L.input(), a.b());
			CorePred both = joinAnd(L.residual(), R.residual());
			return new LiftResult(R.input(), both);
		}
		if (p instanceof CoreOr o) {
			// Para OR con subconsultas: si alguno levanta JOINs, devolvemos OR del residual
			// y el input más “rico”.
			LiftResult L = liftSubqueryPredicates(input, o.a());
			LiftResult R = liftSubqueryPredicates(L.input(), o.b());
			CorePred res = joinOr(L.residual(), R.residual());
			return new LiftResult(R.input(), res);
		}
		if (p instanceof CoreNot n) {
			// NOT con subconsultas (NOT IN / NOT EXISTS) — NO SOPORTADO por ahora
			if (containsSubquery(n.a())) {
				throw new UnsupportedOperationException(
						"NOT IN / NOT EXISTS no soportados aún (requieren diferencia/antijoin).");
			}
			LiftResult inner = liftSubqueryPredicates(input, n.a());
			return new LiftResult(inner.input(), new CoreNot(inner.residual()));
		}

		// Hojas
		if (p instanceof CoreInSubselect in) {
			Derived d = materializeSubselect(in.sub());

			// 1) calificar lado izquierdo en el ÁMBITO ACTUAL (input)
			CoreExpr leftQ  = qualifyInScope(in.left(),  input);

			// 2) calificar expresión proyectada del subselect en el ÁMBITO DEL SUBSELECT
			CoreExpr rightQ = qualifyInScope(d.projectedExpr, d.fromTree);

			// 3) ON = (leftQ = rightQ) ∧ where(subselect)
			CorePred on = new CoreAnd(new CoreCmp(leftQ, CmpOp.EQ, rightQ), d.whereOrTrue);

			CoreRel joined = new CoreJoin(input, d.fromTree, on);
			return new LiftResult(joined, null);
		}

		if (p instanceof CoreExists ex) {
			// EXISTS (SELECT k FROM S WHERE P) → JOIN con S' sobre P (no necesita comparar
			// columnas)
			Derived d = materializeSubselect(ex.sub());
			CoreRel joined = new CoreJoin(input, d.fromTree, d.whereOrTrue);
			return new LiftResult(joined, null);
		}

		if (p instanceof CoreNotExists) {
			throw new UnsupportedOperationException("NOT EXISTS no soportado aún (requiere antijoin/diferencia).");
		}

		// Predicado sin subconsulta: lo dejamos como residual
		return new LiftResult(input, p);
	}

	private static boolean containsSubquery(CorePred p) {
		if (p == null)
			return false;
		if (p instanceof CoreInSubselect || p instanceof CoreExists || p instanceof CoreNotExists)
			return true;
		if (p instanceof CoreAnd a)
			return containsSubquery(a.a()) || containsSubquery(a.b());
		if (p instanceof CoreOr o)
			return containsSubquery(o.a()) || containsSubquery(o.b());
		if (p instanceof CoreNot n)
			return containsSubquery(n.a());
		return false;
	}

	private CorePred joinAnd(CorePred a, CorePred b) {
		if (a == null)
			return b;
		if (b == null)
			return a;
		return new CoreAnd(a, b);
	}

	private CorePred joinOr(CorePred a, CorePred b) {
		if (a == null)
			return b;
		if (b == null)
			return a;
		return new CoreOr(a, b);
	}

	// Relación derivada de un subselect (una sola columna en proyección)
	private record Derived(CoreRel fromTree, CoreExpr projectedExpr, CorePred whereOrTrue) {
	}

	private Derived materializeSubselect(CoreSubselect sub) {
		// Normaliza el FROM del subselect
		CoreRel from = normalizeRel(sub.fromTree());
		// WHERE interno (sin subconsultas, por gramática)
		CorePred where = normalizePred(sub.whereOrNull());

		// La proyección del subselect DEBE ser 1 columna
		CoreProjItem item = sub.oneProjected();
		CoreExpr projExpr;
		if (item instanceof CoreProjExpr pe && pe.expr() instanceof CoreColumnRef) {
			projExpr = pe.expr();
		} else if (item instanceof CoreProjExpr pe) {
			// Permitimos expr, pero tu pipeline probablemente exige columna
			projExpr = pe.expr();
		} else {
			throw new IllegalStateException("El subselect debe proyectar exactamente una columna/expr.");
		}

		return new Derived(from, projExpr,
				where == null ? new CoreCmp(new CoreNumber("1"), CmpOp.EQ, new CoreNumber("1")) : where);
	}

	// -------------------- Expansión de * / t.* --------------------

	private List<CoreProjItem> expandProjectItems(List<CoreProjItem> items, CoreRel inputTree) {
		if (items == null)
			return List.of(new CoreProjAll()); // fallback
		List<CoreProjItem> out = new ArrayList<>();
		for (CoreProjItem it : items) {
			if (it instanceof CoreProjAll) {
				// expandir *
				for (CoreColumnRef c : columnsQualifiedFromTree(inputTree)) {
					out.add(new CoreProjExpr(c, null));
				}
			} else if (it instanceof CoreProjAllFrom af) {
				for (CoreColumnRef c : columnsFromAliasOrTable(inputTree, af.relOrAlias())) {
					out.add(new CoreProjExpr(c, null));
				}
			} else {
				out.add(it);
			}
		}
		return out;
	}

	// ---------- Helpers de columnas basados en SchemaIndex ----------

	/** Normaliza nombres para lookups (lowercase, trim simple). */
	private static String norm(String s) {
		return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Conjunto de nombres de columna (lowercase, sin calificador) presentes en un
	 * subárbol.
	 */

	/**
	 * Lista de columnas totalmente calificadas (alias o tabla + nombre) para todo
	 * el árbol.
	 */
	private List<CoreColumnRef> columnsQualifiedFromTree(CoreRel tree) {
		List<CoreColumnRef> out = new ArrayList<>();
		if (tree instanceof CoreTable t) {
			String tname = norm(t.name());
			if (tname != null) {
				Set<String> cols = schema.columnsByTable().getOrDefault(tname, Set.of());
				for (String c : cols)
					out.add(new CoreColumnRef(t.name(), c)); // calificador = nombre tabla
			}
		} else if (tree instanceof CoreAlias a) {
			// Califica con el alias
			for (CoreColumnRef c : columnsQualifiedFromTree(a.input())) {
				out.add(new CoreColumnRef(a.alias(), c.name()));
			}
		} else if (tree instanceof CoreProject p) {
			// Si * ya fue expandido, podrías derivar de items; aquí tomamos el input para
			// mantener simple
			out.addAll(columnsQualifiedFromTree(p.input()));
		} else if (tree instanceof CoreSelect s) {
			out.addAll(columnsQualifiedFromTree(s.input()));
		} else if (tree instanceof CoreProduct pr) {
			out.addAll(columnsQualifiedFromTree(pr.left()));
			out.addAll(columnsQualifiedFromTree(pr.right()));
		} else if (tree instanceof CoreJoin j) {
			out.addAll(columnsQualifiedFromTree(j.left()));
			out.addAll(columnsQualifiedFromTree(j.right()));
		} else if (tree instanceof CoreUnion u) {
			out.addAll(columnsQualifiedFromTree(u.left()));
			out.addAll(columnsQualifiedFromTree(u.right()));
		} else if (tree instanceof CoreIntersect i) {
			out.addAll(columnsQualifiedFromTree(i.left()));
			out.addAll(columnsQualifiedFromTree(i.right()));
		} else if (tree instanceof CoreExcept e) {
			out.addAll(columnsQualifiedFromTree(e.left()));
			out.addAll(columnsQualifiedFromTree(e.right()));
		}
		return out;
	}

	/**
	 * Columnas calificadas solo de la relación cuyo alias o nombre coincide
	 * (case-insensitive).
	 */
	private List<CoreColumnRef> columnsFromAliasOrTable(CoreRel tree, String relOrAlias) {
		String target = norm(relOrAlias);
		if (target == null)
			return List.of();

		List<CoreColumnRef> out = new ArrayList<>();
		if (tree instanceof CoreTable t) {
			String tname = norm(t.name());
			if (target.equals(tname)) {
				Set<String> cols = schema.columnsByTable().getOrDefault(tname, Set.of());
				for (String c : cols)
					out.add(new CoreColumnRef(t.name(), c));
			}
		} else if (tree instanceof CoreAlias a) {
			if (target.equals(norm(a.alias()))) {
				for (CoreColumnRef c : columnsQualifiedFromTree(a.input())) {
					out.add(new CoreColumnRef(a.alias(), c.name()));
				}
			} else {
				out.addAll(columnsFromAliasOrTable(a.input(), relOrAlias));
			}
		} else if (tree instanceof CoreProject p) {
			out.addAll(columnsFromAliasOrTable(p.input(), relOrAlias));
		} else if (tree instanceof CoreSelect s) {
			out.addAll(columnsFromAliasOrTable(s.input(), relOrAlias));
		} else if (tree instanceof CoreProduct pr) {
			out.addAll(columnsFromAliasOrTable(pr.left(), relOrAlias));
			out.addAll(columnsFromAliasOrTable(pr.right(), relOrAlias));
		} else if (tree instanceof CoreJoin j) {
			out.addAll(columnsFromAliasOrTable(j.left(), relOrAlias));
			out.addAll(columnsFromAliasOrTable(j.right(), relOrAlias));
		} else if (tree instanceof CoreUnion u) {
			out.addAll(columnsFromAliasOrTable(u.left(), relOrAlias));
			out.addAll(columnsFromAliasOrTable(u.right(), relOrAlias));
		} else if (tree instanceof CoreIntersect i) {
			out.addAll(columnsFromAliasOrTable(i.left(), relOrAlias));
			out.addAll(columnsFromAliasOrTable(i.right(), relOrAlias));
		} else if (tree instanceof CoreExcept e) {
			out.addAll(columnsFromAliasOrTable(e.left(), relOrAlias));
			out.addAll(columnsFromAliasOrTable(e.right(), relOrAlias));
		}
		return out;
	}
	
	// Devuelve null si el predicado de entrada es null; en otro caso normaliza
	private CorePred normalizePredOrNull(CorePred p) {
	    if (p == null) return null;
	    return normalizePred(p);
	}

	// Detecta 1 = 1 como TRUE (tu normalizePred(null) devolvía eso)
	private boolean isTrue(CorePred p) {
	    if (p instanceof CoreCmp c
	        && c.op() == CmpOp.EQ
	        && c.left() instanceof CoreNumber ln
	        && c.right() instanceof CoreNumber rn) {
	        try {
	            // comparamos valores numéricos
	            return new java.math.BigDecimal(ln.lexeme()).compareTo(
	                   new java.math.BigDecimal(rn.lexeme())) == 0;
	        } catch (NumberFormatException ignore) {
	            // por si cambiaste el literal
	        }
	    }
	    return false;
	}
	
	private CoreExpr qualifyInScope(CoreExpr e, CoreRel scope) {
	    // Si no es columna o ya viene calificada, regresa tal cual
	    if (!(e instanceof CoreColumnRef c) || c.relOrNull() != null) return e;

	    String target = c.name();

	    // Busca candidatos con ese nombre dentro del árbol 'scope'
	    java.util.List<CoreColumnRef> candidates = columnsQualifiedFromTree(scope).stream()
	        .filter(cc -> cc.name().equalsIgnoreCase(target))
	        .toList();

	    if (candidates.isEmpty()) {
	        return e; // no encontrado aquí; deja que otra etapa lo maneje
	    }
	    if (candidates.size() > 1) {
	        String rels = candidates.stream()
	                .map(CoreColumnRef::relOrNull)
	                .filter(java.util.Objects::nonNull)
	                .distinct()
	                .sorted()
	                .collect(java.util.stream.Collectors.joining(", "));
	        throw new IllegalArgumentException("Columna ambigua: " + target + " en [" + rels + "]");
	    }

	    CoreColumnRef only = candidates.get(0);
	    // Califica con el alias/tabla detectado en el scope
	    return new CoreColumnRef(only.relOrNull(), c.name());
	}
}
