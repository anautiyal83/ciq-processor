package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates {@code type: ip} and {@code type: cidr} columns (merged from CidrValidator).
 *
 * <p>For {@code type: ip}, the {@code accepts} field controls which IP version is allowed:
 * <ul>
 *   <li>{@code ipv4} (default) — value must be a valid IPv4 address (dotted decimal)</li>
 *   <li>{@code ipv6}           — value must be a valid IPv6 address</li>
 *   <li>{@code both}           — value must be either a valid IPv4 or IPv6 address</li>
 * </ul>
 *
 * <p>For {@code type: cidr}, CIDR notation is accepted for both IPv4 and IPv6:
 * <ul>
 *   <li>IPv4: {@code 192.168.1.0/24}  (prefix 0–32)</li>
 *   <li>IPv6: {@code 2001:db8::/32}   (prefix 0–128)</li>
 * </ul>
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * MANAGEMENT_IP:
 *   type: ip
 *   accepts: both    # optional; default is ipv4
 *
 * SUBNET:
 *   type: cidr
 * </pre>
 */
public class IpAddressValidator implements CellValidator {

    // Dotted-decimal IPv4: each octet 0-255
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}"
            + "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    // Simplified IPv6: covers full, compressed (::), and mixed forms
    private static final Pattern IPV6 = Pattern.compile(
            "^("
            // full 8-group form
            + "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"
            // compressed forms with ::
            + "|([0-9a-fA-F]{1,4}:){1,7}:"
            + "|:([0-9a-fA-F]{1,4}:?){1,7}"
            + "|(([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4})"
            + "|(([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2})"
            + "|(([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3})"
            + "|(([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4})"
            + "|(([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5})"
            + "|([0-9a-fA-F]{1,4}:(:[0-9a-fA-F]{1,4}){1,6})"
            + "|::(ffff(:0{1,4})?:)?"
            + "((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
            + "|::1|::"
            + ")$");

    // CIDR patterns (merged from CidrValidator)
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern IPV4_CIDR = Pattern.compile(
            "^(" + IPV4_OCTET + "\\.){3}" + IPV4_OCTET + "/([0-9]|[12]\\d|3[0-2])$");
    private static final Pattern IPV6_CIDR = Pattern.compile(
            "^[0-9a-fA-F:]+(?::[0-9a-fA-F]*)*/([0-9]|[1-9]\\d|1[01]\\d|12[0-8])$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isIpType() && !rule.isCidrType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String v = value.trim();

        // --- CIDR type ---
        if (rule.isCidrType()) {
            if (IPV4_CIDR.matcher(v).matches() || IPV6_CIDR.matcher(v).matches()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "type",
                        "Value '" + value + "' is not a valid CIDR notation; "
                        + "expected format: 192.168.1.0/24 or 2001:db8::/32")));
        }

        // --- IP type ---
        String accepts = rule.getAccepts() != null ? rule.getAccepts().toLowerCase() : "ipv4";

        boolean valid;
        String expected;
        switch (accepts) {
            case "ipv6":
                valid    = IPV6.matcher(v).matches();
                expected = "IPv6";
                break;
            case "both":
                valid    = IPV4.matcher(v).matches() || IPV6.matcher(v).matches();
                expected = "IPv4 or IPv6";
                break;
            default: // ipv4
                valid    = IPV4.matcher(v).matches();
                expected = "IPv4";
                break;
        }

        if (valid) return Collections.emptyList();
        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                CellValidator.msg(rule.getMessages(), "type",
                    "Value '" + value + "' is not a valid " + expected + " address")));
    }
}
