package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * Validates enum type ({@code type: enum} + {@code values}) and the
 * {@code allowedValues} string constraint — both case-insensitive.
 *
 * <p>Precedence: if {@code type: enum} is set, {@code values} is used and
 * {@code allowedValues} is ignored.  For all other types, {@code allowedValues}
 * is used as a soft constraint on the string value.
 *
 * <p>Blank values are silently skipped — pair with {@link RequiredValidator} to
 * enforce presence.
 */
public class AllowedValuesValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        List<String> allowed;
        String context;
        if (rule.isEnum()) {
            allowed  = rule.getValues();
            context  = "enum values";
        } else if (rule.isProtocolType()) {
            allowed  = rule.getValues();
            context  = "allowed protocols";
        } else {
            allowed  = rule.getAllowedValues();
            context  = "allowed values";
        }

        if (allowed == null || allowed.isEmpty()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        for (String a : allowed) {
            if (a != null && a.equalsIgnoreCase(value)) return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' not in " + context + ": " + allowed));
    }
}
