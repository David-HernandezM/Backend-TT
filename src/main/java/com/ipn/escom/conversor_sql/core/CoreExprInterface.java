package com.ipn.escom.conversor_sql.core;

sealed interface CoreExprInterface permits CoreCol, CoreConst, CoreParen { }