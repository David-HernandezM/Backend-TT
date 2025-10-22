package com.ipn.escom.conversor_sql.conversion;

import java.util.List;

import com.ipn.escom.conversor_sql.core.CoreAnd;
import com.ipn.escom.conversor_sql.core.CoreAttr;
import com.ipn.escom.conversor_sql.core.CoreCross;
import com.ipn.escom.conversor_sql.core.CoreJoin;
import com.ipn.escom.conversor_sql.core.CoreNodeInterface;
import com.ipn.escom.conversor_sql.core.CorePredicateInterface;
import com.ipn.escom.conversor_sql.core.CoreProject;
import com.ipn.escom.conversor_sql.core.CoreSelect;
import com.ipn.escom.conversor_sql.core.CoreTable;
import com.ipn.escom.conversor_sql.validation.schema.SchemaIndex;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

public class SqlToCoreNormalizer {
	private final SchemaIndex schema;

	public SqlToCoreNormalizer(SchemaIndex schema) {
		this.schema = schema;
	}

	public CoreNodeInterface toCore(Statement stmt) {
	    if (!(stmt instanceof Select s))
	        throw new IllegalArgumentException("Sólo SELECT soportado.");
	    var body = s.getSelectBody();
	    if (!(body instanceof PlainSelect ps))
	        throw new IllegalArgumentException("Sólo SELECT simple en MVP.");

	    CoreNodeInterface from = mapFrom(ps.getFromItem());

	    if (ps.getJoins() != null) {
	        for (Join j : ps.getJoins()) {
	            var right = mapFrom(j.getRightItem());

	            if (j.isSimple()) {
	                // CROSS JOIN
	                from = new CoreCross(from, right);
	            } else {
	                // INNER JOIN ... ON (θ)   ->  θ = AND de todas las onExpressions
	                if (j.getOnExpressions() == null || j.getOnExpressions().isEmpty()) {
	                    throw new IllegalArgumentException("INNER JOIN requiere ON.");
	                }

	                CorePredicateInterface on = j.getOnExpressions().stream()
	                    .map(ExprMapper::toPredicate)            // Expression -> CorePredicateInterface
	                    .reduce((a, b) -> new CoreAnd(a, b))      // AND de todas
	                    .orElseThrow(() ->
	                        new IllegalArgumentException("INNER JOIN requiere ON."));

	                from = new CoreJoin(from, right, on);
	            }
	        }
	    }

	    CoreNodeInterface where = (ps.getWhere() != null)
	        ? new CoreSelect(from, ExprMapper.toPredicate(ps.getWhere()))
	        : from;

	    // Para el MVP, asume lista de columnas explícita (sin '*')
	    List<CoreAttr> attrs = SelectItemsMapper.expand(ps.getSelectItems(), where, schema);
	    return new CoreProject(where, attrs);
	}

	private CoreNodeInterface mapFrom(FromItem fi) {
	    if (fi instanceof Table t) {
	        // getName() ya retorna el nombre (String o TableName con .toString())
	        String name = t.getName().toString();

	        String alias = (t.getAlias() != null) ? t.getAlias().getName() : null;
	        return new CoreTable(name, alias);
	    }
	    throw new IllegalArgumentException("FROM no soportado: " + fi);
	}
}
