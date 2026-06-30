package edu.whu.tmdb.query.operations.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.whu.tmdb.query.operations.Exception.ErrorList;
import edu.whu.tmdb.query.operations.Exception.TMDBException;
import edu.whu.tmdb.query.operations.utils.SelectResult;
import edu.whu.tmdb.storage.memory.Tuple;
import edu.whu.tmdb.storage.memory.TupleList;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

public class Having {
    private final Where comparison;

    public Having() throws TMDBException, IOException {
        this.comparison = new Where();
    }

    public SelectResult having(PlainSelect plainSelect, SelectResult selectResult) throws TMDBException {
        Expression expression = plainSelect.getHaving();
        if (expression == null || selectResult.getTpl().tuplelist.isEmpty()) {
            return selectResult;
        }

        TupleList filtered = new TupleList();
        HashMap<Object, ArrayList<Integer>> filteredGroupMap = new HashMap<>();
        for (Tuple tuple : selectResult.getTpl().tuplelist) {
            if (!evaluateCondition(expression, tuple, plainSelect, selectResult)) {
                continue;
            }
            filtered.addTuple(tuple);
            if (selectResult.getGroupMap() != null && tuple.tuple.length > 0) {
                ArrayList<Integer> sourceIds = selectResult.getGroupMap().get(tuple.tuple[0]);
                if (sourceIds != null) {
                    filteredGroupMap.put(tuple.tuple[0], sourceIds);
                }
            }
        }
        selectResult.setTpl(filtered);
        if (selectResult.getGroupMap() != null) {
            selectResult.setGroupMap(filteredGroupMap);
        }
        return selectResult;
    }

    private boolean evaluateCondition(
        Expression expression,
        Tuple tuple,
        PlainSelect plainSelect,
        SelectResult selectResult
    ) throws TMDBException {
        if (expression instanceof Parenthesis) {
            return evaluateCondition(
                ((Parenthesis) expression).getExpression(), tuple, plainSelect, selectResult
            );
        }
        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            return evaluateCondition(and.getLeftExpression(), tuple, plainSelect, selectResult)
                && evaluateCondition(and.getRightExpression(), tuple, plainSelect, selectResult);
        }
        if (expression instanceof OrExpression) {
            OrExpression or = (OrExpression) expression;
            return evaluateCondition(or.getLeftExpression(), tuple, plainSelect, selectResult)
                || evaluateCondition(or.getRightExpression(), tuple, plainSelect, selectResult);
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            Object left = evaluateValue(binary.getLeftExpression(), tuple, plainSelect, selectResult);
            Object right = evaluateValue(binary.getRightExpression(), tuple, plainSelect, selectResult);
            int comparison = compare(left, right);
            if (expression instanceof EqualsTo) return comparison == 0;
            if (expression instanceof NotEqualsTo) return comparison != 0;
            if (expression instanceof GreaterThan) return comparison > 0;
            if (expression instanceof GreaterThanEquals) return comparison >= 0;
            if (expression instanceof MinorThan) return comparison < 0;
            if (expression instanceof MinorThanEquals) return comparison <= 0;
        }
        throw unsupported(expression);
    }

    private Object evaluateValue(
        Expression expression,
        Tuple tuple,
        PlainSelect plainSelect,
        SelectResult selectResult
    ) throws TMDBException {
        if (expression instanceof Parenthesis) {
            return evaluateValue(
                ((Parenthesis) expression).getExpression(), tuple, plainSelect, selectResult
            );
        }
        if (expression instanceof Function) {
            return tuple.tuple[findAggregateColumn((Function) expression, plainSelect, selectResult)];
        }
        if (expression instanceof Column) {
            return tuple.tuple[findColumn(((Column) expression).getColumnName(), selectResult)];
        }
        if (expression instanceof LongValue) {
            return ((LongValue) expression).getValue();
        }
        if (expression instanceof DoubleValue) {
            return ((DoubleValue) expression).getValue();
        }
        if (expression instanceof StringValue) {
            return ((StringValue) expression).getValue();
        }
        if (expression instanceof SignedExpression) {
            SignedExpression signed = (SignedExpression) expression;
            double value = asDouble(evaluateValue(
                signed.getExpression(), tuple, plainSelect, selectResult
            ));
            return signed.getSign() == '-' ? -value : value;
        }
        if (expression instanceof Addition
                || expression instanceof Subtraction
                || expression instanceof Multiplication
                || expression instanceof Division) {
            BinaryExpression binary = (BinaryExpression) expression;
            double left = asDouble(evaluateValue(
                binary.getLeftExpression(), tuple, plainSelect, selectResult
            ));
            double right = asDouble(evaluateValue(
                binary.getRightExpression(), tuple, plainSelect, selectResult
            ));
            if (expression instanceof Addition) return left + right;
            if (expression instanceof Subtraction) return left - right;
            if (expression instanceof Multiplication) return left * right;
            return left / right;
        }
        throw unsupported(expression);
    }

    private int findAggregateColumn(
        Function function,
        PlainSelect plainSelect,
        SelectResult selectResult
    ) throws TMDBException {
        String expressionText = function.toString();
        for (int i = 0; i < plainSelect.getSelectItems().size(); i++) {
            SelectItem item = plainSelect.getSelectItems().get(i);
            if (!(item instanceof SelectExpressionItem)) {
                continue;
            }
            SelectExpressionItem expressionItem = (SelectExpressionItem) item;
            if (expressionText.equalsIgnoreCase(expressionItem.getExpression().toString())) {
                return i;
            }
        }
        return findColumn(expressionText, selectResult);
    }

    private int findColumn(String columnName, SelectResult selectResult) throws TMDBException {
        for (int i = 0; i < selectResult.getAttrname().length; i++) {
            if (columnName.equalsIgnoreCase(selectResult.getAttrname()[i])
                    || columnName.equalsIgnoreCase(selectResult.getAlias()[i])) {
                return i;
            }
        }
        throw new TMDBException(ErrorList.COLUMN_NAME_DOES_NOT_EXIST, columnName);
    }

    private int compare(Object left, Object right) throws TMDBException {
        return comparison.compare(left, right);
    }

    private double asDouble(Object value) {
        return Double.parseDouble(String.valueOf(value));
    }

    private TMDBException unsupported(Expression expression) {
        return new TMDBException(
            ErrorList.TYPE_IS_NOT_SUPPORTED,
            "HAVING expression " + expression.getClass().getSimpleName()
        );
    }
}
