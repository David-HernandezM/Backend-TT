package com.ipn.escom.conversor_sql.core.predicados;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.subselect.CoreSubselect;

public record CoreInSubselect(CoreExpr left, CoreSubselect sub) implements CorePred {}