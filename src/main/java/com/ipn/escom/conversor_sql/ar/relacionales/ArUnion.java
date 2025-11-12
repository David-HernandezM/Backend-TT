package com.ipn.escom.conversor_sql.ar.relacionales;

public record ArUnion(ArRel left, ArRel right) implements ArRel {}