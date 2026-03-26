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
 * Validates the {@code pattern} rule (full regex match).
 * Blank values are silently skipped.
 */
public class PatternValidator implements CellValidator {

    private static final Logger log = LoggerFactory.getLogger(PatternValidator.class);

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (rule.getPattern() == null) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        try {
            if (!Pattern.compile(rule.getPattern()).matcher(value).matches()) {
                String msg = (rule.getPatternMessage() != null && !rule.getPatternMessage().isEmpty())
                        ? rule.getPatternMessage() + " (got: '" + value + "')"
                        : "Value '" + value + "' does not match pattern: " + rule.getPattern();
                return Collections.singletonList(new ValidationError(
                        row.getRowNumber(), colName, value, msg));
            }
        } catch (PatternSyntaxException e) {
            log.warn("Invalid pattern '{}' in rules for column '{}': {}",
                    rule.getPattern(), colName, e.getMessage());
        }

        return Collections.emptyList();
    }
}
