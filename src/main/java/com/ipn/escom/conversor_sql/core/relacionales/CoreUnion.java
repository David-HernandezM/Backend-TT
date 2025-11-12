package com.ipn.escom.conversor_sql.core.relacionales;

public record CoreUnion(CoreRel left, CoreRel right) implements CoreRel {}