package com.ipn.escom.conversor_sql.core.predicados;

import java.util.List;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;

public record CoreInList(CoreExpr left, List<CoreExpr> rightList) implements CorePred {}