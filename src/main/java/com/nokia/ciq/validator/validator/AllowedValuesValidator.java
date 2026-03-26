package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * Validates the {@code allowedValues} rule (case-insensitive).
 * Blank values are silently skipped — use {@link RequiredValidator} to enforce presence.
 */
public class AllowedValuesValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        List<String> allowed = rule.getAllowedValues();
        if (allowed == null || allowed.isEmpty()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        for (String a : allowed) {
            if (a != null && a.equalsIgnoreCase(value)) return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' not in allowed values: " + allowed));
    }
}
