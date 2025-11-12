package com.ipn.escom.conversor_sql.core;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
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
import com.ipn.escom.conversor_sql.validation.ValidationResult;

public final class PredicateInvariants {

  private final TypeResolver types;

  public PredicateInvariants(TypeResolver types) { this.types = types; }

  public void check(CorePred p, ValidationResult vr) {
    if (p == null) return;
    if (p instanceof CoreAnd a) { check(a.a(), vr); check(a.b(), vr); }
    else if (p instanceof CoreOr o) { check(o.a(), vr); check(o.b(), vr); }
    else if (p instanceof CoreNot n) { check(n.a(), vr); }
    else if (p instanceof CoreCmp c) {
      TypeResolver.ValueType lt = types.typeOf(c.left());
      TypeResolver.ValueType rt = types.typeOf(c.right());
      if (!TypeResolver.comparable(lt, rt)) {
        vr.addErrorLogico("Comparación incompatible: " + lt + " " + c.op() + " " + rt);
      }
    }
    else if (p instanceof CoreBetween bt) {
      var vt = types.typeOf(bt.value());
      var lo = types.typeOf(bt.low());
      var hi = types.typeOf(bt.high());
      if (!(TypeResolver.comparable(vt, lo) && TypeResolver.comparable(vt, hi))) {
        vr.addErrorLogico("BETWEEN con tipos incompatibles: " + vt + " entre " + lo + " y " + hi);
      }
    }
    else if (p instanceof CoreInList in) {
      var base = types.typeOf(in.left());
      for (CoreExpr e : in.rightList()) {
        var t = types.typeOf(e);
        if (!TypeResolver.comparable(base, t)) {
          vr.addErrorLogico("IN lista con tipos incompatibles: " + base + " vs " + t);
          break;
        }
      }
    }
    else if (p instanceof CoreInSubselect) {
      // tipado profundo opcional
    }
    else if (p instanceof CoreExists || p instanceof CoreNotExists) {
      // sin chequeos extra
    }
  }
}
