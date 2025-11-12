package com.ipn.escom.conversor_sql.core.expresiones;

public record CoreBinArith(ArithOp op, CoreExpr left, CoreExpr right) implements CoreExpr {}