package com.ipn.escom.conversor_sql.ar.predicados;

import com.ipn.escom.conversor_sql.ar.expresiones.ArExprInterface;

public record ArCmp(ArExprInterface l, ArCmpOp op, ArExprInterface r) implements ArPredInterface {}