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
 * Validates {@code type: date}, {@code type: time}, and {@code type: datetime} columns.
 *
 * <p>Replaces the three separate DateValidator, TimeValidator, and DateTimeValidator classes.
 * The {@code format} field on the column rule overrides the type default.
 *
 * <p>Default formats:
 * <ul>
 *   <li>date     → {@code yyyy-MM-dd}</li>
 *   <li>time     → {@code HH:mm:ss}</li>
 *   <li>datetime → {@code yyyy-MM-dd'T'HH:mm:ss}</li>
 * </ul>
 *
 * <p>Blank values are silently skipped.
 */
public class TemporalValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        String defaultFmt;
        if (rule.isDateType())          defaultFmt = "yyyy-MM-dd";
        else if (rule.isTimeType())     defaultFmt = "HH:mm:ss";
        else if (rule.isDatetimeType()) defaultFmt = "yyyy-MM-dd'T'HH:mm:ss";
        else                            return Collections.emptyList();

        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String fmt = rule.getFormat() != null ? rule.getFormat() : defaultFmt;
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        sdf.setLenient(false);

        try {
            sdf.parse(value.trim());
            return Collections.emptyList();
        } catch (ParseException e) {
            String typeLabel = rule.isDateType() ? "date" : rule.isTimeType() ? "time" : "datetime";
            return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                CellValidator.msg(rule.getMessages(), "format",
                    "Value '" + value + "' is not a valid " + typeLabel
                    + "; expected format: " + fmt)));
        }
    }
}
