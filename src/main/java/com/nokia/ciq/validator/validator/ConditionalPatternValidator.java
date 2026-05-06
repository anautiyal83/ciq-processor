package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.reader.model.CiqSheet;
import com.nokia.ciq.reader.store.CiqDataStore;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.config.ConditionalPattern;
import com.nokia.ciq.validator.config.ConditionalPatternEntry;
import com.nokia.ciq.validator.config.LookupStep;
import com.nokia.ciq.validator.model.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates the {@code conditionalPattern} column rule.
 *
 * <p>Resolves a final set of lookup values by following a multi-hop join chain
 * through workbook sheets, then finds the first {@link ConditionalPatternEntry}
 * whose {@code when} regex matches a resolved value and validates the cell against
 * the corresponding {@code pattern} regex.
 *
 * <p>Join resolution per row:
 * <ol>
 *   <li>Read {@code startColumn} from the current row → initial value set</li>
 *   <li>For each {@link LookupStep}: scan the step's sheet for rows where {@code joinOn}
 *       matches any value in the current set; collect {@code extract} column values as the
 *       next set</li>
 *   <li>The final set is matched against {@code when} rules to select a {@code pattern}</li>
 * </ol>
 *
 * <p>Blank cell values are silently skipped (pair with {@code required: true} to reject blanks).
 * If the join chain resolves to an empty set, validation is skipped with a warning.
 *
 * <p>YAML usage:
 * <pre>
 * INPUT_FILE:
 *   conditionalPattern:
 *     startColumn: GROUP
 *     lookupChain:
 *       - sheet: Index
 *         joinOn: CRGroup
 *         extract: Node
 *       - sheet: Node_Details
 *         joinOn: Node_Name
 *         extract: VER
 *     rules:
 *       - when: "^13\\."
 *         pattern: "(?i).*\\.jar$"
 *         message: "Version 13.x requires a .jar file — '{value}' is not valid"
 *       - when: ".*"
 *         pattern: "(?i)^(?!.*\\.(jar|tar\\.gz|tgz|gz|zip|bz2|7z|rar)$).*$"
 *         message: "Non-13.x versions require a plain non-compressed file — '{value}' is not valid"
 * </pre>
 */
public class ConditionalPatternValidator implements CellValidator {

    private static final Logger log = LoggerFactory.getLogger(ConditionalPatternValidator.class);

    private final CiqDataStore store;

    public ConditionalPatternValidator(CiqDataStore store) {
        this.store = store;
    }

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        ConditionalPattern cp = rule.getConditionalPattern();
        if (cp == null) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (cp.getRules() == null || cp.getRules().isEmpty()) return Collections.emptyList();
        if (cp.getLookupChain() == null || cp.getLookupChain().isEmpty()) return Collections.emptyList();

        // Seed: read startColumn from the current row
        String startCol = cp.getStartColumn();
        if (startCol == null || startCol.trim().isEmpty()) {
            log.warn("conditionalPattern: 'startColumn' is not configured for column '{}'", colName);
            return Collections.emptyList();
        }
        String startValue = row.get(startCol);
        if (startValue == null || startValue.trim().isEmpty()) {
            return Collections.emptyList(); // no start value — nothing to resolve
        }

        Set<String> currentValues = new LinkedHashSet<>();
        currentValues.add(startValue.trim());

        // Walk the join chain
        for (LookupStep step : cp.getLookupChain()) {
            currentValues = resolveStep(step, currentValues, colName);
            if (currentValues.isEmpty()) {
                log.warn("conditionalPattern: join chain produced no values at step [{}.{} -> {}] for column '{}'",
                        step.getSheet(), step.getJoinOn(), step.getExtract(), colName);
                return Collections.emptyList();
            }
        }

        // currentValues now holds the resolved final values (e.g. VER values)
        String cellValue = value.trim();

        for (String resolvedValue : currentValues) {
            ConditionalPatternEntry matched = findMatchingRule(cp, resolvedValue, colName);
            if (matched == null) continue;

            String pattern = matched.getPattern();
            if (pattern == null || pattern.trim().isEmpty()) continue;

            try {
                if (!Pattern.compile(pattern).matcher(cellValue).matches()) {
                    String msg = buildMessage(matched.getMessage(), cellValue, resolvedValue, pattern);
                    return Collections.singletonList(
                            new ValidationError(row.getRowNumber(), colName, value, msg));
                }
            } catch (PatternSyntaxException e) {
                log.error("conditionalPattern: invalid pattern '{}' for column '{}': {}",
                        pattern, colName, e.getMessage());
                return Collections.singletonList(new ValidationError(row.getRowNumber(), colName, value,
                        "Configuration error: invalid pattern '" + pattern
                        + "' in conditionalPattern for column '" + colName + "'"));
            }
        }

        return Collections.emptyList();
    }

    /**
     * Scans {@code step.sheet} for rows where the {@code step.joinOn} column matches
     * any value in {@code incomingValues}, and collects the corresponding
     * {@code step.extract} column values.
     */
    private Set<String> resolveStep(LookupStep step, Set<String> incomingValues, String colName) {
        Set<String> result = new LinkedHashSet<>();
        try {
            CiqSheet sheet = store.getSheet(step.getSheet());
            if (sheet == null) {
                log.warn("conditionalPattern: sheet '{}' not found in workbook (referenced by column '{}')",
                        step.getSheet(), colName);
                return result;
            }
            for (CiqRow r : sheet.getRows()) {
                String joinVal = r.get(step.getJoinOn());
                if (joinVal == null || joinVal.trim().isEmpty()) continue;
                if (incomingValues.contains(joinVal.trim())) {
                    String extracted = r.get(step.getExtract());
                    if (extracted != null && !extracted.trim().isEmpty()) {
                        result.add(extracted.trim());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("conditionalPattern: error reading sheet '{}': {}", step.getSheet(), e.getMessage());
        }
        return result;
    }

    /**
     * Returns the first {@link ConditionalPatternEntry} whose {@code when} regex
     * matches {@code resolvedValue} (full-string), or {@code null} if none match.
     * A null/empty {@code when} is treated as catch-all.
     */
    private static ConditionalPatternEntry findMatchingRule(ConditionalPattern cp,
                                                             String resolvedValue,
                                                             String colName) {
        for (ConditionalPatternEntry entry : cp.getRules()) {
            String when = entry.getWhen();
            if (when == null || when.trim().isEmpty()) {
                return entry; // catch-all
            }
            try {
                if (Pattern.compile(when).matcher(resolvedValue).find()) {
                    return entry;
                }
            } catch (PatternSyntaxException e) {
                log.error("conditionalPattern: invalid 'when' regex '{}' for column '{}': {}",
                        when, colName, e.getMessage());
            }
        }
        return null;
    }

    private static String buildMessage(String template, String cellValue,
                                       String resolvedValue, String pattern) {
        if (template == null || template.trim().isEmpty()) {
            return "Value '" + cellValue + "' does not match the required pattern"
                    + " (resolved lookup value: '" + resolvedValue + "'): " + pattern;
        }
        return template
                .replace("{value}", cellValue)
                .replace("{lookupValue}", resolvedValue);
    }
}
