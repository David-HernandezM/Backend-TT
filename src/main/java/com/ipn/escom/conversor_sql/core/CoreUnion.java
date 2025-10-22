package com.ipn.escom.conversor_sql.core;

public record CoreUnion(CoreNodeInterface left, CoreNodeInterface right) implements CoreNodeInterface {}