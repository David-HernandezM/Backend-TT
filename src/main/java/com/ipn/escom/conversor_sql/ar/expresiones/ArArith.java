package com.ipn.escom.conversor_sql.ar.expresiones;

public record ArArith(ArArithOp op, ArExprInterface left, ArExprInterface right) implements ArExprInterface {}