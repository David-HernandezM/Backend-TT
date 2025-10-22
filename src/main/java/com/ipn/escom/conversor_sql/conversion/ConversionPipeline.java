package com.ipn.escom.conversor_sql.conversion;

import com.ipn.escom.conversor_sql.ar.ArNodeInterface;
import com.ipn.escom.conversor_sql.ar.ArPrinter;
import com.ipn.escom.conversor_sql.core.CoreNodeInterface;
import com.ipn.escom.conversor_sql.core.CoreValidator;
import com.ipn.escom.conversor_sql.validation.ValidationResult;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

import net.sf.jsqlparser.statement.Statement;

public class ConversionPipeline {

  public Statement parse(String sql) throws Exception {
    return net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
  }

  public CoreNodeInterface normalizeToCore(Statement stmt, SchemaIndex schema) {
    return new SqlToCoreNormalizer(schema).toCore(stmt);
  }

  public ValidationResult validate(CoreNodeInterface core, SchemaIndex schema) {
    return new CoreValidator(schema).validate(core);
  }

  public ArNodeInterface toAlgebra(CoreNodeInterface core) {
    return new CoreToAr().convert(core);
  }

  public String render(ArNodeInterface ar) {
    return ArPrinter.print(ar);
  }
}