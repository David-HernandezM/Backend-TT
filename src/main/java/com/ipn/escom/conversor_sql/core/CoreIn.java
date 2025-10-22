package com.ipn.escom.conversor_sql.core;

public record CoreIn(CoreExprInterface left, CoreNodeInterface rightOneCol) implements CorePredicateInterface {}