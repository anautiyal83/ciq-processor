package com.nokia.ciq.validator.validator;

import com.nokia.ciq.reader.model.CiqIndex;
import com.nokia.ciq.reader.model.CiqRow;
import com.nokia.ciq.validator.config.ColumnRule;
import com.nokia.ciq.validator.model.ValidationError;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Validates {@code type: urlScheme} columns.
 *
 * <p>Checks that the value is a syntactically valid URL <em>and</em> that its
 * scheme is one of the values listed in the {@code values} field.
 *
 * <p>Blank values and missing {@code values} list are silently skipped.
 *
 * <p>YAML usage:
 * <pre>
 * ENDPOINT_URL:
 *   type: urlScheme
 *   values: [http, https]
 * </pre>
 */
public class UrlSchemeValidator implements CellValidator {

    @Override
    public List<ValidationError> validate(CiqRow row, String colName, String value,
                                          ColumnRule rule, CiqIndex index) {
        if (!rule.isUrlSchemeType()) return Collections.emptyList();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();

        String v = value.trim();
        URI uri;
        try {
            uri = new URI(v);
        } catch (Exception e) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' is not a valid URL"));
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return Collections.singletonList(new ValidationError(
                    row.getRowNumber(), colName, value,
                    "Value '" + value + "' has no URL scheme; expected one of: "
                    + (rule.getValues() != null ? rule.getValues() : "—")));
        }

        List<String> allowed = rule.getValues();
        if (allowed == null || allowed.isEmpty()) return Collections.emptyList();

        for (String a : allowed) {
            if (a != null && a.equalsIgnoreCase(scheme)) return Collections.emptyList();
        }

        return Collections.singletonList(new ValidationError(
                row.getRowNumber(), colName, value,
                "URL scheme '" + scheme + "' is not allowed; expected one of: " + allowed));
    }
}
