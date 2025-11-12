package com.ipn.escom.conversor_sql.core.relacionales;

import com.ipn.escom.conversor_sql.core.predicados.CorePred;

public record CoreSelect(CoreRel input, CorePred predicate) implements CoreRel {}