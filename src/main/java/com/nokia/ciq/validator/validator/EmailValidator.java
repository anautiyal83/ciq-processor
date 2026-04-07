package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates the {@code email: true} rule.
 *
 * <p>Checks that the value is a valid email address in the form {@code local@domain.tld}.
 * Blank values are silently skipped (pair with {@code required: true} to reject blanks).
 *
 * <p>YAML usage:
 * <pre>
 * EMAIL:
 *   required: true
 *   email:    true
 * </pre>
 */
public class EmailValidator implements CellValidator {

    /**
     * RFC 5322-inspired pattern covering the common practical subset:
     * local part: alphanumeric + {@code . _ % + -}
     * domain:     at least one label with alphanumeric / hyphens, dot-separated
     * TLD:        2 or more alpha characters
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isEmail() && !rule.isEmailType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        List<ValidationError> errors = new ArrayList<>();
        for (String part : value.split(",")) {
            String email = part.trim();
            if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        CellValidator.msg(rule.getMessages(), "type",
                            "'" + email + "' is not a valid email address")));
            }
        }
        return errors;
    }
}
