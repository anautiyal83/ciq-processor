package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: mac} columns.
 *
 * <p>Accepts the three standard MAC address formats:
 * <ul>
 *   <li>Colon-separated:  {@code AA:BB:CC:DD:EE:FF}</li>
 *   <li>Hyphen-separated: {@code AA-BB-CC-DD-EE-FF}</li>
 *   <li>Plain hex:        {@code AABBCCDDEEFF}</li>
 * </ul>
 * Hex digits are accepted in upper or lower case.
 *
 * <p>If {@code pattern} is set on the rule the built-in check is skipped —
 * {@link PatternValidator} handles the custom pattern instead.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * MAC_ADDRESS:
 *   type: mac
 *   # optional: pattern: "^([0-9A-F]{2}:){5}[0-9A-F]{2}$"  # restrict to upper-case colon form
 * </pre>
 */
public class MacAddressValidator implements CellValidator {

    private static final Pattern MAC_COLON  =
            Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    private static final Pattern MAC_HYPHEN =
            Pattern.compile("^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$");
    private static final Pattern MAC_PLAIN  =
            Pattern.compile("^[0-9A-Fa-f]{12}$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isMacType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        // Defer to PatternValidator when a custom pattern is provided
        if (rule.getPattern() != null) return Collections.emptyList();

        String v = value.trim();
        if (MAC_COLON.matcher(v).matches()
                || MAC_HYPHEN.matcher(v).matches()
                || MAC_PLAIN.matcher(v).matches()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' is not a valid MAC address; "
                + "expected AA:BB:CC:DD:EE:FF, AA-BB-CC-DD-EE-FF, or AABBCCDDEEFF"));
    }
}
