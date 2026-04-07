package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.List;

/**
 * Strategy interface for a row-level validation rule.
 *
 * <p>Row validators are applied to every row of a sheet by the validation engine
 * after per-cell validators have run.  Each implementation handles one
 * {@link SheetRowRule} directive (require/forbid, compare, one_of, etc.).
 */
public interface RowValidator {

    /**
     * Validate the given row against the given rule.
     *
     * @param row  the data row to validate
     * @param rule the row-level rule to apply
     * @return list of {@link ValidationError}s — empty means the row passes this rule
     */
    List<ValidationError> validate(CiqRow row, SheetRowRule rule);
}
