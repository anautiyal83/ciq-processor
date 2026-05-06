package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: hostname} (RFC 1123) and {@code type: fqdn} columns
 * (merged from FqdnValidator).
 *
 * <p>Hostname rules:
 * <ul>
 *   <li>Each label: letters, digits, and hyphens; cannot start or end with a hyphen</li>
 *   <li>Each label: max 63 characters</li>
 *   <li>Total length: max 253 characters</li>
 *   <li>Labels separated by dots; must have at least one label</li>
 * </ul>
 *
 * <p>FQDN rules (same per-label constraints plus):
 * <ul>
 *   <li>Must contain at least one dot and end with a recognised TLD (2+ alpha chars)</li>
 *   <li>Optional trailing dot is accepted (absolute FQDN)</li>
 * </ul>
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * NODE_HOSTNAME:
 *   type: hostname
 *
 * REMOTE_FQDN:
 *   type: fqdn
 * </pre>
 */
public class HostnameValidator implements CellValidator {

    // Each label: 1 char OR starts/ends with alnum, middle may have hyphens; max 63
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)"
            + "(?:\\.(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$");

    // Must have at least one label + dot + TLD (>=2 alpha), optional trailing dot
    private static final Pattern FQDN = Pattern.compile(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)"
            + "+[a-zA-Z]{2,}\\.?$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isHostnameType() && !rule.isFqdnType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String v = value.trim();

        // --- FQDN type ---
        if (rule.isFqdnType()) {
            if (v.length() > 253) {
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "FQDN '" + value + "' exceeds maximum length of 253 characters")));
            }
            if (FQDN.matcher(v).matches()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "type",
                        "Value '" + value + "' is not a valid FQDN; "
                        + "expected format: host.domain.tld (e.g. router.corp.nokia.com)")));
        }

        // --- Hostname type ---
        if (v.length() > 253) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "type",
                        "Hostname '" + value + "' exceeds maximum length of 253 characters")));
        }
        if (HOSTNAME.matcher(v).matches()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                CellValidator.msg(rule.getMessages(), "type",
                    "Value '" + value + "' is not a valid hostname (RFC 1123): "
                    + "use letters, digits, and hyphens; labels max 63 chars; no leading/trailing hyphen")));
    }
}
