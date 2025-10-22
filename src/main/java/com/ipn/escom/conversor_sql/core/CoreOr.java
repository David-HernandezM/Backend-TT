package com.ipn.escom.conversor_sql.core;

public record CoreOr(CorePredicateInterface a, CorePredicateInterface b) implements CorePredicateInterface {}