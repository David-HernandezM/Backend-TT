package com.ipn.escom.conversor_sql.core;

public record CoreJoin(CoreNodeInterface left, CoreNodeInterface right, CorePredicateInterface on) implements CoreNodeInterface {}