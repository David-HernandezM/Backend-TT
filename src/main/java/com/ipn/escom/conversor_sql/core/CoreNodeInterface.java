package com.ipn.escom.conversor_sql.core;

public sealed interface CoreNodeInterface 
	permits CoreTable, CoreProject, CoreSelect, CoreJoin, CoreCross, CoreUnion, CoreIntersect, CoreMinus, CoreRename, CoreSemiJoin {}