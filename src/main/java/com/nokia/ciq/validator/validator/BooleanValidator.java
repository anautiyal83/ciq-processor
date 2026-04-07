package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Validates {@code type: boolean} columns.
 *
 * <p>The {@code accept} field controls which boolean representation is expected:
 * <ul>
 *   <li>{@code true/false} (default) — accepts {@code true} or {@code false}</li>
 *   <li>{@code yes/no}               — accepts {@code yes} or {@code no}</li>
 *   <li>{@code 1/0}                  — accepts {@code 1} or {@code 0}</li>
 * </ul>
 * Matching is always case-insensitive.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * IS_ACTIVE:
 *   type: boolean
 *   accept: yes/no    # optional; default is true/false
 * </pre>
 */
public class BooleanValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isBooleanType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String accept = rule.getAccept();
        List<String> valid;
        if ("yes/no".equalsIgnoreCase(accept)) {
            valid = Arrays.asList("yes", "no");
        } else if ("1/0".equals(accept)) {
            valid = Arrays.asList("1", "0");
        } else {
            valid = Arrays.asList("true", "false");
        }

        for (String v : valid) {
            if (v.equalsIgnoreCase(value.trim())) return Collections.emptyList();
        }

        String acceptDesc = accept != null ? accept : "true/false";
        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' is not a valid boolean; expected one of: "
                + String.join(", ", valid) + " (accept: " + acceptDesc + ")"));
    }
}
