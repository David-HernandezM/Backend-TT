package com.ipn.escom.conversor_sql.ar.relacionales;

//Producto cartesiano ×
public record ArProduct(ArRel left, ArRel right) implements ArRel {}