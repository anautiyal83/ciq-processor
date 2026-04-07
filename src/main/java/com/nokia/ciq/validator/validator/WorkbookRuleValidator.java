package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.WorkbookRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.List;

/**
 * Strategy interface for a workbook-level cross-sheet validation rule.
 *
 * <p>Workbook validators are applied once per validation run after all
 * per-sheet validation has completed.
 */
public interface WorkbookRuleValidator {

    /**
     * Validate the workbook rule against the full data store.
     *
     * @param rule  the workbook-level rule to evaluate
     * @param store the full CIQ data store (all sheets accessible)
     * @return list of {@link ValidationError}s — empty means the rule passes
     */
    List<ValidationError> validate(WorkbookRule rule, CiqDataStore store);
}
