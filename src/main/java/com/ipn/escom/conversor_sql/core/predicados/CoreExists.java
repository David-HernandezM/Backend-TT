package com.ipn.escom.conversor_sql.core.predicados;

import com.ipn.escom.conversor_sql.core.subselect.CoreSubselect;

public record CoreExists(CoreSubselect sub) implements CorePred {}