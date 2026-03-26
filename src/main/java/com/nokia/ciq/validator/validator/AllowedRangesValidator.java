package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.IntRange;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * Validates the {@code allowedRanges} rule.
 * The value must be a valid integer that falls within at least one of the configured ranges.
 * Blank values are silently skipped.
 */
public class AllowedRangesValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        List<IntRange> ranges = rule.getAllowedRanges();
        if (ranges == null || ranges.isEmpty()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        try {
            long num = Long.parseLong(value.trim());
            for (IntRange r : ranges) {
                if (num >= r.getMin() && num <= r.getMax()) return Collections.emptyList();
            }
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value " + num + " is not within any allowed range: " + ranges));

        } catch (NumberFormatException e) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' is not a valid integer"));
        }
    }
}
