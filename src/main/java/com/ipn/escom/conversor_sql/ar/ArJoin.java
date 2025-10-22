package com.ipn.escom.conversor_sql.ar;

public record ArJoin(ArNodeInterface left, ArNodeInterface right, ArPredInterface on) implements ArNodeInterface {}