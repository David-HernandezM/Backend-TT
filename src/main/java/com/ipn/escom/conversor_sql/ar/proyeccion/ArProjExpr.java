package com.ipn.escom.conversor_sql.ar.proyeccion;

import com.ipn.escom.conversor_sql.ar.expresiones.ArExprInterface;

//Solo expresiones concretas; en AR ya no debe existir '*'
public record ArProjExpr(ArExprInterface expr, String aliasOrNull) implements ArProjItem {}