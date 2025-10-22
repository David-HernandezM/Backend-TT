package com.ipn.escom.conversor_sql.ar;

public record ArUnion(ArNodeInterface left, ArNodeInterface right) implements ArNodeInterface {}