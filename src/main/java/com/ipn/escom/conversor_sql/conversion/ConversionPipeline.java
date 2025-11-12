package com.ipn.escom.conversor_sql.conversion;

import com.ipn.escom.conversor_sql.ar.ArPrinter;
import com.ipn.escom.conversor_sql.ar.relacionales.ArRel;
import com.ipn.escom.conversor_sql.core.CoreInvariants;
import com.ipn.escom.conversor_sql.core.SchemaGuards;
import com.ipn.escom.conversor_sql.core.relacionales.CoreRel;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

public final class ConversionPipeline {

	public Statement parse(String sql) {
		try {
			return CCJSqlParserUtil.parse(sql);
		} catch (Exception ex) {
			throw new IllegalArgumentException("SQL inválido: " + ex.getMessage(), ex);
		}
	}

	public CoreRel normalizeToCore(Statement stmt, SchemaIndex schema) {
		CoreRel raw = new SqlToCoreBuilder().build(stmt);
		return new SqlToCoreNormalizer(schema).normalize(raw);
	}

	public ValidationResult validate(CoreRel core, SchemaIndex schema) {
		SchemaGuards guards = new SchemaGuards(schema);
		CoreInvariants ci = new CoreInvariants(guards);
		return ci.validate(core);
	}

	public ArRel toAlgebra(CoreRel core) {
		return new CoreToAr().convert(core);
	}

	public String render(ArRel ar) {
		return new ArPrinter().print(ar);
	}
}
