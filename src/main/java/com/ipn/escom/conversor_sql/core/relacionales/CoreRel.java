package com.ipn.escom.conversor_sql.core.relacionales;

public sealed interface CoreRel permits CoreTable, CoreAlias, CoreProject, CoreSelect, CoreProduct, CoreJoin, CoreNaturalJoin,
		CoreUnion, CoreIntersect, CoreExcept {}