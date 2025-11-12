package com.ipn.escom.conversor_sql.core.predicados;

public record CoreOr (CorePred a, CorePred b) implements CorePred {}