package com.nokia.ciq.validator.validator;

import com.nokia.ciq.processor.reader.InMemoryCiqDataStore;
import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@code sheetRef: true} rule.
 *
 * <p>The cell value must match the name of a sheet that exists in the workbook.
 * Blank values are silently skipped — pair with {@code required: true} to enforce presence.
 *
 * <p>Case sensitivity is controlled by the dedicated {@code sheetRefIgnoreCase} flag
 * (kept separate from {@code ignoreCase}, which applies to column-value comparisons):
 * <ul>
 *   <li>{@code sheetRefIgnoreCase: false} (default) — exact-case match
 *       ({@code ANNOUNCEMENT_FILES} ≠ {@code announcement_files})</li>
 *   <li>{@code sheetRefIgnoreCase: true} — case-insensitive match</li>
 * </ul>
 *
 * <p>Sheet names are resolved once from the store and cached for the lifetime
 * of this validator instance.
 *
 * <p>When the cell contains multiple comma-separated sheet names (e.g. the
 * {@code Tables} column in an Index sheet), set {@code multi: true} and each
 * name is validated individually.
 *
 * <p>YAML usage:
 * <pre>
 * Tables:
 *   required: true
 *   sheetRef: true                      # case-sensitive by default
 *   multi: true                         # split on commas, validate each name
 *
 * Tables:
 *   required: true
 *   sheetRef: true
 *   sheetRefIgnoreCase: true            # case-insensitive match
 *   messages:
 *     type: "Sheet '{value}' does not exist in the workbook"
 * </pre>
 */
public class SheetRefValidator implements CellValidator {

    private static final Logger log = LoggerFactory.getLogger(SheetRefValidator.class);

    private final CiqDataStore store;
    /** Exact-case sheet names, built lazily on first use. */
    private Set<String> sheetNamesExact;
    /** Lower-case sheet names for case-insensitive matching, built lazily on first use. */
    private Set<String> sheetNamesLower;

    public SheetRefValidator(CiqDataStore store) {
        this.store = store;
    }

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isSheetRef()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String[] parts = rule.isMulti()
                ? value.split(",")
                : new String[]{ value };

        List<ValidationError> errors = new java.util.ArrayList<>();
        String suffix = rule.isSheetRefIgnoreCase() ? "" : " (case-sensitive)";

        for (String part : parts) {
            String v = part.trim();
            if (v.isEmpty()) continue;

            boolean found = rule.isSheetRefIgnoreCase()
                    ? sheetNamesLower().contains(v.toLowerCase())
                    : sheetNamesExact().contains(v);

            if (!found) {
                errors.add(new ValidationError(
                        row.getRowNumber(), colName, v,
                        CellValidator.msg(rule.getMessages(), "type",
                            "Sheet '" + v + "' does not exist in the workbook" + suffix + "; "
                            + "available sheets: " + availableSheetNames())));
            }
        }
        return errors;
    }

    private Set<String> sheetNamesExact() {
        if (sheetNamesExact != null) return sheetNamesExact;
        sheetNamesExact = new HashSet<>(workbookSheetNames());
        return sheetNamesExact;
    }

    private Set<String> sheetNamesLower() {
        if (sheetNamesLower != null) return sheetNamesLower;
        sheetNamesLower = new HashSet<>();
        for (String s : workbookSheetNames()) sheetNamesLower.add(s.toLowerCase());
        return sheetNamesLower;
    }

    /**
     * Returns the complete list of sheet names to validate against.
     *
     * <p>When the store is an {@link InMemoryCiqDataStore}, uses the full workbook sheet
     * list captured at read time — this is the correct source of truth because
     * {@link CiqDataStore#getAvailableSheets()} only returns loaded data tables and would
     * silently accept or reject sheet names that exist in the workbook but were not loaded.
     *
     * <p>Falls back to {@code getAvailableSheets()} for other store implementations.
     */
    private List<String> workbookSheetNames() {
        if (store instanceof InMemoryCiqDataStore) {
            List<String> all = ((InMemoryCiqDataStore) store).getAllWorkbookSheetNames();
            if (all != null && !all.isEmpty()) return all;
        }
        List<String> sheets = store.getAvailableSheets();
        return sheets != null ? sheets : Collections.emptyList();
    }

    /** Returns original-case sheet names for use in error messages. */
    private List<String> availableSheetNames() {
        return workbookSheetNames();
    }
}
