package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles {@code compare: "ColA operator ColB"} row rules.
 *
 * <p>Canonical string operators (preferred):
 * {@code equals}, {@code notEquals}, {@code greaterThan}, {@code greaterThanOrEquals},
 * {@code lessThan}, {@code lessThanOrEquals}.
 *
 * <p>Symbol aliases are also accepted for convenience:
 * {@code ==}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=}.
 *
 * <p>YAML examples:
 * <pre>
 * compare: "StartPort lessThanOrEquals EndPort"
 * compare: "Price greaterThan MinPrice"
 * compare: "Status equals ExpectedStatus"
 * </pre>
 *
 * <p>Comparison is numeric when both values are parseable as {@code double};
 * otherwise falls back to lexicographic string comparison.
 * Rows where either column is blank are silently skipped.
 */
public class CompareColumnsValidator implements RowValidator {

    // Matches:  ColumnA  <operator>  ColumnB
    // Operator can be a symbol (>=, <=, ==, !=, >, <) or a word (greaterThanOrEquals, etc.)
    private static final Pattern COMPARE_PATTERN = Pattern.compile(
            "(\\w+)\\s+(>=|<=|==|!=|>|<|\\w+)\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        if (rule.getCompare() == null) return Collections.emptyList();

        Matcher m = COMPARE_PATTERN.matcher(rule.getCompare().trim());
        if (!m.matches()) return Collections.emptyList();

        String colA = m.group(1);
        String op   = Operator.normalize(m.group(2));
        String colB = m.group(3);

        String valA = row.get(colA);
        String valB = row.get(colB);
        if (isBlank(valA) || isBlank(valB)) return Collections.emptyList();

        int cmp = compareNumericOrString(valA, valB);
        if (Operator.evaluate(op, cmp)) return Collections.emptyList();

        return Collections.singletonList(new ValidationError(row.getRowNumber(), colA, valA,
                "Comparison failed: " + colA + " (" + valA + ") "
                + m.group(2) + " " + colB + " (" + valB + ")"));
    }

    private static int compareNumericOrString(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
