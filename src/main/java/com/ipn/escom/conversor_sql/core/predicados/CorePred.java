package com.ipn.escom.conversor_sql.core.predicados;

public sealed interface CorePred
		permits CoreAnd, CoreOr, CoreNot, CoreCmp, CoreBetween, CoreInList, CoreInSubselect, CoreExists, CoreNotExists {}