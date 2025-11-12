package com.ipn.escom.conversor_sql.conversion;

import java.util.ArrayList;
import java.util.List;

import com.ipn.escom.conversor_sql.ar.expresiones.ArArith;
import com.ipn.escom.conversor_sql.ar.expresiones.ArArithOp;
import com.ipn.escom.conversor_sql.ar.expresiones.ArCol;
import com.ipn.escom.conversor_sql.ar.expresiones.ArConst;
import com.ipn.escom.conversor_sql.ar.expresiones.ArExprInterface;
import com.ipn.escom.conversor_sql.ar.predicados.ArAnd;
import com.ipn.escom.conversor_sql.ar.predicados.ArCmp;
import com.ipn.escom.conversor_sql.ar.predicados.ArCmpOp;
import com.ipn.escom.conversor_sql.ar.predicados.ArNot;
import com.ipn.escom.conversor_sql.ar.predicados.ArOr;
import com.ipn.escom.conversor_sql.ar.predicados.ArPredInterface;
import com.ipn.escom.conversor_sql.core.expresiones.ArithOp;
import com.ipn.escom.conversor_sql.core.expresiones.CoreBinArith;
import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNull;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNumber;
import com.ipn.escom.conversor_sql.core.expresiones.CoreParen;
import com.ipn.escom.conversor_sql.core.expresiones.CoreString;
import com.ipn.escom.conversor_sql.core.expresiones.CoreUnaryArith;
import com.ipn.escom.conversor_sql.core.predicados.CmpOp;
import com.ipn.escom.conversor_sql.core.predicados.CoreAnd;
import com.ipn.escom.conversor_sql.core.predicados.CoreBetween;
import com.ipn.escom.conversor_sql.core.predicados.CoreCmp;
import com.ipn.escom.conversor_sql.core.predicados.CoreInList;
import com.ipn.escom.conversor_sql.core.predicados.CoreNot;
import com.ipn.escom.conversor_sql.core.predicados.CoreOr;
import com.ipn.escom.conversor_sql.core.predicados.CorePred;

public final class PredMapper {

    private PredMapper() {}

    // ---------- Predicados públicos ----------

    public static ArPredInterface mapForJoin(CorePred p) {
        // preserva calificación y simplifica (quita AND TRUE, OR FALSE, etc.)
        return simplify(predPreserveQual(p));
    }

    public static ArPredInterface map(CorePred p) {
        ArPredInterface out;
        if (p == null) {
            out = makeTrue(); // TRUE
        } else if (p instanceof CoreAnd a) {
            out = new ArAnd(map(a.a()), map(a.b()));
        } else if (p instanceof CoreOr o) {
            out = new ArOr(map(o.a()), map(o.b()));
        } else if (p instanceof CoreNot n) {
            out = new ArNot(map(n.a()));
        } else if (p instanceof CoreCmp c) {
            out = new ArCmp(expr(c.left()), toOp(c.op()), expr(c.right()));
        } else if (p instanceof CoreBetween bt) {
            ArPredInterface ge = new ArCmp(expr(bt.value()), ArCmpOp.GTE, expr(bt.low()));
            ArPredInterface le = new ArCmp(expr(bt.value()), ArCmpOp.LTE, expr(bt.high()));
            out = new ArAnd(ge, le);
        } else if (p instanceof CoreInList in) {
            List<ArPredInterface> disj = new ArrayList<>();
            for (CoreExpr e : in.rightList())
                disj.add(new ArCmp(expr(in.left()), ArCmpOp.EQ, expr(e)));
            out = orChain(disj);
        } else {
            throw new IllegalStateException(
                "Predicado no soportado en AR (debe haberse normalizado): " + p.getClass().getSimpleName());
        }
        return simplify(out); // <<< simplifica aquí
    }

    // ---------- Expresiones ----------

    public static ArExprInterface expr(CoreExpr e) {
        if (e instanceof CoreColumnRef c)
            return new ArCol(c.relOrNull(), c.name()); // conserva alias
        if (e instanceof CoreNumber n)
            return new ArConst(new java.math.BigDecimal(n.lexeme()));
        if (e instanceof CoreString s)
            return new ArConst(s.value());
        if (e instanceof CoreNull)
            return new ArConst(null);
        if (e instanceof CoreParen p)
            return expr(p.inner());
        if (e instanceof CoreUnaryArith ua) {
            ArExprInterface inner = expr(ua.a());
            return ua.op() == ArithOp.SUB
                ? new ArArith(ArArithOp.SUB, new ArConst(java.math.BigDecimal.ZERO), inner)
                : new ArArith(ArArithOp.ADD, new ArConst(java.math.BigDecimal.ZERO), inner);
        }
        if (e instanceof CoreBinArith ba) {
            ArExprInterface L = expr(ba.left());
            ArExprInterface R = expr(ba.right());
            ArArithOp op = switch (ba.op()) {
                case ADD -> ArArithOp.ADD;
                case SUB -> ArArithOp.SUB;
                case MUL -> ArArithOp.MUL;
                case DIV -> ArArithOp.DIV;
            };
            return new ArArith(op, L, R);
        }
        throw new IllegalArgumentException("Expr Core no soportada: " + e.getClass().getSimpleName());
    }

    // ---------- Pred mapeado preservando calificación ----------

    private static ArPredInterface predPreserveQual(CorePred p) {
        ArPredInterface out;
        if (p == null) {
            out = makeTrue(); // TRUE
        } else if (p instanceof CoreAnd a) {
            out = new ArAnd(predPreserveQual(a.a()), predPreserveQual(a.b()));
        } else if (p instanceof CoreOr o) {
            out = new ArOr(predPreserveQual(o.a()), predPreserveQual(o.b()));
        } else if (p instanceof CoreNot n) {
            out = new ArNot(predPreserveQual(n.a()));
        } else if (p instanceof CoreCmp c) {
            out = new ArCmp(exprPreserveQual(c.left()), toOp(c.op()), exprPreserveQual(c.right()));
        } else if (p instanceof CoreBetween bt) {
            ArPredInterface ge = new ArCmp(exprPreserveQual(bt.value()), ArCmpOp.GTE, exprPreserveQual(bt.low()));
            ArPredInterface le = new ArCmp(exprPreserveQual(bt.value()), ArCmpOp.LTE, exprPreserveQual(bt.high()));
            out = new ArAnd(ge, le);
        } else if (p instanceof CoreInList in) {
            List<ArPredInterface> disj = new ArrayList<>();
            for (CoreExpr e : in.rightList())
                disj.add(new ArCmp(exprPreserveQual(in.left()), ArCmpOp.EQ, exprPreserveQual(e)));
            out = orChain(disj);
        } else {
            out = map(p); // fallback
        }
        return simplify(out); // <<< simplifica aquí
    }

    private static ArExprInterface exprPreserveQual(CoreExpr e) {
        if (e instanceof CoreColumnRef c)
            return new ArCol(c.relOrNull(), c.name());
        if (e instanceof CoreNumber n)
            return new ArConst(new java.math.BigDecimal(n.lexeme()));
        if (e instanceof CoreString s)
            return new ArConst(s.value());
        if (e instanceof CoreNull)
            return new ArConst(null);
        if (e instanceof CoreParen p)
            return exprPreserveQual(p.inner());
        if (e instanceof CoreUnaryArith ua) {
            ArExprInterface inner = exprPreserveQual(ua.a());
            return ua.op() == ArithOp.SUB
                ? new ArArith(ArArithOp.SUB, new ArConst(java.math.BigDecimal.ZERO), inner)
                : new ArArith(ArArithOp.ADD, new ArConst(java.math.BigDecimal.ZERO), inner);
        }
        if (e instanceof CoreBinArith ba) {
            ArExprInterface L = exprPreserveQual(ba.left());
            ArExprInterface R = exprPreserveQual(ba.right());
            ArArithOp op = switch (ba.op()) {
                case ADD -> ArArithOp.ADD;
                case SUB -> ArArithOp.SUB;
                case MUL -> ArArithOp.MUL;
                case DIV -> ArArithOp.DIV;
            };
            return new ArArith(op, L, R);
        }
        throw new IllegalArgumentException("Expr Core no soportada: " + e.getClass().getSimpleName());
    }

    // ---------- Utilidades internas ----------

    private static ArPredInterface orChain(List<ArPredInterface> xs) {
        if (xs.isEmpty()) return makeFalse();
        ArPredInterface acc = xs.get(0);
        for (int i = 1; i < xs.size(); i++) acc = new ArOr(acc, xs.get(i));
        return acc;
    }

    private static ArCmpOp toOp(CmpOp op) {
        return switch (op) {
            case EQ  -> ArCmpOp.EQ;
            case NEQ -> ArCmpOp.NEQ;
            case LT  -> ArCmpOp.LT;
            case LTE -> ArCmpOp.LTE;
            case GT  -> ArCmpOp.GT;
            case GTE -> ArCmpOp.GTE;
        };
    }

    // ====== Simplificador de predicados ======

    public static ArPredInterface simplify(ArPredInterface p) {
        if (p == null) return makeTrue();

        if (p instanceof ArAnd a) {
            var L = simplify(a.a());
            var R = simplify(a.b());
            if (isTrue(L))  return R;
            if (isTrue(R))  return L;
            if (isFalse(L) || isFalse(R)) return makeFalse();
            if (L == a.a() && R == a.b()) return p;
            return new ArAnd(L, R);
        }
        if (p instanceof ArOr o) {
            var L = simplify(o.a());
            var R = simplify(o.b());
            if (isTrue(L) || isTrue(R))  return makeTrue();
            if (isFalse(L)) return R;
            if (isFalse(R)) return L;
            if (L == o.a() && R == o.b()) return p;
            return new ArOr(L, R);
        }
        if (p instanceof ArNot n) {
            var A = simplify(n.a());
            if (isTrue(A))  return makeFalse();
            if (isFalse(A)) return makeTrue();
            if (A == n.a()) return p;
            return new ArNot(A);
        }
        if (p instanceof ArCmp c) {
            var L = c.l();
            var R = c.r();
            if (L instanceof ArConst lc && R instanceof ArConst rc) {
                Boolean val = evalConstCmp(lc.value(), c.op(), rc.value());
                if (val != null) return val ? makeTrue() : makeFalse();
            }
        }
        return p;
    }

    private static Boolean evalConstCmp(Object lv, ArCmpOp op, Object rv) {
        if (lv == null || rv == null) {
            if (lv == null && rv == null) return (op == ArCmpOp.EQ);
            return null; // (col = NULL) se maneja como IS en otro sitio
        }
        if (lv instanceof java.math.BigDecimal a && rv instanceof java.math.BigDecimal b) {
            int cmp = a.compareTo(b);
            return switch (op) {
                case EQ  -> cmp == 0;
                case NEQ -> cmp != 0;
                case LT  -> cmp < 0;
                case LTE -> cmp <= 0;
                case GT  -> cmp > 0;
                case GTE -> cmp >= 0;
            };
        }
        if (lv instanceof String ls && rv instanceof String rs) {
            int cmp = ls.compareTo(rs);
            return switch (op) {
                case EQ  -> cmp == 0;
                case NEQ -> cmp != 0;
                case LT  -> cmp < 0;
                case LTE -> cmp <= 0;
                case GT  -> cmp > 0;
                case GTE -> cmp >= 0;
            };
        }
        return null;
    }

    private static boolean isTrue(ArPredInterface p)  { return isConstCmp(p, true); }
    private static boolean isFalse(ArPredInterface p) { return isConstCmp(p, false); }

    private static boolean isConstCmp(ArPredInterface p, boolean wantTrue) {
        if (p instanceof ArCmp c) {
            if (c.l() instanceof ArConst lc && c.r() instanceof ArConst rc) {
                Object lv = lc.value(), rv = rc.value();
                if (lv instanceof java.math.BigDecimal a && rv instanceof java.math.BigDecimal b) {
                    int cmp = a.compareTo(b);
                    boolean val = switch (c.op()) {
                        case EQ  -> cmp == 0;
                        case NEQ -> cmp != 0;
                        case LT  -> cmp < 0;
                        case LTE -> cmp <= 0;
                        case GT  -> cmp > 0;
                        case GTE -> cmp >= 0;
                    };
                    return val == wantTrue;
                }
            }
        }
        return false;
    }

    private static ArPredInterface makeTrue()  {
        return new ArCmp(new ArConst(new java.math.BigDecimal(1)), ArCmpOp.EQ, new ArConst(new java.math.BigDecimal(1)));
    }

    private static ArPredInterface makeFalse() {
        return new ArCmp(new ArConst(new java.math.BigDecimal(1)), ArCmpOp.EQ, new ArConst(new java.math.BigDecimal(0)));
    }
}
