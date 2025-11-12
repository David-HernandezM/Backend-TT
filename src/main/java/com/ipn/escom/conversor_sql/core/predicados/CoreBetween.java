package com.ipn.escom.conversor_sql.core.predicados;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;

public record CoreBetween(CoreExpr value, CoreExpr low, CoreExpr high) implements CorePred {}