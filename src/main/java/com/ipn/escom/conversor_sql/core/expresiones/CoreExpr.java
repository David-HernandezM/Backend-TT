package com.ipn.escom.conversor_sql.core.expresiones;

public sealed interface CoreExpr
		permits CoreColumnRef, CoreNumber, CoreString, CoreNull, CoreParen, CoreUnaryArith, CoreBinArith {}