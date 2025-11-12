package com.ipn.escom.conversor_sql.core.relacionales;

public record CoreNaturalJoin(CoreRel left, CoreRel right) implements CoreRel {}