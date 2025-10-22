package com.ipn.escom.conversor_sql.core;

public record CoreCross(CoreNodeInterface left, CoreNodeInterface right) implements CoreNodeInterface {}