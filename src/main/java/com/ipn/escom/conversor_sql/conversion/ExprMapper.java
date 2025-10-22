package com.ipn.escom.conversor_sql.conversion;

import com.ipn.escom.conversor_sql.core.CmpOp;
import com.ipn.escom.conversor_sql.core.CoreCmp;
import com.ipn.escom.conversor_sql.core.CoreConst;
import com.ipn.escom.conversor_sql.core.CorePredicateInterface;

public final class ExprMapper {
	private ExprMapper() {
	}

	public static CorePredicateInterface toPredicate(net.sf.jsqlparser.expression.Expression e) {
		// TODO: mapea realmente la expresión
		// Placeholder mínimo correcto de tipo:
		return new CoreCmp(new CoreConst(1), CmpOp.EQ, new CoreConst(1));
	}
}