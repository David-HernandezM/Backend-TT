package com.ipn.escom.conversor_sql.core.expresiones;

public record CoreColumnRef(String relOrNull, String name) implements CoreExpr {} // [t.]col