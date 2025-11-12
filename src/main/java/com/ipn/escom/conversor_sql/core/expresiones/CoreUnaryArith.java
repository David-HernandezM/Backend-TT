package com.ipn.escom.conversor_sql.core.expresiones;

public record CoreUnaryArith(ArithOp op, CoreExpr a) implements CoreExpr {}