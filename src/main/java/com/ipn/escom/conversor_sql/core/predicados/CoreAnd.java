package com.ipn.escom.conversor_sql.core.predicados;

public record CoreAnd(CorePred a, CorePred b) implements CorePred {}