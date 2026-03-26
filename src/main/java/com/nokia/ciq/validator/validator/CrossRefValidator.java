package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.CrossRef;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the {@code crossRef} rule.
 * The cell value must exist in a specified column of another (or the same) sheet.
 *
 * <p>Results are cached per {@code sheet.column} key to avoid repeated IO.
 * Blank values are silently skipped.
 */
public class CrossRefValidator implements CellValidator {

    private static final Logger log = LoggerFactory.getLogger(CrossRefValidator.class);

    private final CiqDataStore store;
    private final Map<String, Set<String>> cache = new HashMap<>();

    public CrossRefValidator(CiqDataStore store) {
        this.store = store;
    }

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        CrossRef ref = rule.getCrossRef();
        if (ref == null) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        Set<String> validValues = resolve(ref, index);
        if (validValues != null && !validValues.isEmpty() && !validValues.contains(value)) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' not found in "
                    + ref.getSheet() + "." + ref.getColumn()
                    + ". Valid values: " + validValues));
        }

        return Collections.emptyList();
    }

    private Set<String> resolve(CrossRef ref, CiqIndex index) {
        String cacheKey = ref.getSheet() + "." + ref.getColumn();
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey);

        Set<String> values = new HashSet<>();
        try {
            if ("_index".equals(ref.getSheet())) {
                if ("node".equalsIgnoreCase(ref.getColumn())) {
                    values.addAll(index.getNiamMapping().keySet());
                }
            } else {
                CiqSheet sheet = store.getSheet(ref.getSheet());
                if (sheet != null) {
                    for (CiqRow r : sheet.getRows()) {
                        String v = r.get(ref.getColumn());
                        if (v != null) values.add(v);
                    }
                } else {
                    log.warn("CrossRef sheet '{}' not found", ref.getSheet());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to resolve crossRef {}: {}", cacheKey, e.getMessage());
        }

        cache.put(cacheKey, values);
        return values;
    }
}
