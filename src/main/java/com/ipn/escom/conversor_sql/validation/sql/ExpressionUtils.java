package com.ipn.escom.conversor_sql.validation.sql;

import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;

public class ExpressionUtils {
	// ===== 1) Detector general: NULL y IS [NOT] NULL en cualquier expresión =====
    public static boolean containsNullSyntax(Expression expr) {
        if (expr == null) return false;
        final AtomicBoolean found = new AtomicBoolean(false);

        expr.accept(new ExpressionVisitorAdapter() {
            @Override public void visit(NullValue nullValue) { found.set(true); }
            @Override public void visit(IsNullExpression isNull) { found.set(true); }

            // Recorridos básicos (binarios / unarios / contenedores comunes)

            // Binarios de comparación ( =, <>, <, >, <=, >=, LIKE, etc.)
            @Override protected void visitBinaryExpression(BinaryExpression binaryExpr) {
                if (found.get()) return;
                binaryExpr.getLeftExpression().accept(this);
                if (found.get()) return;
                binaryExpr.getRightExpression().accept(this);
            }

            // NOT expr
            @Override public void visit(NotExpression notExpr) {
                if (found.get()) return;
                if (notExpr.getExpression() != null) notExpr.getExpression().accept(this);
            }

            // (expr)
            @Override public void visit(Parenthesis parenthesis) {
                if (found.get()) return;
                if (parenthesis.getExpression() != null) parenthesis.getExpression().accept(this);
            }

            // f(arg1, arg2, ...)
            @Override public void visit(Function function) {
                if (found.get()) return;
                if (function.getParameters() != null && function.getParameters().getExpressions() != null) {
                    for (Expression e : function.getParameters().getExpressions()) {
                        if (found.get()) break;
                        if (e != null) e.accept(this);
                    }
                }
            }

            // a BETWEEN b AND c
            @Override public void visit(Between between) {
                if (found.get()) return;
                if (between.getLeftExpression() != null) between.getLeftExpression().accept(this);
                if (found.get()) return;
                if (between.getBetweenExpressionStart() != null) between.getBetweenExpressionStart().accept(this);
                if (found.get()) return;
                if (between.getBetweenExpressionEnd() != null) between.getBetweenExpressionEnd().accept(this);
            }

            // a IN ( ... )
            @Override public void visit(InExpression inExpr) {
                if (found.get()) return;
                if (inExpr.getLeftExpression() != null) inExpr.getLeftExpression().accept(this);
                if (found.get()) return;
                ItemsList items = inExpr.getRightItemsList();
                if (items instanceof ExpressionList el && el.getExpressions() != null) {
                    for (Expression e : el.getExpressions()) {
                        if (found.get()) break;
                        if (e != null) e.accept(this);
                    }
                } else if (items instanceof MultiExpressionList mel && mel.getExpressionLists() != null) {
                    for (ExpressionList el2 : mel.getExpressionLists()) {
                        if (found.get()) break;
                        if (el2 != null && el2.getExpressions() != null) {
                            for (Expression e : el2.getExpressions()) {
                                if (found.get()) break;
                                if (e != null) e.accept(this);
                            }
                        }
                    }
                }
                // Nota: NO entramos a SubSelect; si allí hubiera NULL, lo detectarás al validar esa subconsulta por separado.
            }

            // AND / OR
            @Override public void visit(AndExpression andExpr) {
                if (found.get()) return;
                super.visit(andExpr); // delega a visitBinaryExpression
            }
            @Override public void visit(OrExpression orExpr) {
                if (found.get()) return;
                super.visit(orExpr); // delega a visitBinaryExpression
            }

            // Aritméticos (por si los permites en predicados)
            @Override public void visit(Addition expr) { super.visit(expr); }
            @Override public void visit(Subtraction expr) { super.visit(expr); }
            @Override public void visit(Multiplication expr) { super.visit(expr); }
            @Override public void visit(Division expr) { super.visit(expr); }
            @Override public void visit(Modulo expr) { super.visit(expr); }
        });

        return found.get();
    }
}
