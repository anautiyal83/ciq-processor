package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.ConditionalAllowedValues;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@code allowedValuesWhen} rules.
 *
 * <p>Each condition entry is evaluated independently:
 * <ul>
 *   <li>If the trigger column equals the trigger value (case-insensitive) and
 *       {@code allowedValues} is <em>empty</em>, the cell must be blank.</li>
 *   <li>If the trigger column equals the trigger value and {@code allowedValues} is
 *       <em>non-empty</em>, the cell value must match one of the listed values
 *       (case-insensitive).  Blank cells pass this check.</li>
 * </ul>
 *
 * <p>All matching conditions are evaluated; a cell can trigger more than one entry.
 */
public class AllowedValuesWhenValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        List<ConditionalAllowedValues> conditions = rule.getAllowedValuesWhen();
        if (conditions == null || conditions.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<ValidationError> errors = new ArrayList<>();
        boolean isBlank = value == null || value.trim().isEmpty();

        for (ConditionalAllowedValues cond : conditions) {
            String triggerVal = row.get(cond.getColumn());
            if (triggerVal == null || !cond.getValue().equalsIgnoreCase(triggerVal.trim())) {
                continue; // condition not triggered
            }

            List<String> allowed = cond.getAllowedValues();
            if (allowed == null || allowed.isEmpty()) {
                // Must be blank
                if (!isBlank) {
                    errors.add(new ValidationError(
                            row.getRowNumber(), colName, value,
                            "Column '" + colName + "' must be blank when "
                            + cond.getColumn() + "=" + cond.getValue()
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
                        errors.add(new ValidationError(
                                row.getRowNumber(), colName, value,
                                "Value '" + value + "' is not allowed when "
                                + cond.getColumn() + "=" + cond.getValue()
                                + ". Allowed values: " + allowed));
                    }
                }
            }
        }
        return errors;
    }
}
