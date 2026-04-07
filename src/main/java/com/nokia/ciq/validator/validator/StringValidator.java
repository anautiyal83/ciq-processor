package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * String-level validator — the single entry point for all text-based constraints.
 *
 * <p>Handles (in order):
 * <ol>
 *   <li><b>Length</b>   — {@code minLength} / {@code maxLength}</li>
 *   <li><b>Boolean</b>  — {@code type: boolean} + optional {@code accept: yes/no | 1/0 | true/false}</li>
 *   <li><b>URL scheme</b> — {@code type: urlScheme} + {@code values} (allowed schemes)</li>
 *   <li><b>Enum / protocol / allowedValues</b> — {@code type: enum | protocol} + {@code values},
 *       or a plain {@code allowedValues} soft constraint</li>
 * </ol>
 *
 * <p>Blank values are silently skipped — pair with {@link RequiredValidator} to enforce presence.
 *
 * <p>All error messages fall back to built-in defaults when
 * {@code messages:} is not set on the column rule.
 */
public class StringValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        List<ValidationError> errors = new ArrayList<>();

        // --- 1. Length ---
        if (rule.getMinLength() != null && value.length() < rule.getMinLength()) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "minLength",
                        "Value '" + value + "' is too short (length " + value.length()
                        + "); minimum is " + rule.getMinLength())));
        }
        if (rule.getMaxLength() != null && value.length() > rule.getMaxLength()) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    CellValidator.msg(rule.getMessages(), "maxLength",
                        "Value '" + value + "' is too long (length " + value.length()
                        + "); maximum is " + rule.getMaxLength())));
        }

        // --- 2. Boolean ---
        if (rule.isBooleanType()) {
            String accept = rule.getAccept();
            List<String> valid;
            if ("yes/no".equalsIgnoreCase(accept)) {
                valid = Arrays.asList("yes", "no");
            } else if ("1/0".equals(accept)) {
                valid = Arrays.asList("1", "0");
            } else {
                valid = Arrays.asList("true", "false");
            }
            boolean matched = valid.stream().anyMatch(v -> v.equalsIgnoreCase(value.trim()));
            if (!matched) {
                String acceptDesc = accept != null ? accept : "true/false";
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Value '" + value + "' is not a valid boolean; expected one of: "
                            + String.join(", ", valid) + " (accept: " + acceptDesc + ")")));
            }
            return errors;
        }

        // --- 3. URL scheme ---
        if (rule.isUrlSchemeType()) {
            List<String> allowed = rule.getValues();
            if (allowed == null || allowed.isEmpty()) return errors;

            URI uri;
            try {
                uri = new URI(value.trim());
            } catch (Exception e) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Value '" + value + "' is not a valid URL")));
                return errors;
            }

            String scheme = uri.getScheme();
            if (scheme == null) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Value '" + value + "' has no URL scheme; expected one of: " + allowed)));
                return errors;
            }

            boolean schemeOk = allowed.stream().anyMatch(a -> a != null && a.equalsIgnoreCase(scheme));
            if (!schemeOk) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "URL scheme '" + scheme + "' is not allowed; expected one of: " + allowed)));
            }
            return errors;
        }

        // --- 4. Enum / protocol / allowedValues ---
        List<String> allowed;
        String context;
        if (rule.isEnum()) {
            allowed = rule.getValues();
            context = "enum values";
        } else if (rule.isProtocolType()) {
            allowed = rule.getValues();
            context = "allowed protocols";
        } else {
            allowed = rule.getAllowedValues();
            context = "allowed values";
        }

        if (allowed != null && !allowed.isEmpty()) {
            boolean matched = allowed.stream().anyMatch(a -> a != null && a.equalsIgnoreCase(value));
            if (!matched) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Value '" + value + "' not in " + context + ": " + allowed)));
            }
        }

        return errors;
    }
}
