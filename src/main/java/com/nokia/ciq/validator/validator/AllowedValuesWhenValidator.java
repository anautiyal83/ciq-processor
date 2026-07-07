package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.ConditionalAllowedValues;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Validates {@code allowedValuesWhen} rules.
 *
 * <p>Each condition entry is evaluated independently.  The trigger condition is formed by
 * comparing the trigger column's value using the configured {@code operator}
 * (defaults to {@code equals} when omitted).
 *
 * <p>When the condition is satisfied:
 * <ul>
 *   <li>Empty {@code allowedValues} → cell must be blank.</li>
 *   <li>Non-empty {@code allowedValues} → cell value must match one of the listed values
 *       (case-insensitive).  Blank cells pass this check.</li>
 * </ul>
 *
 * <p>Supported operators (same set as row-rule {@code when:} conditions):
 * {@code equals} (default), {@code notEquals}, {@code contains}, {@code blank},
 * {@code notBlank}, {@code greaterThan}, {@code greaterThanOrEquals},
 * {@code lessThan}, {@code lessThanOrEquals}.
 * Symbol aliases {@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=}
 * are also accepted.
 */
public class AllowedValuesWhenValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        List<ConditionalAllowedValues> conditions = rule.getAllowedValuesWhen();
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyList();
        }

        List<ValidationError> errors = new ArrayList<>();
        boolean isBlank = value == null || value.trim().isEmpty();

        for (ConditionalAllowedValues cond : conditions) {
            if (!conditionMatches(cond, row)) continue;

            List<String> allowed = cond.getAllowedValues();
            if (allowed == null || allowed.isEmpty()) {
                // Must be blank
                if (!isBlank) {
                    String op = describeOperator(cond);
                    String trigger = (cond.getWhen() != null && !cond.getWhen().trim().isEmpty())
                            ? "condition [" + cond.getWhen().trim() + "] holds"
                            : cond.getColumn() + " " + op + " '" + cond.getValue() + "'";
                    errors.add(new ValidationError(
                            row.getRowNumber(), colName, value,
                            "Column '" + colName + "' must be blank when "
                            + trigger + " but found '" + value + "'"));
                }
            } else {
                // Must match one of the allowed values (blank passes)
                if (!isBlank) {
                    boolean matched = false;
                    for (String a : allowed) {
                        if (a.equalsIgnoreCase(value.trim())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        String op = describeOperator(cond);
                        String trigger = (cond.getWhen() != null && !cond.getWhen().trim().isEmpty())
                                ? "condition [" + cond.getWhen().trim() + "] holds"
                                : cond.getColumn() + " " + op + " '" + cond.getValue() + "'";
                        errors.add(new ValidationError(
                                row.getRowNumber(), colName, value,
                                "Value '" + value + "' is not allowed when "
                                + trigger + ". Allowed values: " + allowed));
                    }
                }
            }
        }
        return errors;
    }

    /**
     * Returns true when the condition's trigger is satisfied for the current row.
     */
    private boolean conditionMatches(ConditionalAllowedValues cond, CiqRow row) {
        // Compound expression form, e.g. when: "A == 'No' && B == 'No' || C != 'Yes'"
        if (cond.getWhen() != null && !cond.getWhen().trim().isEmpty()) {
            return evaluateExpression(cond.getWhen(), row);
        }
        String triggerRaw = row.get(cond.getColumn());
        String trigger    = triggerRaw != null ? triggerRaw.trim() : "";
        String op         = Operator.normalize(
                cond.getOperator() != null ? cond.getOperator() : Operator.EQUALS);
        String condValue  = cond.getValue() != null ? cond.getValue().trim() : "";

        switch (op) {
            case Operator.BLANK:
                return trigger.isEmpty();

            case Operator.NOT_BLANK:
                return !trigger.isEmpty();

            case Operator.CONTAINS:
                return trigger.toLowerCase().contains(condValue.toLowerCase());

            case Operator.EQUALS:
                return condValue.equalsIgnoreCase(trigger);

            case Operator.NOT_EQUALS:
                return !condValue.equalsIgnoreCase(trigger);

            case Operator.GREATER_THAN:
            case Operator.GREATER_THAN_OR_EQUALS:
            case Operator.LESS_THAN:
            case Operator.LESS_THAN_OR_EQUALS: {
                // Try numeric comparison first, fall back to lexicographic
                try {
                    double a = Double.parseDouble(trigger);
                    double b = Double.parseDouble(condValue);
                    return Operator.evaluate(op, Double.compare(a, b));
                } catch (NumberFormatException e) {
                    return Operator.evaluate(op, trigger.compareToIgnoreCase(condValue));
                }
            }

            default:
                return false;
        }
    }

    /** Human-readable operator description for error messages. */
    private String describeOperator(ConditionalAllowedValues cond) {
        String op = Operator.normalize(
                cond.getOperator() != null ? cond.getOperator() : Operator.EQUALS);
        switch (op) {
            case Operator.EQUALS:                 return "=";
            case Operator.NOT_EQUALS:             return "!=";
            case Operator.CONTAINS:               return "contains";
            case Operator.BLANK:                  return "is blank";
            case Operator.NOT_BLANK:              return "is not blank";
            case Operator.GREATER_THAN:           return ">";
            case Operator.GREATER_THAN_OR_EQUALS: return ">=";
            case Operator.LESS_THAN:              return "<";
            case Operator.LESS_THAN_OR_EQUALS:    return "<=";
            default:                              return op;
        }
    }

    // ---- compound `when:` expression evaluation -----------------------------
    // Grammar: OR ('||') of ANDs ('&&') of terms "COL <op> literal".
    // Ops: == != > >= < <= ; literals may be single/double quoted; a column may be
    // referenced by its full header or its leaf (segment after the last '.').

    boolean evaluateExpression(String expr, CiqRow row) {
        if (expr == null) return false;
        String e = expr.trim();
        if (e.startsWith("${") && e.endsWith("}")) e = e.substring(2, e.length() - 1).trim();
        List<String> ors = splitTopLevel(e, "||");
        if (ors.size() > 1) {
            for (String o : ors) if (evaluateExpression(o, row)) return true;
            return false;
        }
        List<String> ands = splitTopLevel(e, "&&");
        if (ands.size() > 1) {
            for (String a : ands) if (!evaluateExpression(a, row)) return false;
            return true;
        }
        return evaluateTerm(e, row);
    }

    private boolean evaluateTerm(String term, CiqRow row) {
        String t = term.trim();
        if (t.startsWith("(") && t.endsWith(")")) {
            return evaluateExpression(t.substring(1, t.length() - 1), row);
        }
        String[] ops = {">=", "<=", "!=", "==", ">", "<"};
        for (String op : ops) {
            int idx = indexOutsideQuotes(t, op);
            if (idx > 0) {
                String lhs = t.substring(0, idx).trim();
                String rhs = stripQuotes(t.substring(idx + op.length()).trim());
                String raw = resolve(row, lhs);
                String v   = raw != null ? raw.trim() : "";
                if ("==".equals(op)) return v.equalsIgnoreCase(rhs);
                if ("!=".equals(op)) return !v.equalsIgnoreCase(rhs);
                String norm = Operator.normalize(op);
                try {
                    return Operator.evaluate(norm,
                            Double.compare(Double.parseDouble(v), Double.parseDouble(rhs)));
                } catch (NumberFormatException ex) {
                    return Operator.evaluate(norm, v.compareToIgnoreCase(rhs));
                }
            }
        }
        // Bare column reference -> truthy when non-blank
        String raw = resolve(row, t);
        return raw != null && !raw.trim().isEmpty();
    }

    /** Splits on {@code sep} at top level (ignoring separators inside quotes). */
    private List<String> splitTopLevel(String expr, String sep) {
        List<String> parts = new ArrayList<>();
        int last = 0; boolean sq = false, dq = false;
        int i = 0;
        while (i <= expr.length() - sep.length()) {
            char c = expr.charAt(i);
            if (c == '\'' && !dq) { sq = !sq; i++; continue; }
            if (c == '"'  && !sq) { dq = !dq; i++; continue; }
            if (!sq && !dq && expr.regionMatches(i, sep, 0, sep.length())) {
                parts.add(expr.substring(last, i));
                i += sep.length(); last = i; continue;
            }
            i++;
        }
        parts.add(expr.substring(last));
        return parts;
    }

    /** Index of {@code op} in {@code s} outside quotes, or -1. */
    private int indexOutsideQuotes(String s, String op) {
        boolean sq = false, dq = false;
        for (int i = 0; i <= s.length() - op.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !dq) { sq = !sq; continue; }
            if (c == '"'  && !sq) { dq = !dq; continue; }
            if (!sq && !dq && s.regionMatches(i, op, 0, op.length())) return i;
        }
        return -1;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && ((s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'')
                             || (s.charAt(0) == '"'  && s.charAt(s.length() - 1) == '"'))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Resolves a column value by full header, else by leaf (segment after the last '.'). */
    private String resolve(CiqRow row, String col) {
        String v = row.get(col);
        if (v != null) return v;
        String target = norm(col);
        for (Map.Entry<String, String> e : row.getData().entrySet()) {
            String key = e.getKey();
            String leaf = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
            if (norm(leaf).equals(target) || norm(key).equals(target)) return e.getValue();
        }
        return null;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace("_", "").replace(" ", "").toLowerCase();
    }
}
