package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.RowCondition;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles {@code require} and {@code forbid} row rules, optionally gated by a
 * {@code when} condition.
 *
 * <ul>
 *   <li>{@code require: Column} — the column must be non-blank when the condition is true.</li>
 *   <li>{@code forbid:  Column} — the column must be blank when the condition is true.</li>
 * </ul>
 */
public class ConditionalRowRuleValidator implements RowValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        if (rule.getRequire() == null && rule.getForbid() == null) {
            return Collections.emptyList();
        }

        List<ValidationError> errors = new ArrayList<>();
        boolean conditionMet = evaluateCondition(rule.getWhen(), row);

        if (rule.getRequire() != null && conditionMet) {
            String val = row.get(rule.getRequire());
            if (isBlank(val)) {
                errors.add(new ValidationError(row.getRowNumber(), rule.getRequire(), null,
                        "Column '" + rule.getRequire() + "' is required but is blank"));
            }
        }

        if (rule.getForbid() != null && conditionMet) {
            String val = row.get(rule.getForbid());
            if (!isBlank(val)) {
                errors.add(new ValidationError(row.getRowNumber(), rule.getForbid(), val,
                        "Column '" + rule.getForbid() + "' must be blank but contains: " + val));
            }
        }

        return errors;
    }

    /**
     * Evaluates a {@link RowCondition} against the given row.
     * A {@code null} condition always returns {@code true} (no condition = always apply).
     */
    boolean evaluateCondition(RowCondition cond, CiqRow row) {
        if (cond == null) return true;

        // Compound conditions
        if (cond.getAll() != null) {
            for (RowCondition c : cond.getAll()) {
                if (!evaluateCondition(c, row)) return false;
            }
            return true;
        }
        if (cond.getAny() != null) {
            for (RowCondition c : cond.getAny()) {
                if (evaluateCondition(c, row)) return true;
            }
            return false;
        }

        // Single-cell condition
        String colVal = row.get(cond.getColumn());
        String operator = cond.getOperator() != null ? cond.getOperator().toLowerCase() : "";

        switch (operator) {
            case "blank":
                return isBlank(colVal);
            case "notblank":
                return !isBlank(colVal);
            case "equals":
                return cond.getValue() != null && cond.getValue().equalsIgnoreCase(colVal);
            case "notequals":
                return cond.getValue() == null || !cond.getValue().equalsIgnoreCase(colVal);
            case "greaterthan":
                return compareNumericOrString(colVal, cond.getValue()) > 0;
            case "lessthan":
                return compareNumericOrString(colVal, cond.getValue()) < 0;
            case "contains":
                return colVal != null && cond.getValue() != null
                        && colVal.toLowerCase().contains(cond.getValue().toLowerCase());
            default:
                // Unknown operator — treat as not matching
                return false;
        }
    }

    /**
     * Compares two values numerically if possible, falling back to case-insensitive
     * string comparison.
     */
    private int compareNumericOrString(String a, String b) {
        if (a == null || b == null) return 0;
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
