package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates the {@code pattern} rule (full regex match) and provides built-in
 * format checks for {@code type: mac} and {@code type: phone} when no custom
 * pattern is configured.
 *
 * <p>Trigger conditions (OR):
 * <ul>
 *   <li>{@code rule.getPattern() != null} — custom regex override (any type)</li>
 *   <li>{@code rule.isMacType()}          — built-in MAC address pattern</li>
 *   <li>{@code rule.isPhoneType()}        — built-in phone number pattern + digit count</li>
 * </ul>
 *
 * <p>Blank values are silently skipped.
 */
public class PatternValidator implements CellValidator {

    private static final Logger log = LoggerFactory.getLogger(PatternValidator.class);

    /** Accepts colon-separated, hyphen-separated, or plain 12-hex-digit MAC addresses. */
    private static final Pattern MAC_PATTERN = Pattern.compile(
            "^([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}$|^[0-9A-Fa-f]{12}$");

    /** Allows optional +, then digits/spaces/hyphens/parens; overall 7–20 chars. */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[\\d\\s().\\-]{7,20}$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        boolean hasCustomPattern = rule.getPattern() != null;
        boolean isMac   = rule.isMacType();
        boolean isPhone = rule.isPhoneType();

        // Nothing to do for this validator
        if (!hasCustomPattern && !isMac && !isPhone) return Collections.emptyList();

        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String v = value.trim();

        // --- Custom pattern overrides built-in checks (works for ANY type) ---
        if (hasCustomPattern) {
            try {
                if (!Pattern.compile(rule.getPattern()).matcher(v).matches()) {
                    String defaultMsg = (rule.getPatternMessage() != null
                                        && !rule.getPatternMessage().isEmpty())
                            ? rule.getPatternMessage() + " (got: '" + v + "')"
                            : "Value '" + v + "' does not match pattern: " + rule.getPattern();
                    return Collections.singletonList(new ValidationError(
                            row.getRowNumber(), colName, value,
                            CellValidator.msg(rule.getMessages(), "pattern", defaultMsg)));
                }
            } catch (PatternSyntaxException e) {
                log.error("Invalid regex pattern '{}' configured for column '{}': {}",
                        rule.getPattern(), colName, e.getMessage());
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        "Configuration error: invalid regex pattern '" + rule.getPattern()
                        + "' for column '" + colName + "': " + e.getDescription()));
            }
            return Collections.emptyList();
        }

        // --- Built-in phone check ---
        if (isPhone) {
            if (!PHONE_PATTERN.matcher(v).matches()) {
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "pattern",
                            "Value '" + value + "' is not a valid phone number; "
                            + "expected E.164 format, e.g. +14155552671")));
            }
            // Count digits — must be 7..15
            int digitCount = 0;
            for (char c : v.toCharArray()) {
                if (Character.isDigit(c)) digitCount++;
            }
            if (digitCount < 7 || digitCount > 15) {
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "pattern",
                            "Phone number '" + value + "' has " + digitCount
                            + " digit(s); must have 7\u201315 digits")));
            }
            return Collections.emptyList();
        }

        // --- Built-in MAC check ---
        if (isMac) {
            if (!MAC_PATTERN.matcher(v).matches()) {
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "pattern",
                            "Value '" + value + "' is not a valid MAC address; "
                            + "expected AA:BB:CC:DD:EE:FF, AA-BB-CC-DD-EE-FF, or AABBCCDDEEFF")));
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
