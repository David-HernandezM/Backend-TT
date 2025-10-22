package com.ipn.escom.conversor_sql.ar;

import java.util.stream.Collectors;

public final class ArPrinter {
  private ArPrinter() {}

  public static String print(ArNodeInterface n) {
    return switch (n) {
      case ArTable t -> t.name();
      case ArRename r -> "ρ_" + r.newName() + "(" + print(r.input()) + ")";
      case ArProject p -> "π_{" + p.attrs().stream()
          .map(a -> (a.rel() != null ? a.rel() + "." : "") + a.name())
          .collect(Collectors.joining(",")) + "}(" + print(p.input()) + ")";
      case ArSelect s -> "σ_(" + pred(s.pred()) + ")(" + print(s.input()) + ")";
      case ArJoin j -> "(" + print(j.left()) + ") ⋈_(" + pred(j.on()) + ") (" + print(j.right()) + ")";
      case ArCross c -> "(" + print(c.left()) + ") × (" + print(c.right()) + ")";
      case ArUnion u -> "(" + print(u.left()) + ") ∪ (" + print(u.right()) + ")";
      case ArIntersect i -> "(" + print(i.left()) + ") ∩ (" + print(i.right()) + ")";
      case ArMinus m -> "(" + print(m.left()) + ") − (" + print(m.right()) + ")";
      case ArSemiJoin sj -> "(" + print(sj.left()) + ") ⋉_(" + pred(sj.on()) + ") (" + print(sj.right()) + ")";
    };
  }

  private static String pred(ArPredInterface p) {
    return switch (p) {
      case ArAnd a -> "(" + pred(a.a()) + " ∧ " + pred(a.b()) + ")";
      case ArOr o -> "(" + pred(o.a()) + " ∨ " + pred(o.b()) + ")";
      case ArNot n -> "¬" + pred(n.a());
      case ArCmp c -> expr(c.l()) + " " + c.op() + " " + expr(c.r());
    };
  }

  private static String expr(ArExprInterface e) {
	  return switch (e) {
	    case ArCol c -> (c.rel() != null ? c.rel() + "." : "") + c.name();
	    case ArConst k -> {
	      Object v = k.value();                 // <- toma el valor interno
	      if (v == null) yield "NULL";
	      if (v instanceof String s)            // <- ahora sí, compara el valor
	        yield "'" + s.replace("'", "''") + "'"; // escapa comillas
	      yield String.valueOf(v);
	    }
	  };
	}
}