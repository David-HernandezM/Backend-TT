package com.ipn.escom.conversor_sql.core.relacionales;

public record CoreProduct(CoreRel left, CoreRel right) implements CoreRel {}