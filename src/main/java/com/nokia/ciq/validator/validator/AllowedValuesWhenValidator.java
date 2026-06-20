package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.ConditionalAllowedValues;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                    errors.add(new ValidationError(
                            row.getRowNumber(), colName, value,
                            "Column '" + colName + "' must be blank when "
                            + cond.getColumn() + " " + op + " '" + cond.getValue() + "'"
                            + " but found '" + value + "'"));
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
                        errors.add(new ValidationError(
                                row.getRowNumber(), colName, value,
                                "Value '" + value + "' is not allowed when "
                                + cond.getColumn() + " " + op + " '" + cond.getValue() + "'"
                                + ". Allowed values: " + allowed));
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
}
