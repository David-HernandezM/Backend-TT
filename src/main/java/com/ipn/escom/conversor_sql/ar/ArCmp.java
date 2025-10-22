package com.ipn.escom.conversor_sql.ar;

public record ArCmp(ArExprInterface l, ArCmpOp op, ArExprInterface r) implements ArPredInterface {}