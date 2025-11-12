package com.ipn.escom.conversor_sql.core.proyeccion;

import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;

public record CoreProjExpr(CoreExpr expr, String aliasOrNull) implements CoreProjItem {} // col o expr [AS alias]