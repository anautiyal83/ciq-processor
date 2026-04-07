package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * Validates {@code type: datetime} columns.
 *
 * <p>Checks that the value is a parseable date-time using the configured
 * {@code format} pattern (default: {@code yyyy-MM-dd'T'HH:mm:ss}).
 *
 * <p>Blank values are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * CHANGE_WINDOW_START:
 *   type: datetime
 *   format: "yyyy-MM-dd'T'HH:mm:ss"   # optional; this is the default
 * </pre>
 */
public class DateTimeValidator implements CellValidator {

    private static final String DEFAULT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isDatetimeType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String fmt = rule.getFormat() != null ? rule.getFormat() : DEFAULT_FORMAT;
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        sdf.setLenient(false);

        try {
            sdf.parse(value.trim());
            return Collections.emptyList();
        } catch (ParseException e) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' is not a valid datetime; expected format: " + fmt));
        }
    }
}
