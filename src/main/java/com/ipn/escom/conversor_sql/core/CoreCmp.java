package com.ipn.escom.conversor_sql.core;

public record CoreCmp(CoreExprInterface left, CmpOp op, CoreExprInterface right) implements CorePredicateInterface {}