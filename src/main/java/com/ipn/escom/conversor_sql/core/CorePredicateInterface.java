package com.ipn.escom.conversor_sql.core;

public sealed interface CorePredicateInterface permits CoreAnd, CoreOr, CoreNot, CoreCmp, CoreIn { }