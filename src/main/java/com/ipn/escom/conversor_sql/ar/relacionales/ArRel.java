package com.ipn.escom.conversor_sql.ar.relacionales;

public sealed interface ArRel
		permits ArBase, ArRename, ArProject, ArSelect, ArProduct, ArJoin, ArNaturalJoin, ArUnion, ArIntersect, ArExcept {}