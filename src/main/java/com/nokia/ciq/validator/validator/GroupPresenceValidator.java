package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles presence-group row rules: {@code one_of}, {@code only_one}, {@code all_or_none}.
 *
 * <ul>
 *   <li>{@code one_of}     — at least one of the listed columns must be non-blank.</li>
 *   <li>{@code only_one}   — exactly one of the listed columns must be non-blank.</li>
 *   <li>{@code all_or_none}— either all listed columns are non-blank or all are blank.</li>
 * </ul>
 */
public class GroupPresenceValidator implements RowValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        List<String> cols;
        String directive;

        if (rule.getOne_of() != null && !rule.getOne_of().isEmpty()) {
            cols = rule.getOne_of();
            directive = "one_of";
        } else if (rule.getOnly_one() != null && !rule.getOnly_one().isEmpty()) {
            cols = rule.getOnly_one();
            directive = "only_one";
        } else if (rule.getAll_or_none() != null && !rule.getAll_or_none().isEmpty()) {
            cols = rule.getAll_or_none();
            directive = "all_or_none";
        } else {
            return Collections.emptyList();
        }

        long nonBlankCount = 0;
        for (String col : cols) {
            if (!isBlank(row.get(col))) nonBlankCount++;
        }

        boolean passed;
        String message;

        switch (directive) {
            case "one_of":
                passed  = nonBlankCount >= 1;
                message = "At least one of " + cols + " must be non-blank (found " + nonBlankCount + " non-blank)";
                break;
            case "only_one":
                passed  = nonBlankCount == 1;
                message = "Exactly one of " + cols + " must be non-blank (found " + nonBlankCount + " non-blank)";
                break;
            case "all_or_none":
                passed  = nonBlankCount == 0 || nonBlankCount == cols.size();
                message = "Either all or none of " + cols + " must be non-blank (found " + nonBlankCount + " of "
                        + cols.size() + " non-blank)";
                break;
            default:
                return Collections.emptyList();
        }

        if (!passed) {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(row.getRowNumber(), directive, null, message));
            return errors;
        }
        return Collections.emptyList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
