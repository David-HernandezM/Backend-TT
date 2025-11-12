package com.ipn.escom.conversor_sql.ar.relacionales;

import com.ipn.escom.conversor_sql.ar.predicados.ArPredInterface;

//Selección σ
public record ArSelect(ArRel input, ArPredInterface predicate) implements ArRel {}