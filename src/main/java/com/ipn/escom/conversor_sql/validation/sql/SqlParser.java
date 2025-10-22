package com.ipn.escom.conversor_sql.validation.sql;

import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

public class SqlParser {
	/* ============================
    		   Parseo SQL
	============================ */
	public static Statement parseSql(String sqlQuery, ValidationResult validationResult) {
        try {
            return CCJSqlParserUtil.parse(sqlQuery);
        } catch (Exception parseException) {
            validationResult.agregarMensaje("error", TipoDetallado.ERROR_SINTAXIS,
                    "Error de sintaxis en la sentencia SQL. Detalles: " + parseException.getMessage());
            return null;
        }
    }
}
