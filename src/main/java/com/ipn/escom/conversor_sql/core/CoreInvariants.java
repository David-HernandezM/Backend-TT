package com.ipn.escom.conversor_sql.core;

import com.ipn.escom.conversor_sql.core.predicados.CorePred;
import com.ipn.escom.conversor_sql.core.relacionales.CoreExcept;
import com.ipn.escom.conversor_sql.core.relacionales.CoreIntersect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreNaturalJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProduct;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProject;
import com.ipn.escom.conversor_sql.core.relacionales.CoreRel;
import com.ipn.escom.conversor_sql.core.relacionales.CoreSelect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreUnion;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

public final class CoreInvariants {

  private final SchemaGuards guards;

  public CoreInvariants(SchemaGuards guards) {
    this.guards = guards;
  }

  public ValidationResult validate(CoreRel root) {
    ValidationResult vr = ValidationResult.builder().build();

    NodeInvariants.checkRelTree(root, vr);

    guards.checkTablesExist(root, vr);
    guards.checkColumnsResolvable(root, vr);

    walkPredicates(root, p -> new PredicateInvariants(new TypeResolver()).check(p, vr));

    return vr;
  }

  private interface PredVisitor { void accept(CorePred p); }

  private void walkPredicates(CoreRel r, PredVisitor v) {
    if (r instanceof CoreProject p) {
      walkPredicates(p.input(), v);
    } else if (r instanceof CoreSelect s) {
      v.accept(s.predicate());
      walkPredicates(s.input(), v);
    } else if (r instanceof CoreProduct x) {
      walkPredicates(x.left(), v); walkPredicates(x.right(), v);
    } else if (r instanceof CoreJoin j) {
      v.accept(j.on());
      walkPredicates(j.left(), v); walkPredicates(j.right(), v);
    } else if (r instanceof CoreNaturalJoin nj) {
      walkPredicates(nj.left(), v); walkPredicates(nj.right(), v);
    } else if (r instanceof CoreUnion u) {
      walkPredicates(u.left(), v); walkPredicates(u.right(), v);
    } else if (r instanceof CoreIntersect i) {
      walkPredicates(i.left(), v); walkPredicates(i.right(), v);
    } else if (r instanceof CoreExcept e) {
      walkPredicates(e.left(), v); walkPredicates(e.right(), v);
    }
  }
}
