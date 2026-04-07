package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates {@code type: decimal} columns.
 *
 * <p>Checks that the value is a parseable decimal number and optionally enforces:
 * <ul>
 *   <li>{@code minDecimal} — value must be &ge; this bound</li>
 *   <li>{@code maxDecimal} — value must be &le; this bound</li>
 *   <li>{@code precision}  — value must have no more decimal places than this</li>
 * </ul>
 *
 * <p>Blank values are silently skipped — pair with {@code required: true} to reject blanks.
 *
 * <p>YAML usage:
 * <pre>
 * PRICE:
 *   type: decimal
 *   minDecimal: 0.0
 *   maxDecimal: 9999.99
 *   precision: 2
 * </pre>
 */
public class DecimalValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isDecimalType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        BigDecimal bd;
        try {
            bd = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' is not a valid decimal number"));
        }

        List<ValidationError> errors = new ArrayList<>();

        if (rule.getMinDecimal() != null
                && bd.compareTo(BigDecimal.valueOf(rule.getMinDecimal())) < 0) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    "Value " + bd.toPlainString() + " is below minimum " + rule.getMinDecimal()));
        }
        if (rule.getMaxDecimal() != null
                && bd.compareTo(BigDecimal.valueOf(rule.getMaxDecimal())) > 0) {
            errors.add(new ValidationError(row.getRowNumber(), colName, value,
                    "Value " + bd.toPlainString() + " exceeds maximum " + rule.getMaxDecimal()));
        }
        if (rule.getPrecision() != null) {
            // stripTrailingZeros to avoid false failures on "1.10" when precision=2
            int scale = bd.stripTrailingZeros().scale();
            if (scale > rule.getPrecision()) {
                errors.add(new ValidationError(row.getRowNumber(), colName, value,
                        "Value '" + value + "' has " + scale
                        + " decimal place(s) but maximum allowed is " + rule.getPrecision()));
            }
        }

        return errors;
    }
}
