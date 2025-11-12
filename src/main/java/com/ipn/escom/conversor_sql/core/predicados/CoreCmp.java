package com.ipn.escom.conversor_sql.core.predicados;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;

public record CoreCmp(CoreExpr left, CmpOp op, CoreExpr right) implements CorePred {}