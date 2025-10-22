package com.ipn.escom.conversor_sql.ar;

public record ArSemiJoin(ArNodeInterface left, ArNodeInterface right, ArPredInterface on) implements ArNodeInterface {}