package com.ipn.escom.conversor_sql.core.relacionales;

import com.ipn.escom.conversor_sql.core.predicados.CorePred;

public record CoreJoin(CoreRel left, CoreRel right, CorePred on) implements CoreRel {}