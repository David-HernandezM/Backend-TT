package com.ipn.escom.conversor_sql.validation.sql;

/** Representa una fuente del FROM/JOIN con su alias si existe. */
public record Source(
		String tableReal, 
		String aliasLower, 
		String originalToken
) { }
