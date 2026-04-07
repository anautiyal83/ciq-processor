package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles {@code sum: [cols] equals: col} row rules.
 *
 * <p>Computes the sum of the values in {@code sum} columns and checks that the
 * result equals the value in the {@code equals} column (within a tolerance of
 * {@code 0.0001}).
 *
 * <p>If any of the columns (sum or equals) is blank or non-numeric the row is
 * silently skipped.
 */
public class SumEqualsValidator implements RowValidator {

    private static final double TOLERANCE = 0.0001;

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        if (rule.getSum() == null || rule.getSum().isEmpty() || rule.getEquals() == null) {
            return Collections.emptyList();
        }

        double sum = 0.0;
        for (String col : rule.getSum()) {
            String val = row.get(col);
            if (isBlank(val)) return Collections.emptyList();
            try {
                sum += Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return Collections.emptyList(); // non-numeric — skip silently
            }
        }

        String expectedStr = row.get(rule.getEquals());
        if (isBlank(expectedStr)) return Collections.emptyList();
        double expected;
        try {
            expected = Double.parseDouble(expectedStr);
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

        if (Math.abs(sum - expected) > TOLERANCE) {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(row.getRowNumber(), rule.getEquals(), expectedStr,
                    "Sum of " + rule.getSum() + " is " + sum
                    + " but expected " + expected + " (column: " + rule.getEquals() + ")"));
            return errors;
        }
        return Collections.emptyList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
