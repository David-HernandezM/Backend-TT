package com.ipn.escom.conversor_sql.core;

public record CoreSelect(CoreNodeInterface input, CorePredicateInterface predicate) implements CoreNodeInterface {}