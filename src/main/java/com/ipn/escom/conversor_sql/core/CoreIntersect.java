package com.ipn.escom.conversor_sql.core;

public record CoreIntersect(CoreNodeInterface left, CoreNodeInterface right) implements CoreNodeInterface {}