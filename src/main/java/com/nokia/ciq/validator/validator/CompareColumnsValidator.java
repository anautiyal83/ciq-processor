package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.SheetRowRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles {@code compare: "ColA op ColB"} row rules.
 *
 * <p>Supported operators: {@code >=}, {@code <=}, {@code >}, {@code <}, {@code ==}, {@code !=}.
 *
 * <p>Comparison is numeric when both values are parseable as {@code double};
 * otherwise it falls back to lexicographic string comparison.
 * Rows where either column is blank are silently skipped.
 */
public class CompareColumnsValidator implements RowValidator {

    private static final Pattern COMPARE_PATTERN =
            Pattern.compile("(\\w+)\\s*(>=|<=|>|<|==|!=)\\s*(\\w+)");

    @Override
    public List<ValidationError> validate(CiqRow row, SheetRowRule rule) {
        if (rule.getCompare() == null) return Collections.emptyList();

        Matcher m = COMPARE_PATTERN.matcher(rule.getCompare().trim());
        if (!m.matches()) {
            return Collections.emptyList(); // unparseable — skip silently
        }

        String colA = m.group(1);
        String op   = m.group(2);
        String colB = m.group(3);

        String valA = row.get(colA);
        String valB = row.get(colB);

        if (isBlank(valA) || isBlank(valB)) return Collections.emptyList();

        boolean passed;
        int cmp = compareNumericOrString(valA, valB);

        switch (op) {
            case ">=": passed = cmp >= 0; break;
            case "<=": passed = cmp <= 0; break;
            case ">":  passed = cmp > 0;  break;
            case "<":  passed = cmp < 0;  break;
            case "==": passed = cmp == 0; break;
            case "!=": passed = cmp != 0; break;
            default:   return Collections.emptyList();
        }

        if (!passed) {
            List<ValidationError> errors = new ArrayList<>();
            errors.add(new ValidationError(row.getRowNumber(), colA, valA,
                    "Comparison failed: " + colA + " (" + valA + ") " + op
                    + " " + colB + " (" + valB + ")"));
            return errors;
        }
        return Collections.emptyList();
    }

    private int compareNumericOrString(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
