package com.ipn.escom.conversor_sql.conversion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.ipn.escom.conversor_sql.core.expresiones.ArithOp;
import com.ipn.escom.conversor_sql.core.expresiones.CoreBinArith;
import com.ipn.escom.conversor_sql.core.expresiones.CoreColumnRef;
import com.ipn.escom.conversor_sql.core.expresiones.CoreExpr;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNull;
import com.ipn.escom.conversor_sql.core.expresiones.CoreNumber;
import com.ipn.escom.conversor_sql.core.expresiones.CoreParen;
import com.ipn.escom.conversor_sql.core.expresiones.CoreString;
import com.ipn.escom.conversor_sql.core.expresiones.CoreUnaryArith;
import com.ipn.escom.conversor_sql.core.predicados.CmpOp;
import com.ipn.escom.conversor_sql.core.predicados.CoreAnd;
import com.ipn.escom.conversor_sql.core.predicados.CoreBetween;
import com.ipn.escom.conversor_sql.core.predicados.CoreCmp;
import com.ipn.escom.conversor_sql.core.predicados.CoreExists;
import com.ipn.escom.conversor_sql.core.predicados.CoreInList;
import com.ipn.escom.conversor_sql.core.predicados.CoreInSubselect;
import com.ipn.escom.conversor_sql.core.predicados.CoreNot;
import com.ipn.escom.conversor_sql.core.predicados.CoreNotExists;
import com.ipn.escom.conversor_sql.core.predicados.CoreOr;
import com.ipn.escom.conversor_sql.core.predicados.CorePred;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjAll;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjAllFrom;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjExpr;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjItem;
import com.ipn.escom.conversor_sql.core.relacionales.CoreAlias;
import com.ipn.escom.conversor_sql.core.relacionales.CoreExcept;
import com.ipn.escom.conversor_sql.core.relacionales.CoreIntersect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreNaturalJoin;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProduct;
import com.ipn.escom.conversor_sql.core.relacionales.CoreProject;
import com.ipn.escom.conversor_sql.core.relacionales.CoreRel;
import com.ipn.escom.conversor_sql.core.relacionales.CoreSelect;
import com.ipn.escom.conversor_sql.core.relacionales.CoreTable;
import com.ipn.escom.conversor_sql.core.relacionales.CoreUnion;
import com.ipn.escom.conversor_sql.core.subselect.CoreSubselect;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ExceptOp;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.IntersectOp;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.MinusOp;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperation;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.UnionOp;

/** Construye el AST de SQL a partir de un Statement de JSQLParser. */
public final class SqlToCoreBuilder {
	// ---------- Entry ----------

	public CoreRel build(Statement stmt) {
		if (!(stmt instanceof net.sf.jsqlparser.statement.select.Select sel)) {
			throw new UnsupportedOperationException("Solo SELECT es soportado.");
		}
		return buildSelect(sel);
	}

	// ---------- SELECT / Set ops ----------

	private CoreRel buildSelect(Select sel) {
		SelectBody body = sel.getSelectBody();
		if (body instanceof PlainSelect ps) {
			return buildPlainSelect(ps);
		}
		if (body instanceof SetOperationList sol) {
			List<SelectBody> selects = sol.getSelects();
			List<SetOperation> ops = sol.getOperations();
			if (selects == null || selects.isEmpty()) {
				throw new IllegalArgumentException("SetOperation sin selects.");
			}
			if (ops == null || selects.size() != ops.size() + 1) {
				throw new IllegalArgumentException("SetOperation inconsistente: selects=" + selects.size() + " ops="
						+ (ops == null ? -1 : ops.size()));
			}

			// 1) Construir hojas
			List<CoreRel> leaves = new ArrayList<>(selects.size());
			for (SelectBody sb : selects) {
				leaves.add(buildSelectBody(sb));
			}

			// 2) Colapsar INTERSECT contiguos
			List<CoreRel> stage = new ArrayList<>();
			List<SetOperation> stageOps = new ArrayList<>(); // quedarán sólo UNION/EXCEPT
			int i = 0;
			while (i < leaves.size()) {
				CoreRel acc2 = leaves.get(i++);
				while ((i - 1) < ops.size() && (ops.get(i - 1) instanceof IntersectOp)) {
					CoreRel right2 = leaves.get(i++);
					acc2 = new CoreIntersect(acc2, right2);
				}
				stage.add(acc2);
				if ((i - 1) < ops.size() && !(ops.get(i - 1) instanceof IntersectOp)) {
					stageOps.add(ops.get(i - 1));
				}
			}

			// 3) Plegar UNION / EXCEPT en orden (left-assoc)
			CoreRel result = stage.get(0);
			for (int k = 0; k < stageOps.size(); k++) {
				SetOperation op = stageOps.get(k);
				CoreRel right = stage.get(k + 1);
				if (op instanceof UnionOp) {
					result = new CoreUnion(result, right);
				} else if (op instanceof ExceptOp || op instanceof MinusOp) {
					result = new CoreExcept(result, right);
				} else {
					throw new UnsupportedOperationException(
							"Operación de conjunto no soportada: " + op.getClass().getSimpleName());
				}
			}
			return result;
		}
		throw new UnsupportedOperationException("SelectBody no soportado: " + body.getClass().getSimpleName());
	}

	private CoreRel buildSelectBody(SelectBody body) {
		if (body instanceof PlainSelect ps)
			return buildPlainSelect(ps);
		throw new UnsupportedOperationException("SelectBody no soportado: " + body.getClass().getSimpleName());
	}

	private CoreRel buildPlainSelect(PlainSelect ps) {
		// FROM
		CoreRel fromTree = buildFrom(ps.getFromItem(), ps.getJoins());

		// WHERE (puede traer subconsultas 1 nivel)
		CorePred where = mapWhere(ps.getWhere());

		// SELECT list
		List<CoreProjItem> items = mapSelectItems(ps.getSelectItems());

		CoreRel base = (where != null) ? new CoreSelect(fromTree, where) : fromTree;
		return new CoreProject(base, items);
	}

	// ---------- FROM / JOINs ----------

	private CoreRel buildFrom(FromItem fromItem, List<Join> joins) {
		CoreRel base = mapFromItem(fromItem);
		if (joins != null) {
			for (Join j : joins) {
				if (j.isSimple()) {
					// coma implícita no llega aquí; JSQLParser usa Join objects. “Simple” suele ser
					// CROSS-like
					base = new CoreProduct(base, mapFromItem(j.getRightItem()));
					continue;
				}
				if (Boolean.TRUE.equals(j.isCross())) {
					base = new CoreProduct(base, mapFromItem(j.getRightItem()));

				} else if (Boolean.TRUE.equals(j.isNatural())) {
					base = new CoreNaturalJoin(base, mapFromItem(j.getRightItem()));

				} else {
					// INNER (theta). Ahora ON puede venir como lista.
					CoreRel right = mapFromItem(j.getRightItem());

					// Preferir la API nueva:
					Collection<Expression> onList = j.getOnExpressions();
					CorePred pred = null;
					if (onList != null && !onList.isEmpty()) {
						for (Expression e : onList) {
							CorePred pe = mapBool(e);
							pred = (pred == null) ? pe : new CoreAnd(pred, pe); // AND-chain
						}
					} else {
						// Fallback por compatibilidad (método deprecado)
						@SuppressWarnings("deprecation")
						Expression legacy = j.getOnExpression();
						if (legacy != null)
							pred = mapBool(legacy);
					}

					base = new CoreJoin(base, right, pred);
				}
			}
		}

		return base;
	}

	private CoreRel mapFromItem(FromItem fi) {
		CoreRel rel;
		if (fi instanceof Table t) {
			rel = new CoreTable(t.getFullyQualifiedName());
		} else if (fi instanceof SubSelect) {
			// REGLA: Subselects sólo en WHERE (1 nivel). No permitidos en FROM.
			throw new IllegalArgumentException(
					"Subconsulta en FROM no permitida. Solo se aceptan subconsultas (1 nivel) en la cláusula WHERE.");
		} else {
			throw new UnsupportedOperationException("FromItem no soportado: " + fi.getClass().getSimpleName());
		}
		Alias alias = fi.getAlias();
		if (alias != null) {
			rel = new CoreAlias(rel, alias.getName());
		}
		return rel;
	}

	// ---------- SELECT list ----------

	private List<CoreProjItem> mapSelectItems(List<SelectItem> sis) {
		if (sis == null || sis.isEmpty())
			return List.of(new CoreProjAll());
		List<CoreProjItem> out = new ArrayList<>();
		for (SelectItem it : sis) {
			if (it instanceof AllColumns) {
				out.add(new CoreProjAll());
			} else if (it instanceof AllTableColumns atc) {
				out.add(new CoreProjAllFrom(atc.getTable().getFullyQualifiedName()));
			} else if (it instanceof SelectExpressionItem sei) {
				String alias = sei.getAlias() != null ? sei.getAlias().getName() : null;
				out.add(new CoreProjExpr(mapExpr(sei.getExpression()), alias));
			} else {
				throw new UnsupportedOperationException("SelectItem no soportado: " + it.getClass().getSimpleName());
			}
		}
		return out;
	}

	// ---------- WHERE / ON ----------

	private CorePred mapWhere(Expression e) {
		if (e == null)
			return null;
		return mapBool(e);
	}

	private CorePred mapBool(Expression e) {
		if (e == null)
			return null;

		// Paréntesis
		if (e instanceof Parenthesis p) {
			return mapBool(p.getExpression());
		}
		// NOT
		if (e instanceof NotExpression n) {
			return new CoreNot(mapBool(n.getExpression()));
		}
		// AND / OR
		if (e instanceof AndExpression a) {
			return new CoreAnd(mapBool(a.getLeftExpression()), mapBool(a.getRightExpression()));
		}
		if (e instanceof OrExpression o) {
			return new CoreOr(mapBool(o.getLeftExpression()), mapBool(o.getRightExpression()));
		}
		if (e instanceof InExpression in) {
		    if (in.isNot()) {
		        throw new UnsupportedOperationException("NOT IN no soportado aún (requiere anti-join/antijoin).");
		    }

		    // Lado izquierdo
		    CoreExpr left = mapExpr(in.getLeftExpression());

		    // Parte derecha
		    ItemsList right = in.getRightItemsList();

		    // En algunas versiones/inputs mal formados puede venir null
		    if (right == null) {
		        // Intenta fallback por compatibilidad (algunas versiones antiguas tenían rightExpression)
		        try {
		            java.lang.reflect.Method m = e.getClass().getMethod("getRightExpression");
		            Object maybe = m.invoke(e);
		            if (maybe instanceof SubSelect ss2) {
		                return new CoreInSubselect(left, mapSubselect(ss2));
		            }
		        } catch (Throwable ignore) { /* sin-op */ }

		    // IN (subselect)
		    } else if (right instanceof SubSelect ss) {
		        return new CoreInSubselect(left, mapSubselect(ss));

		    // IN (lista de expresiones)
		    } else if (right instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList el) {
		        List<CoreExpr> values = new java.util.ArrayList<>();
		        for (Expression ex : el.getExpressions()) {
		            values.add(mapExpr(ex));
		        }
		        return new CoreInList(left, values);

		    // IN (lista múltiple: ( (a,b), (c,d) ) ) -> no soportado por ahora
		    } else if (right instanceof net.sf.jsqlparser.expression.operators.relational.MultiExpressionList) {
		        throw new UnsupportedOperationException("IN con MultiExpressionList no soportado.");

		    } else {
		        throw new UnsupportedOperationException("IN con items no soportados: " + right.getClass().getName());
		    }

		    // Si llegamos aquí, no pudimos resolver la parte derecha
		    throw new UnsupportedOperationException("IN sin parte derecha (items/subselect) no soportado.");
		}

		// Comparadores
		if (e instanceof EqualsTo c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.EQ, mapExpr(c.getRightExpression()));
		if (e instanceof NotEqualsTo c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.NEQ, mapExpr(c.getRightExpression()));
		if (e instanceof GreaterThan c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.GT, mapExpr(c.getRightExpression()));
		if (e instanceof GreaterThanEquals c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.GTE, mapExpr(c.getRightExpression()));
		if (e instanceof MinorThan c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.LT, mapExpr(c.getRightExpression()));
		if (e instanceof MinorThanEquals c)
			return new CoreCmp(mapExpr(c.getLeftExpression()), CmpOp.LTE, mapExpr(c.getRightExpression()));

		// BETWEEN a AND b
		if (e instanceof Between b) {
			return new CoreBetween(mapExpr(b.getLeftExpression()), mapExpr(b.getBetweenExpressionStart()),
					mapExpr(b.getBetweenExpressionEnd()));
		}

		// IN (lista)
		if (e instanceof InExpression in && !in.isNot()) {
			Expression left = in.getLeftExpression();
			ItemsList list = in.getRightItemsList();
			if (list instanceof ExpressionList el) {
				List<CoreExpr> values = el.getExpressions().stream().map(this::mapExpr).collect(Collectors.toList());
				return new CoreInList(mapExpr(left), values);
			}
		}

		// IN (subselect) / EXISTS / NOT EXISTS (1 nivel)
		if (e instanceof InExpression in2 && in2.getRightItemsList() instanceof SubSelect ss && !in2.isNot()) {
			return new CoreInSubselect(mapExpr(in2.getLeftExpression()), mapSubselect(ss));
		}
		if (e instanceof ExistsExpression ex && !ex.isNot()) {
			if (!(ex.getRightExpression() instanceof SubSelect ss)) {
				throw new IllegalArgumentException("EXISTS requiere subselect.");
			}
			return new CoreExists(mapSubselect(ss));
		}
		if (e instanceof ExistsExpression exNot && exNot.isNot()) {
			if (!(exNot.getRightExpression() instanceof SubSelect ss)) {
				throw new IllegalArgumentException("NOT EXISTS requiere subselect.");
			}
			return new CoreNotExists(mapSubselect(ss));
		}
		// IS NULL / IS NOT NULL
		if (e instanceof IsNullExpression inz) {
			// lado izquierdo (columna o expresión)
			CoreExpr left = mapExpr(inz.getLeftExpression());
			// Eq con NULL => IS NULL; Neq con NULL => IS NOT NULL
			return new CoreCmp(left, inz.isNot() ? CmpOp.NEQ : CmpOp.EQ, new CoreNull());
		}
		if (e instanceof IsNullExpression inz) {
			CoreExpr left = mapExpr(inz.getLeftExpression());
			return new CoreCmp(left, inz.isNot() ? CmpOp.NEQ : CmpOp.EQ, new CoreNull());
		}

		throw new UnsupportedOperationException("Expresión booleana no soportada: " + e.getClass().getSimpleName());
	}

	// ---------- Subselect (1 columna) ----------

	private CoreSubselect mapSubselect(SubSelect ss) {
		if (!(ss.getSelectBody() instanceof PlainSelect ps)) {
			throw new UnsupportedOperationException("Subselect debe ser SELECT plano.");
		}
		// FROM
		CoreRel from = buildFrom(ps.getFromItem(), ps.getJoins());
		// WHERE (sin subconsultas por gramática interna)
		CorePred where = mapWhere(ps.getWhere()); // normalizador validará que no traiga subconsultas

		// Proyección UNA columna
		List<SelectItem> sis = ps.getSelectItems();
		if (sis == null || sis.size() != 1) {
			throw new IllegalStateException("Subselect debe proyectar exactamente una columna.");
		}
		SelectItem only = sis.get(0);
		CoreProjItem proj;
		if (only instanceof SelectExpressionItem sei) {
			proj = new CoreProjExpr(mapExpr(sei.getExpression()),
					sei.getAlias() != null ? sei.getAlias().getName() : null);
		} else if (only instanceof AllTableColumns || only instanceof AllColumns) {
			throw new IllegalStateException("Subselect de IN/EXISTS no puede usar * o t.*");
		} else {
			throw new UnsupportedOperationException(
					"SelectItem no soportado en subselect: " + only.getClass().getSimpleName());
		}
		return new CoreSubselect(from, proj, where);
	}

	// ---------- Expr ----------

	private CoreExpr mapExpr(Expression e) {
		if (e == null)
			return new CoreNull();

		if (e instanceof Parenthesis p)
			return new CoreParen(mapExpr(p.getExpression()));
		if (e instanceof Column c) {
			String tbl = c.getTable() != null ? c.getTable().getFullyQualifiedName() : null;
			return new CoreColumnRef(tbl, c.getColumnName());
		}
		if (e instanceof LongValue lv)
			return new CoreNumber(lv.getStringValue());
		if (e instanceof DoubleValue dv)
			return new CoreNumber(dv.toString()); // conserva el lexema
		if (e instanceof StringValue sv)
			return new CoreString(sv.getValue());
		if (e instanceof NullValue)
			return new CoreNull();

		// Unario
		if (e instanceof SignedExpression sx) {
			ArithOp op = (sx.getSign() == '-') ? ArithOp.SUB : ArithOp.ADD;
			return new CoreUnaryArith(op, mapExpr(sx.getExpression()));
		}

		// Binario aritmético
		if (e instanceof Addition a)
			return new CoreBinArith(ArithOp.ADD, mapExpr(a.getLeftExpression()), mapExpr(a.getRightExpression()));
		if (e instanceof Subtraction s)
			return new CoreBinArith(ArithOp.SUB, mapExpr(s.getLeftExpression()), mapExpr(s.getRightExpression()));
		if (e instanceof Multiplication m)
			return new CoreBinArith(ArithOp.MUL, mapExpr(m.getLeftExpression()), mapExpr(m.getRightExpression()));
		if (e instanceof Division d)
			return new CoreBinArith(ArithOp.DIV, mapExpr(d.getLeftExpression()), mapExpr(d.getRightExpression()));

		throw new UnsupportedOperationException("Expresión no soportada: " + e.getClass().getSimpleName());
	}
}
