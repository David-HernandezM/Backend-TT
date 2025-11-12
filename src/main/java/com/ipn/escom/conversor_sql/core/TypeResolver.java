package com.ipn.escom.conversor_sql.core;

import com.ipn.escom.conversor_sql.core.expresiones.CoreBinArith;
import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNull;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNumber;
import com.ipn.escom.conversor_sql.core.expresiones.CoreParen;
import com.ipn.escom.conversor_sql.core.expresiones.CoreString;
import com.ipn.escom.conversor_sql.core.expresiones.CoreUnaryArith;

public final class TypeResolver {

  public enum ValueType { NUMBER, STRING, NULL, UNKNOWN }

  public ValueType typeOf(CoreExpr e) {
    if (e == null) return ValueType.UNKNOWN;
    if (e instanceof CoreNumber) return ValueType.NUMBER;
    if (e instanceof CoreString) return ValueType.STRING;
    if (e instanceof CoreNull)   return ValueType.NULL;
    if (e instanceof CoreColumnRef) {
      return ValueType.UNKNOWN; // si luego quieres, mira metadata de SchemaIndex
    }
    if (e instanceof CoreParen p) return typeOf(p.inner());
    if (e instanceof CoreUnaryArith ua) {
      return (typeOf(ua.a()) == ValueType.NUMBER) ? ValueType.NUMBER : ValueType.UNKNOWN;
    }
    if (e instanceof CoreBinArith ba) {
      ValueType L = typeOf(ba.left());
      ValueType R = typeOf(ba.right());
      if (L == ValueType.NUMBER && R == ValueType.NUMBER) return ValueType.NUMBER;
      return ValueType.UNKNOWN;
    }
    return ValueType.UNKNOWN;
  }

  public static boolean comparable(ValueType a, ValueType b) {
    if (a == ValueType.NULL || b == ValueType.NULL) return true;
    if (a == b) return true;
    if (a == ValueType.NUMBER && b == ValueType.UNKNOWN) return true;
    if (b == ValueType.NUMBER && a == ValueType.UNKNOWN) return true;
    if (a == ValueType.STRING && b == ValueType.UNKNOWN) return true;
    if (b == ValueType.STRING && a == ValueType.UNKNOWN) return true;
    return false;
  }
}
