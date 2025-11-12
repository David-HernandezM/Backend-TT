package com.ipn.escom.conversor_sql.ar;

import com.ipn.escom.conversor_sql.ar.expresiones.ArArith;
import com.ipn.escom.conversor_sql.ar.expresiones.ArCol;
import com.ipn.escom.conversor_sql.ar.expresiones.ArConst;
import com.ipn.escom.conversor_sql.ar.expresiones.ArExprInterface;
import com.ipn.escom.conversor_sql.ar.predicados.ArAnd;
import com.ipn.escom.conversor_sql.ar.predicados.ArCmp;
import com.ipn.escom.conversor_sql.ar.predicados.ArCmpOp;
import com.ipn.escom.conversor_sql.ar.predicados.ArNot;
import com.ipn.escom.conversor_sql.ar.predicados.ArOr;
import com.ipn.escom.conversor_sql.ar.predicados.ArPredInterface;
import com.ipn.escom.conversor_sql.ar.proyeccion.ArProjExpr;
import com.ipn.escom.conversor_sql.ar.proyeccion.ArProjItem;
import com.ipn.escom.conversor_sql.ar.relacionales.ArBase;
import com.ipn.escom.conversor_sql.ar.relacionales.ArExcept;
import com.ipn.escom.conversor_sql.ar.relacionales.ArIntersect;
import com.ipn.escom.conversor_sql.ar.relacionales.ArJoin;
import com.ipn.escom.conversor_sql.ar.relacionales.ArNaturalJoin;
import com.ipn.escom.conversor_sql.ar.relacionales.ArProduct;
import com.ipn.escom.conversor_sql.ar.relacionales.ArProject;
import com.ipn.escom.conversor_sql.ar.relacionales.ArRel;
import com.ipn.escom.conversor_sql.ar.relacionales.ArRename;
import com.ipn.escom.conversor_sql.ar.relacionales.ArSelect;
import com.ipn.escom.conversor_sql.ar.relacionales.ArUnion;

public final class ArPrinter {

	public String print(ArRel n) {
		return print(n, 0, 0); // parentPrec = 0 (raíz)
	}

	// --------- Precedencias ----------
	private static final int PREC_SET = 10; // ∪, ∩, −
	private static final int PREC_JOIN = 20; // ⋈, ×
	private static final int PREC_UNARY = 30; // σ, π, ρ y hojas

	private String print(ArRel n, int ind, int parentPrec) {
		// String I = " ".repeat(ind);

		if (n instanceof ArBase b) {
			return b.name();
		}
		if (n instanceof ArRename r) {
			String s = "ρ[" + r.alias() + "](" + print(r.input(), ind + 1, Integer.MIN_VALUE) + ")";
			return needPar(PREC_UNARY, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArProject p) {
			String s = "π[" + proj(p.items()) + "](" + print(p.input(), ind + 1, Integer.MIN_VALUE) + ")";
			return needPar(PREC_UNARY, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArSelect s) {
			String out = "σ[" + pred(s.predicate(), 0) + "](" + print(s.input(), ind + 1, Integer.MIN_VALUE) + ")";
			return needPar(PREC_UNARY, parentPrec) ? "(" + out + ")" : out;
		}
		if (n instanceof ArProduct x) {
			String s = printChild(x.left(), ind, PREC_JOIN, null) + " × " + printChild(x.right(), ind, PREC_JOIN, null);
			return needPar(PREC_JOIN, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArJoin j) {
			String s = printChild(j.left(), ind, PREC_JOIN, null) + " ⋈[" + pred(j.on(), 0) + "] "
					+ printChild(j.right(), ind, PREC_JOIN, null);
			return needPar(PREC_JOIN, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArNaturalJoin j) {
			String s = print(j.left(), ind, PREC_JOIN) + " ⋈ " + print(j.right(), ind, PREC_JOIN);
			return needPar(PREC_JOIN, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArUnion u) {
			String s = printChild(u.left(), ind, PREC_SET, "UNION") + " ∪ "
					+ printChild(u.right(), ind, PREC_SET, "UNION");
			return needPar(PREC_SET, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArIntersect i) {
			String s = printChild(i.left(), ind, PREC_SET, "INTERSECT") + " ∩ "
					+ printChild(i.right(), ind, PREC_SET, "INTERSECT");
			return needPar(PREC_SET, parentPrec) ? "(" + s + ")" : s;
		}
		if (n instanceof ArExcept e) {
			// Izquierdo: no fuerzar paréntesis; basta precedencia
			String left = print(e.left(), ind, PREC_SET);
			// Derecho: sí usa printChild para parentetizar si es necesario
			String right = printChild(e.right(), ind, PREC_SET, "EXCEPT");
			String s = left + " − " + right;
			return needPar(PREC_SET, parentPrec) ? "(" + s + ")" : s;
		}
		throw new IllegalArgumentException("AR no soportado: " + n.getClass().getSimpleName());
	}

	// ¿El hijo necesita paréntesis vs el padre?
	private boolean needPar(int childPrec, int parentPrec) {
		return childPrec < parentPrec;
	}

	// Imprime hijo con paréntesis mínimos, considerando tipo de set-op
	private String printChild(ArRel child, int ind, int parentPrec, String parentSetOp) {
		int childPrec = precedence(child);
		String s = print(child, ind, parentPrec);

		if (childPrec < parentPrec)
			return "(" + s + ")";

		// Si ambos son set-ops y el operador es distinto, pon paréntesis
		if (parentSetOp != null && isSetOp(child)) {
			String childOp = setOpName(child);
			if (!parentSetOp.equals(childOp))
				return "(" + s + ")";
		}
		return s;
	}

	private int precedence(ArRel n) {
		if (n instanceof ArUnion || n instanceof ArIntersect || n instanceof ArExcept)
			return PREC_SET;
		if (n instanceof ArJoin || n instanceof ArProduct || n instanceof ArNaturalJoin)
			return PREC_JOIN;
		return PREC_UNARY; // σ, π, ρ y hojas
	}

	private boolean isSetOp(ArRel n) {
		return (n instanceof ArUnion || n instanceof ArIntersect || n instanceof ArExcept);
	}

	private String setOpName(ArRel n) {
		if (n instanceof ArUnion)
			return "UNION";
		if (n instanceof ArIntersect)
			return "INTERSECT";
		if (n instanceof ArExcept)
			return "EXCEPT";
		return "";
	}

	// Predicados con precedencia: NOT(2) > AND(1) > OR(0)
	private String pred(ArPredInterface p, int prec) {
		if (p == null)
			return "TRUE";
		if (p instanceof ArAnd a)
			return wrap(pred(a.a(), 1) + " AND " + pred(a.b(), 1), prec > 1);
		if (p instanceof ArOr o)
			return wrap(pred(o.a(), 0) + " OR " + pred(o.b(), 0), prec > 0);
		if (p instanceof ArNot n)
			return "NOT " + wrap(pred(n.a(), 2), true);
		if (p instanceof ArCmp c) {
			boolean leftNull = isNullConst(c.l());
			boolean rightNull = isNullConst(c.r());

			// Solo un lado es NULL -> imprime con IS / IS NOT
			if (leftNull ^ rightNull) {
				ArExprInterface col = leftNull ? c.r() : c.l();
				String colStr = expr(col);
				// EQ con NULL => IS NULL ; NEQ con NULL => IS NOT NULL
				return colStr + (c.op() == ArCmpOp.NEQ ? " IS NOT NULL" : " IS NULL");
			}

			// Caso general
			return expr(c.l()) + " " + cmp(c.op()) + " " + expr(c.r());
		}
		return p.toString();
	}

	private boolean isNullConst(ArExprInterface e) {
		return (e instanceof ArConst k) && k.value() == null;
	}

	private String cmp(ArCmpOp op) {
		return switch (op) {
		case EQ -> "=";
		case NEQ -> "!=";
		case LT -> "<";
		case LTE -> "<=";
		case GT -> ">";
		case GTE -> ">=";
		};
	}

	private String proj(java.util.List<ArProjItem> items) {
		return items.stream().map(it -> {
			if (it instanceof ArProjExpr pe) {
				String e = expr(pe.expr());
				String a = pe.aliasOrNull();
				// no alias redundante; usa flecha para renombrar atributos
				return (a == null || a.isBlank() || a.equals(e)) ? e : (a + " \u2190 " + e);
			}
			return it.toString();
		}).collect(java.util.stream.Collectors.joining(", "));
	}

	private String expr(ArExprInterface e) {
		if (e instanceof ArCol c) {
			return c.name(); // <- SIN prefijo
		}
		if (e instanceof ArConst k) {
			Object v = k.value();
			if (v == null)
				return "NULL";
			if (v instanceof String s)
				return "'" + s.replace("'", "''") + "'";
			return String.valueOf(v); // números salen sin comillas
		}
		if (e instanceof ArArith a) {
			return arith(a);
		}
		return e.toString();
	}

	private String arith(ArArith a) {
		String op = switch (a.op()) {
		case ADD -> "+";
		case SUB -> "-";
		case MUL -> "*";
		case DIV -> "/";
		};
		String L = (a.left() instanceof ArArith) ? "(" + expr(a.left()) + ")" : expr(a.left());
		String R = (a.right() instanceof ArArith) ? "(" + expr(a.right()) + ")" : expr(a.right());
		return L + " " + op + " " + R;
	}

	private String wrap(String s, boolean paren) {
		return paren ? "(" + s + ")" : s;
	}
}
