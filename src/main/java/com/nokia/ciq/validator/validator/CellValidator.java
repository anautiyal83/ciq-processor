package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.List;

/**
 * Strategy interface for a single cell-level validation rule.
 *
 * <p>Validators are applied in order by {@link com.nokia.ciq.validator.CiqValidationEngine}.
 * Each validator is responsible for checking one aspect of a {@link ColumnRule}
 * and returning any errors found.
 *
 * <p>To add a new rule type:
 * <ol>
 *   <li>Implement this interface in a new class.</li>
 *   <li>Register it in {@code CiqValidationEngine} (in order).</li>
 * </ol>
 */
public interface CellValidator {

    /**
     * Validate a single cell value against the supplied rule.
     *
     * @param row     the current data row (used for row number and conditional lookups)
     * @param colName the column being validated
     * @param value   the raw cell value (may be null or blank)
     * @param rule    the column rule from the YAML rules file
     * @param index   the CIQ index (used for cross-reference lookups)
     * @return list of {@link ValidationError}s — empty means the cell passes this rule
     */
    List<ValidationError> validate(CiqRow row, String colName, String value,
                                   ColumnRule rule, CiqIndex index);

    /**
     * When {@code true}, the engine stops running further validators for this cell
     * as soon as this validator produces at least one error.
     *
     * <p>Used by {@link RequiredValidator}: a blank required field should not also
     * trigger allowedValues, pattern, or integer errors.
     */
    default boolean isGatekeeper() {
        return false;
    }
}
