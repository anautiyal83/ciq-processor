package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: phone} columns.
 *
 * <p>Accepts E.164 international format and common local formats:
 * <ul>
 *   <li>E.164: {@code +14155552671} (optional leading +, 7–15 digits)</li>
 *   <li>With separators: {@code +1-415-555-2671}, {@code (415) 555-2671}</li>
 * </ul>
 * Digits, spaces, hyphens, parentheses, and a leading + are allowed.
 * Total digits must be 7–15.
 *
 * <p>If {@code pattern} is set on the rule the built-in check is skipped —
 * {@link PatternValidator} handles the custom pattern instead.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * CONTACT_PHONE:
 *   type: phone
 * </pre>
 */
public class PhoneValidator implements CellValidator {

    // Allows optional +, then digits/spaces/hyphens/parens; total 7-15 digits
    private static final Pattern PHONE = Pattern.compile(
            "^\\+?[\\d\\s().\\-]{7,20}$");
    // Digit-only check (to count actual digits)
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\d");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isPhoneType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (rule.getPattern() != null) return Collections.emptyList();

        String v = value.trim();
        if (!PHONE.matcher(v).matches()) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' is not a valid phone number; "
                    + "expected E.164 format, e.g. +14155552671"));
        }

        // Count digits — must be 7..15
        int digitCount = 0;
        for (char c : v.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
        }
        if (digitCount < 7 || digitCount > 15) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Phone number '" + value + "' has " + digitCount
                    + " digit(s); must have 7–15 digits"));
        }

        return Collections.emptyList();
    }
}
