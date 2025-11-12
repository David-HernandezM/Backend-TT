package com.ipn.escom.conversor_sql.core;

import java.util.List;
import java.util.Objects;

import com.ipn.escom.conversor_sql.core.expresiones.CoreBinArith;
import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNull;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNumber;
import com.ipn.escom.conversor_sql.core.expresiones.CoreParen;
import com.ipn.escom.conversor_sql.core.expresiones.CoreString;
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
import com.ipn.escom.conversor_sql.validation.ValidationResult;

public final class NodeInvariants {

  private NodeInvariants() {}

  public static void checkRelTree(CoreRel r, ValidationResult vr) {
    Objects.requireNonNull(r);
    if (r instanceof CoreProject p) {
      requireNonEmpty(p.items(), "La proyección no puede estar vacía", vr);
      checkRelTree(p.input(), vr);
      for (CoreProjItem it : p.items()) checkProj(it, vr);
    } else if (r instanceof CoreSelect s) {
      checkRelTree(s.input(), vr);
      checkPred(s.predicate(), vr);
    } else if (r instanceof CoreProduct x) {
      checkRelTree(x.left(), vr);
      checkRelTree(x.right(), vr);
    } else if (r instanceof CoreJoin j) {
      checkRelTree(j.left(), vr);
      checkRelTree(j.right(), vr);
      if (containsSubquery(j.on())) {
        vr.addErrorLogico("Subconsultas solo se permiten en WHERE externo, no en ON.");
      }
      checkPred(j.on(), vr);
    } else if (r instanceof CoreNaturalJoin nj) {
      checkRelTree(nj.left(), vr);
      checkRelTree(nj.right(), vr);
    } else if (r instanceof CoreUnion u) {
      checkRelTree(u.left(), vr);
      checkRelTree(u.right(), vr);
    } else if (r instanceof CoreIntersect i) {
      checkRelTree(i.left(), vr);
      checkRelTree(i.right(), vr);
    } else if (r instanceof CoreExcept e) {
      checkRelTree(e.left(), vr);
      checkRelTree(e.right(), vr);
    } else if (r instanceof CoreAlias a) {
      if (a.alias() == null || a.alias().isBlank()) {
        vr.addErrorSintaxis("Alias vacío en CoreAlias.");
      }
      checkRelTree(a.input(), vr);
    } else if (r instanceof CoreTable t) {
      if (t.name() == null || t.name().isBlank()) {
        vr.addErrorSintaxis("Nombre de tabla vacío.");
      }
    } else {
      vr.addErrorLogico("Nodo relacional no soportado: " + r.getClass().getSimpleName());
    }
  }

  public static void checkPred(CorePred p, ValidationResult vr) {
    if (p == null) {
      vr.addAdvertencia("Predicado nulo (se interpretará como TRUE).");
      return;
    }
    if (p instanceof CoreAnd a) {
      checkPred(a.a(), vr); checkPred(a.b(), vr);
    } else if (p instanceof CoreOr o) {
      checkPred(o.a(), vr); checkPred(o.b(), vr);
    } else if (p instanceof CoreNot n) {
      checkPred(n.a(), vr);
    } else if (p instanceof CoreCmp c) {
      checkExpr(c.left(), vr);
      checkExpr(c.right(), vr);
    } else if (p instanceof CoreBetween bt) {
      checkExpr(bt.value(), vr);
      checkExpr(bt.low(), vr);
      checkExpr(bt.high(), vr);
    } else if (p instanceof CoreInList in) {
      if (in.rightList() == null || in.rightList().isEmpty()) {
        vr.addErrorLogico("IN (...) no puede estar vacío.");
      }
      checkExpr(in.left(), vr);
      for (CoreExpr e : in.rightList()) checkExpr(e, vr);
    } else if (p instanceof CoreInSubselect inSub) {
      checkSubselect(inSub.sub(), vr);
      checkExpr(inSub.left(), vr);
    } else if (p instanceof CoreExists ex) {
      checkSubselect(ex.sub(), vr);
    } else if (p instanceof CoreNotExists nex) {
      checkSubselect(nex.sub(), vr);
      vr.addAdvertencia("NOT EXISTS será rechazado en normalización (anti-join no implementado).");
    } else {
      vr.addErrorLogico("Predicado no soportado: " + p.getClass().getSimpleName());
    }
  }

  public static void checkExpr(CoreExpr e, ValidationResult vr) {
    if (e == null) { vr.addErrorSintaxis("Expresión nula."); return; }
    if (e instanceof CoreColumnRef c) {
      if (c.name() == null || c.name().isBlank()) {
        vr.addErrorSintaxis("Referencia a columna sin nombre.");
      }
    } else if (e instanceof CoreNumber n) {
      if (n.lexeme() == null || n.lexeme().isBlank()) {
        vr.addErrorSintaxis("Número vacío.");
      }
    } else if (e instanceof CoreString) {
      // ok
    } else if (e instanceof CoreNull) {
      // ok
    } else if (e instanceof CoreParen p) {
      checkExpr(p.inner(), vr);
    } else if (e instanceof CoreUnaryArith ua) {
      checkExpr(ua.a(), vr);
    } else if (e instanceof CoreBinArith ba) {
      checkExpr(ba.left(), vr);
      checkExpr(ba.right(), vr);
    } else {
      vr.addErrorLogico("Expresión no soportada: " + e.getClass().getSimpleName());
    }
  }

  private static boolean containsSubquery(CorePred p) {
    if (p == null) return false;
    if (p instanceof CoreInSubselect || p instanceof CoreExists || p instanceof CoreNotExists) return true;
    if (p instanceof CoreAnd a) return containsSubquery(a.a()) || containsSubquery(a.b());
    if (p instanceof CoreOr  o) return containsSubquery(o.a()) || containsSubquery(o.b());
    if (p instanceof CoreNot n) return containsSubquery(n.a());
    return false;
  }

  private static void checkSubselect(CoreSubselect s, ValidationResult vr) {
    if (s == null) { vr.addErrorSintaxis("Subselect nulo."); return; }
    if (!(s.oneProjected() instanceof CoreProjExpr)) {
      vr.addErrorLogico("El subselect debe proyectar exactamente una columna/expresión.");
    }
    checkRelTree(s.fromTree(), vr);
    if (s.whereOrNull() != null && containsSubquery(s.whereOrNull())) {
      vr.addErrorLogico("El WHERE interno del subselect no puede contener subconsultas.");
    }
  }

  private static <T> void requireNonEmpty(List<T> xs, String msg, ValidationResult vr) {
    if (xs == null || xs.isEmpty()) vr.addErrorLogico(msg);
  }

  private static void checkProj(CoreProjItem it, ValidationResult vr) {
    if (it instanceof CoreProjAll) return;
    if (it instanceof CoreProjAllFrom p) {
      if (p.relOrAlias() == null || p.relOrAlias().isBlank()) {
        vr.addErrorSintaxis("t.* con calificador vacío.");
      }
      return;
    }
    if (it instanceof CoreProjExpr pe) {
      checkExpr(pe.expr(), vr);
      return;
    }
    vr.addErrorLogico("Item de proyección no soportado: " + it.getClass().getSimpleName());
  }
}
