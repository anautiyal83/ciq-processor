package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: cidr} columns.
 *
 * <p>Accepts CIDR notation for both IPv4 and IPv6:
 * <ul>
 *   <li>IPv4: {@code 192.168.1.0/24}  (prefix 0–32)</li>
 *   <li>IPv6: {@code 2001:db8::/32}   (prefix 0–128)</li>
 * </ul>
 *
 * <p>If {@code pattern} is set on the rule the built-in check is skipped —
 * {@link PatternValidator} handles the custom pattern instead.
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * SUBNET:
 *   type: cidr
 * </pre>
 */
public class CidrValidator implements CellValidator {

    // IPv4 address part
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern IPV4_CIDR = Pattern.compile(
            "^(" + IPV4_OCTET + "\\.){3}" + IPV4_OCTET + "/([0-9]|[12]\\d|3[0-2])$");

    // Simplified IPv6 address part (covers main forms)
    private static final Pattern IPV6_CIDR = Pattern.compile(
            "^[0-9a-fA-F:]+(?::[0-9a-fA-F]*)*/([0-9]|[1-9]\\d|1[01]\\d|12[0-8])$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isCidrType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (rule.getPattern() != null) return Collections.emptyList();

        String v = value.trim();
        if (IPV4_CIDR.matcher(v).matches() || IPV6_CIDR.matcher(v).matches()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "Value '" + value + "' is not a valid CIDR notation; "
                + "expected format: 192.168.1.0/24 or 2001:db8::/32"));
    }
}
