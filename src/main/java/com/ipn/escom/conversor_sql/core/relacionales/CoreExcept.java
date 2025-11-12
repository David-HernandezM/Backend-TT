package com.ipn.escom.conversor_sql.core.relacionales;

public record CoreExcept(CoreRel left, CoreRel right) implements CoreRel {}