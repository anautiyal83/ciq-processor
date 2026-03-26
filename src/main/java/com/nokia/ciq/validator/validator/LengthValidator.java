package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates {@code minLength} and {@code maxLength} rules.
 * Blank values are silently skipped.
 */
public class LengthValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (rule.getMinLength() == null && rule.getMaxLength() == null) return Collections.emptyList();

        List<ValidationError> errors = new ArrayList<>();

        if (rule.getMinLength() != null && value.length() < rule.getMinLength()) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    "Value length " + value.length() + " is below minimum length " + rule.getMinLength()));
        }
        if (rule.getMaxLength() != null && value.length() > rule.getMaxLength()) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    "Value length " + value.length() + " exceeds maximum length " + rule.getMaxLength()));
        }

        return errors;
    }
}
