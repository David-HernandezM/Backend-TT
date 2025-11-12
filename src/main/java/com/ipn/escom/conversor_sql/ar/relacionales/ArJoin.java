package com.ipn.escom.conversor_sql.ar.relacionales;

import com.ipn.escom.conversor_sql.ar.predicados.ArPredInterface;

//Join theta ⋈θ  (INNER JOIN / equi-join entra como predicado)
public record ArJoin(ArRel left, ArRel right, ArPredInterface on) implements ArRel {}