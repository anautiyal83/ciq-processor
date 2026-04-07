package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: hostname} columns (RFC 1123).
 *
 * <p>Rules:
 * <ul>
 *   <li>Each label: letters, digits, and hyphens; cannot start or end with a hyphen</li>
 *   <li>Each label: max 63 characters</li>
 *   <li>Total length: max 253 characters</li>
 *   <li>Labels separated by dots; must have at least one label</li>
 * </ul>
 *
 * <p>If {@code pattern} is set on the rule the built-in check is skipped —
 * {@link PatternValidator} handles the custom pattern instead.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * NODE_HOSTNAME:
 *   type: hostname
 * </pre>
 */
public class HostnameValidator implements CellValidator {

    // Each label: 1 char OR starts/ends with alnum, middle may have hyphens; max 63
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)"
            + "(?:\\.(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))* $"
                    .trim());

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isHostnameType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (rule.getPattern() != null) return Collections.emptyList();

        String v = value.trim();
        if (v.length() > 253) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Hostname '" + value + "' exceeds maximum length of 253 characters"));
        }
        if (HOSTNAME.matcher(v).matches()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' is not a valid hostname (RFC 1123): "
                + "use letters, digits, and hyphens; labels max 63 chars; no leading/trailing hyphen"));
    }
}
