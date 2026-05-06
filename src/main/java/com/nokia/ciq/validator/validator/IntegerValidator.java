package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.IntRange;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates the {@code integer} rule together with optional {@code minValue} / {@code maxValue}
 * and {@code allowedRanges} (merged from AllowedRangesValidator).
 * Blank values are silently skipped.
 */
public class IntegerValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isInteger() && !rule.isIntegerType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        try {
            long num = Long.parseLong(value.trim());
            List<ValidationError> errors = new ArrayList<>();

            if (rule.getMinValue() != null && num < rule.getMinValue()) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "min",
                            "Value " + num + " is below minimum " + rule.getMinValue())));
            }
            if (rule.getMaxValue() != null && num > rule.getMaxValue()) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "max",
                            "Value " + num + " exceeds maximum " + rule.getMaxValue())));
            }

            // AllowedRanges check (merged from AllowedRangesValidator)
            List<IntRange> ranges = rule.getAllowedRanges();
            if (ranges != null && !ranges.isEmpty()) {
                boolean inRange = false;
                for (IntRange r : ranges) {
                    if (num >= r.getMin() && num <= r.getMax()) { inRange = true; break; }
                }
                if (!inRange) {
                    return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Value " + num + " is not within any allowed range: " + ranges)));
                }
            }

            return errors;

        } catch (NumberFormatException e) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "type",
                        "Value '" + value + "' is not a valid integer")));
        }
    }
}
