package com.ipn.escom.conversor_sql.core;

public record CoreSemiJoin(CoreNodeInterface left, CoreNodeInterface right, CorePredicateInterface on) implements CoreNodeInterface {}