package com.ipn.escom.conversor_sql.ar.expresiones;

//Constante escalar (número, cadena, null)
public record ArConst(Object value) implements ArExprInterface {}