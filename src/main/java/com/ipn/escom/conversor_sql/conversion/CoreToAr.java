package com.ipn.escom.conversor_sql.conversion;

import com.ipn.escom.conversor_sql.ar.ArPrinter;
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

public final class CoreToAr {

    public ArRel convert(CoreRel n) { return convert(n, null); }

    public ArRel convert(CoreRel n, Trace trace) {
    	// FROM (CoreTable)
    	if (n instanceof CoreTable t) {
    	    ArRel out = new ArBase(t.name());
    	    if (trace != null) trace.addWith("FROM " + t.name(), "(" + t.name() + ")", out);
    	    return out;
    	}

    	// CoreSelect (WHERE)
    	if (n instanceof CoreSelect s) {
    	    ArRel in  = convert(s.input(), trace);
    	    var  p    = PredMapper.map(s.predicate());
    	    ArRel out = new ArSelect(in, p);
    	    if (trace != null) {
    	        String predStr = CompactPredPrinter.print(p);
    	        trace.addWith("WHERE " + predStr, "σ[" + predStr + "]", out);
    	    }
    	    return out;
    	}

    	// CoreProject (SELECT)
    	if (n instanceof CoreProject p) {
    	    ArRel in   = convert(p.input(), trace);
    	    var  items = mapProjItems(p.items());
    	    ArRel out  = new ArProject(in, items);
    	    if (trace != null) {
    	        ProjTexts tx = buildProjectionTexts(items, in); // tu SQL calificado (izquierda)
    	        String piHeaderQ = qualifiedPiHeader(items);    // header π calificado (derecha)
    	        trace.addWith("SELECT " + tx.sql, piHeaderQ, out);
    	    }
    	    return out;
    	}

    	// CoreProduct (CROSS JOIN)
    	if (n instanceof CoreProduct x) {
    	    ArRel L = convert(x.left(), trace), R = convert(x.right(), trace);
    	    ArRel out = new ArProduct(L, R);
    	    if (trace != null) trace.addWith("CROSS JOIN", "×", out);
    	    return out;
    	}

    	// CoreJoin (JOIN ON ...)
    	if (n instanceof CoreJoin j) {
    	    ArRel L = convert(j.left(), trace), R = convert(j.right(), trace);
    	    var  on = PredMapper.mapForJoin(j.on());
    	    ArRel out = new ArJoin(L, R, on);
    	    if (trace != null) trace.addWith("JOIN ON " + CompactPredPrinter.print(on), "⋈[" + CompactPredPrinter.print(on) + "]", out);
    	    return out;
    	}

    	// CoreNaturalJoin
    	if (n instanceof CoreNaturalJoin nj) {
    	    ArRel L = convert(nj.left(), trace), R = convert(nj.right(), trace);
    	    ArRel out = new ArNaturalJoin(L, R);
    	    if (trace != null) trace.addWith("NATURAL JOIN", "⋈", out);
    	    return out;
    	}

    	// CoreUnion
    	if (n instanceof CoreUnion u) {
    	    ArRel L = convert(u.left(), trace), R = convert(u.right(), trace);
    	    ArRel out = new ArUnion(L, R);
    	    if (trace != null) trace.addWith("UNION", "∪", out);
    	    return out;
    	}

    	// CoreIntersect
    	if (n instanceof CoreIntersect i) {
    	    ArRel L = convert(i.left(), trace), R = convert(i.right(), trace);
    	    ArRel out = new ArIntersect(L, R);
    	    if (trace != null) trace.addWith("INTERSECT", "∩", out);
    	    return out;
    	}

    	// CoreExcept
    	if (n instanceof CoreExcept e) {
    	    ArRel L = convert(e.left(), trace), R = convert(e.right(), trace);
    	    ArRel out = new ArExcept(L, R);
    	    if (trace != null) trace.addWith("EXCEPT", "−", out);
    	    return out;
    	}

    	// CoreAlias (FROM ... AS alias combinado)
    	if (n instanceof CoreAlias a) {
    	    if (a.input() instanceof CoreTable t) {
    	        ArRel out = new ArRename(new ArBase(t.name()), a.alias());
    	        if (trace != null) trace.addWith("FROM " + t.name() + " AS " + a.alias(), "ρ[" + a.alias() + "](" + t.name() + ")", out);
    	        return out;
    	    }
    	    ArRel in  = convert(a.input(), trace);
    	    ArRel out = new ArRename(in, a.alias());
    	    if (trace != null) trace.addWith("RENAME " + a.alias(), "ρ[" + a.alias() + "]", out);
    	    return out;
    	}

        throw new IllegalArgumentException("Core no soportado: " + n.getClass().getSimpleName());
    }

    // ===== Helpers ya existentes =====

    private java.util.List<ArProjItem> mapProjItems(java.util.List<CoreProjItem> coreItems) {
        java.util.List<ArProjItem> out = new java.util.ArrayList<>(coreItems.size());
        for (CoreProjItem it : coreItems) {
            if (it instanceof CoreProjExpr pe) {
                out.add(mapProjExpr(pe));
            } else {
                throw new IllegalArgumentException("Item de proyección no soportado: " + it.getClass().getSimpleName());
            }
        }
        return out;
    }

    private ArProjItem mapProjExpr(CoreProjExpr pe) {
        ArExprInterface e = PredMapper.expr(pe.expr());
        String alias = pe.aliasOrNull();
        return (alias != null && !alias.isBlank()) ? new ArProjExpr(e, alias) : new ArProjExpr(e, null);
    }

    // ====== NUEVO: armado de textos SELECT (SQL vs AR) ======

    private static final class ProjTexts {
        final String sql;      // ej: "u.nombre, total"
        ProjTexts(String sql, String arHeader){ this.sql = sql;}
    }

    private ProjTexts buildProjectionTexts(java.util.List<ArProjItem> items, ArRel in) {
        java.util.List<String> sqlList = new java.util.ArrayList<>(items.size());
        java.util.List<String> arList  = new java.util.ArrayList<>(items.size());

        for (ArProjItem it : items) {
            if (!(it instanceof ArProjExpr pe)) {
                // fallback
                sqlList.add(it.toString());
                arList.add(it.toString());
                continue;
            }
            String sqlExpr = CompactExprPrinter.print(pe.expr()); // calificado si aplica
            String alias   = pe.aliasOrNull();

            // SQL (lado izquierdo del paso)
            if (alias != null && !alias.isBlank() && !alias.equals(sqlExpr)) {
                sqlList.add(sqlExpr + " AS " + alias);
            } else {
                sqlList.add(sqlExpr);
            }

            // AR (lado derecho del paso) — descalificar si tabla única y es columna simple
            String arExpr;
            if (alias != null && !alias.isBlank() && !alias.equals(sqlExpr)) {
                // alias ← expr(AR-compacto)
                arExpr = alias + " \u2190 " + printArExprForProj(pe.expr());
            } else {
                arExpr = printArExprForProj(pe.expr());
            }
            arList.add(arExpr);
        }

        String sql = String.join(", ", sqlList);
        String arHeader = "π[" + String.join(", ", arList) + "]";
        return new ProjTexts(sql, arHeader);
    }

    /** Imprime la expresión para la proyección en AR; si es ArCol y tabla única, omite prefijo. */
    private String printArExprForProj(ArExprInterface e) {
        if (e instanceof ArCol c) {
            return c.name(); // <- con prefijo SIEMPRE
        }
        return CompactExprPrinter.print(e);
    }

    // ===== Trace y mini printers compactos =====

    public static final class Trace {
        private final java.util.List<String> steps = new java.util.ArrayList<>();
        private final ArPrinter printer = new ArPrinter();

        public void add(String s) { steps.add(s); }

        // Formato: "<leftLabel> -> <header>: <currentAR>"
        public void addWith(String leftLabel, String header, ArRel current) {
            String currentAr = printer.print(current);
            if (current instanceof ArBase b) {        // <-- fuerza paréntesis sólo para hoja base
                currentAr = "(" + b.name() + ")";
            }
            steps.add(leftLabel + " -> " + header + ": " + currentAr);
        }

        public java.util.List<String> steps() { return java.util.Collections.unmodifiableList(steps); }
        public java.util.List<String> getSteps() { return steps(); }
    }

    /** Imprime SOLO la lista de proyección, sin hijo — p.ej. π[id_usuario, nombre]. */
    static final class CompactProjPrinter {
        static String header(java.util.List<ArProjItem> items) {
            String list = items.stream().map(it -> {
                if (it instanceof ArProjExpr pe) {
                    String e = CompactExprPrinter.print(pe.expr());
                    String a = pe.aliasOrNull();
                    return (a == null || a.isBlank() || a.equals(e)) ? e : (a + " \u2190 " + e);
                }
                return it.toString();
            }).collect(java.util.stream.Collectors.joining(", "));
            return "π[" + list + "]";
        }
    }

    /** Imprime SOLO el predicado, reusando una versión mini (no necesita ArPrinter completo). */
    static final class CompactPredPrinter {
        static String print(ArPredInterface p) {
            return MiniPred.print(p);
        }
    }

    static final class CompactExprPrinter {
        static String print(ArExprInterface e) {
            if (e instanceof ArCol c) {
                return (c.relOrNull() != null ? c.relOrNull() + "." : "") + c.name();
            }
            if (e instanceof ArConst k) {
                Object v = k.value();
                if (v == null) return "NULL";
                if (v instanceof String s) return "'" + s.replace("'", "''") + "'";
                return String.valueOf(v);
            }
            if (e instanceof ArArith a) {
                String op = switch (a.op()) { case ADD->"+"; case SUB->"-"; case MUL->"*"; case DIV->"/"; };
                String L = (a.left()  instanceof ArArith) ? "(" + print(a.left())  + ")" : print(a.left());
                String R = (a.right() instanceof ArArith) ? "(" + print(a.right()) + ")" : print(a.right());
                return L + " " + op + " " + R;
            }
            return e.toString();
        }
    }

    /** Mini pred printer (NOT > AND > OR) con soporte IS NULL/IS NOT NULL. */
    static final class MiniPred {
        static String print(ArPredInterface p) {
            return print(p, 0);
        }
        private static String print(ArPredInterface p, int prec) {
            if (p == null) return "TRUE";
            if (p instanceof ArAnd a) return wrap(print(a.a(),1) + " AND " + print(a.b(),1), prec>1);
            if (p instanceof ArOr  o) return wrap(print(o.a(),0) + " OR "  + print(o.b(),0), prec>0);
            if (p instanceof ArNot n) return "NOT " + wrap(print(n.a(),2), true);
            if (p instanceof ArCmp c) {
                boolean ln = isNull(c.l()), rn = isNull(c.r());
                if (ln ^ rn) {
                    ArExprInterface col = ln ? c.r() : c.l();
                    return CompactExprPrinter.print(col) + (c.op()==ArCmpOp.NEQ ? " IS NOT NULL" : " IS NULL");
                }
                return CompactExprPrinter.print(c.l()) + " " + cmp(c.op()) + " " + CompactExprPrinter.print(c.r());
            }
            return p.toString();
        }
        private static boolean isNull(ArExprInterface e){ return (e instanceof ArConst k) && k.value()==null; }
        private static String wrap(String s, boolean p){ return p ? "("+s+")" : s; }
        private static String cmp(ArCmpOp op){
            return switch (op) { case EQ->"="; case NEQ->"!="; case LT->"<"; case LTE->"<="; case GT->">"; case GTE->">="; };
        }
    }
    
 // π calificado para el HEADER del paso (siempre con prefijo rel.col cuando aplique)
    private String qualifiedPiHeader(java.util.List<ArProjItem> items) {
        java.util.List<String> cols = new java.util.ArrayList<>(items.size());
        for (ArProjItem it : items) {
            if (it instanceof ArProjExpr pe) {
                ArExprInterface e = pe.expr();
                String alias = pe.aliasOrNull();
                String q = qualifiedExpr(e);  // rel.col si ArCol
                if (alias != null && !alias.isBlank() && !alias.equals(q)) {
                    cols.add(alias + " \u2190 " + q);
                } else {
                    cols.add(q);
                }
            } else {
                cols.add(it.toString());
            }
        }
        return "π[" + String.join(", ", cols) + "]";
    }

    // Expr calificada (solo para headers de pasos; no cambia tu pretty printer final)
    private String qualifiedExpr(ArExprInterface e) {
        if (e instanceof ArCol c) {
            String rel = c.relOrNull();
            return (rel != null && !rel.isBlank() ? rel + "." : "") + c.name();
        }
        return CompactExprPrinter.print(e);
    }
}