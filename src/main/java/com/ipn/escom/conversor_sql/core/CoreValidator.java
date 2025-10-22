package com.ipn.escom.conversor_sql.core;

import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

public class CoreValidator {

	  private final SchemaIndex schema;

	  public CoreValidator(SchemaIndex schema) {
	    this.schema = schema;
	  }

	  /** MVP: devuelve un resultado válido sin checar nada aún. */
	  public ValidationResult validate(CoreNodeInterface node) {
	    // Por defecto: valido=true, mensajes=[]
	    return ValidationResult.builder().build();
	  }

	  /* ------------------------------------------------------------------
	     
	     public ValidationResult validate(CoreNodeInterface node) {
	       ValidationResult vr = ValidationResult.builder().build();
	       walk(node, vr);
	       return vr;
	     }

	     private void walk(CoreNodeInterface n, ValidationResult vr) {
	       if (n instanceof CoreTable t) {
	         // verificar que la tabla exista en 'schema'
	         // si no, vr.addErrorLogico("Tabla no existe: " + t.name());
	       } else if (n instanceof CoreProject p) {
	         // validar attrs existen en el scope, tipos, etc.
	         walk(p.input(), vr);
	       } else if (n instanceof CoreSelect s) {
	         // validar predicados/tipos
	         walk(s.input(), vr);
	       } else if (n instanceof CoreJoin j) {
	         // validar θ, tipos compatibles, colisiones de nombres
	         walk(j.left(), vr);
	         walk(j.right(), vr);
	       } else if (n instanceof CoreUnion u) {
	         // validar aridad y compatibilidad de tipos entre ramas
	         walk(u.left(), vr);
	         walk(u.right(), vr);
	       }
	       // … y así con los demás nodos
	     }
	   ------------------------------------------------------------------ */
	}