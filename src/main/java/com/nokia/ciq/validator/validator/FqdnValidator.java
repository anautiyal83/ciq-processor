package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: fqdn} (Fully Qualified Domain Name) columns.
 *
 * <p>An FQDN must contain at least one dot and end with a recognised TLD (2+ alpha chars).
 * An optional trailing dot is accepted (absolute FQDN).
 * Examples: {@code host.example.com}, {@code router.corp.nokia.com.}
 *
 * <p>Per-label rules (same as {@link HostnameValidator}):
 * labels use letters/digits/hyphens, max 63 chars, no leading/trailing hyphen.
 *
 * <p>If {@code pattern} is set on the rule the built-in check is skipped —
 * {@link PatternValidator} handles the custom pattern instead.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * REMOTE_FQDN:
 *   type: fqdn
 * </pre>
 */
public class FqdnValidator implements CellValidator {

    // Must have at least one label + dot + TLD (>=2 alpha), optional trailing dot
    private static final Pattern FQDN = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)"
            + "+[a-zA-Z]{2,}\\.?$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isFqdnType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (rule.getPattern() != null) return Collections.emptyList();

        String v = value.trim();
        if (v.length() > 253) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "FQDN '" + value + "' exceeds maximum length of 253 characters"));
        }
        if (FQDN.matcher(v).matches()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' is not a valid FQDN; "
                + "expected format: host.domain.tld (e.g. router.corp.nokia.com)"));
    }
}
