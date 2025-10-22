package com.ipn.escom.conversor_sql.core;

public record CoreAnd(CorePredicateInterface a, CorePredicateInterface b) implements CorePredicateInterface {}