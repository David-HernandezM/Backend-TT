package com.ipn.escom.conversor_sql.conversion;

import com.ipn.escom.conversor_sql.ar.ArCmp;
import com.ipn.escom.conversor_sql.ar.ArCmpOp;
import com.ipn.escom.conversor_sql.ar.ArConst;
import com.ipn.escom.conversor_sql.ar.ArPredInterface;

public final class PredMapper {
	private PredMapper() {}

	  public static ArPredInterface fake() {
	    // Devuelve cualquier predicado que implemente la interfaz
	    return new ArCmp(new ArConst(1), ArCmpOp.EQ, new ArConst(1));
	  }
}